import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chat Server Implementation.
 * Protocol uses '|' as a delimiter.
 * @author MWDiss
 */
public class ChatServer {

    private static final int PORT = 12345;
    private static final int MAX_USERS = 126;
    private static final String SEP = "|";
    
    // Protocol Commands
    private static final String MSG = "M";
    private static final String SENT = "S";
    private static final String NOTIFY = "N";
    private static final String RENAME = "!rename ";

    // Thread-safe ID generator
    private static final AtomicInteger ID_GEN = new AtomicInteger(0);
    // Active client list
    private final List<Handler> clients = new CopyOnWriteArrayList<>();
    // Thread pool for handling connections
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_USERS);

    /** Starts the server listening loop. */
    public void start() {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println(">> Server active on port " + PORT);
            while (true) {
                pool.execute(new Handler(server.accept()));
            }
        } catch (IOException e) {
            System.out.println("Start Error: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Broadcasts message to all clients except sender.
     * @param msg Raw protocol string
     * @param sender Client to exclude or null
     */
    public void broadcast(String msg, Handler sender) {
        for (Handler client : clients) {
            if (client != sender) {
                client.send(msg);
            }
        }
    }

    /** @param args Unused */
    public static void main(String[] args) {
        new ChatServer().start();
    }
    
    /** Handles individual client connection. */
    private class Handler implements Runnable {
        // Client connection socket
        private final Socket socket;
        // Unique user ID
        private final int id; 
        // Output stream
        private PrintWriter out;
        // Input stream
        private BufferedReader in;
        // User display name
        private String name;

        /** @param socket Client socket. Precondition: Must not be null. */
        public Handler(Socket socket) {
            this.socket = socket;
            this.id = ID_GEN.incrementAndGet();
        }

        /** @param msg Message string */
        public synchronized void send(String msg) {
            if (out != null) {
                out.println(msg);
            }
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
                
                String raw = in.readLine();
                if (raw == null || raw.isBlank()) {
                    return;
                }
                
                name = raw.replace(SEP, "").trim() + "#" + id;
                clients.add(this);
                System.out.println(">> Connected: " + name);
                
                broadcast(NOTIFY + SEP + name + " joined!", this);
                send(NOTIFY + SEP + "Welcome " + name + 
                     "! Type '!rename <new>' to change.");

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    if (line.startsWith(RENAME)) {
                        handleRename(line);
                    } else {
                        handleMessage(line);
                    }
                }
            } catch (IOException e) {
                // Connection closed/dropped
            } finally {
                shutdown();
            }
        }

        /** Handles username changes. */
        private void handleRename(String line) {
            String newName = line.substring(RENAME.length()).replace(SEP, "")
                                 .trim();
            if (!newName.isEmpty() && !newName.equals(name)) {
                String old = name;
                name = newName + "#" + id;
                broadcast(NOTIFY + SEP + old + " is now " + name, null);
            }
        }

        /** Handles standard messages. */
        private void handleMessage(String line) {
            String clean = line.replace(SEP, "");
            send(SENT + SEP + clean);
            System.out.println(" [" + name + "]: " + clean);
            broadcast(MSG + SEP + name + SEP + clean, this);
        }

        /** Cleans up resources. */
        private void shutdown() {
            clients.remove(this);
            if (name != null) {
                System.out.println(">> Left: " + name);
                System.out.println("Remaining users: " + clients.size());
                broadcast(NOTIFY + SEP + name + " left.", null);
            }
            try { 
                socket.close(); 
            } catch (IOException e) { 
                /* Ignore close errors */ 
            }
        }
    }
}
