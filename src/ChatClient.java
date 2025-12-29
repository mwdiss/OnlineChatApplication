import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Chat Client Implementation.
 * Protocol uses '|' as a delimiter.
 * @author MWDiss
 */
public class ChatClient {

    private static final String HOST = "localhost";
    private static final int PORT = 12345;
    private static final String SEP = "|";
    
    // Protocol Commands
    private static final String MSG = "M";
    private static final String SENT = "S";
    private static final String NOTIFY = "N";
    
    /** Starts client connection and UI loop. */
    public void start() {
        System.out.println("=== CLIENT CONNECTING ===");
        try (Socket socket = new Socket(HOST, PORT)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter Username: ");
            String name = scanner.nextLine().trim();
            while (name.isBlank() || name.contains(SEP)) {
                System.out.print("Invalid (no '" + SEP + "'). Try again: ");
                name = scanner.nextLine().trim();
            }
            out.println(name);

            new Thread(new Listener(socket)).start();
            System.out.print(">> ");
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine().replace(SEP, "");
                out.println(input);
            }
        } catch (IOException e) {
            System.out.println("\n*** Connection failed. ***");
        } catch (java.util.NoSuchElementException e) {
            System.out.println("\n*** Input closed. ***");
        }
    }
    
    /** @param args Unused */
    public static void main(String[] args) {
        new ChatClient().start();
    }

    /** Listens for server messages. */
    private class Listener implements Runnable {
        // Input stream from server
        private final BufferedReader in;
        
        /** @param socket Server socket. Precondition: Must not be null. */
        public Listener(Socket socket) throws IOException {
            this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        }
        
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    processLine(line);
                }
            } catch (IOException e) {
                // Socket closed
            } finally {
                System.out.println("\n*** Connection lost. ***");
            }
        }

        /** @param line Raw protocol line */
        private void processLine(String line) {
            int idx = line.indexOf(SEP);
            if (idx == -1) {
                return;
            }

            String cmd = line.substring(0, idx);
            String text = line.substring(idx + 1);
            
            // Only clear line for incoming/notifications, not own 'Sent'
            if (!SENT.equals(cmd)) {
                System.out.println();
            }
            switch (cmd) {
                case MSG:
                    int splitIdx = text.indexOf(SEP);
                    if (splitIdx != -1) {
                        System.out.println("[" + text.substring(0, splitIdx) + 
                                           "]: " + text.substring(splitIdx + 1));
                    }
                    break;
                case SENT:
                    System.out.println("*** [Sent]: " + text);
                    break;
                case NOTIFY:
                    System.out.println("*** " + text);
                    break;
            }
            System.out.print(">> ");
        }
    }
}
