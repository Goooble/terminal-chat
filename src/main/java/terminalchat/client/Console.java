package terminalchat.client;

import java.io.IOException;

/** Owns keyboard commands and the currently selected recipient. */
public final class Console {
    private static Renderer renderer;
    private static ServiceHandler serviceHandler;
    private static Thread connectionThread;
    private static String currentFriend = "me";

    public static void main(String[] args) throws IOException, InterruptedException {
        try (Renderer terminal = new Renderer();
             ServiceHandler connection = new ServiceHandler(terminal)) {
            renderer = terminal;
            serviceHandler = connection;
            renderer.printLine("/hello <username> connects; /send <friend> selects a chat; /quit exits.");

            try {
                String line;
                while ((line = renderer.readLine("[" + currentFriend + "] > ")) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    if (line.startsWith("/")) {
                        if (!handleCommand(line.trim())) {//closes if false
                            break;
                        }
                    } else {
                        serviceHandler.text(currentFriend, line);
                    }
                }
            } finally {
                serviceHandler.close();
                if (connectionThread != null) {
                    connectionThread.join();
                }
                renderer.printLine("quitting application");
            }
        }
    }

    /** Selects a recipient locally; the server checks availability on delivery. */
    private static void setFriend(String username) {
        currentFriend = username;
    }

    private static boolean handleCommand(String line) throws IOException, InterruptedException {
        String[] parts = line.split("\\s+", 2);
        switch (parts[0]) {
            case "/hello":
                if (parts.length != 2 || !parts[1].matches("[A-Za-z0-9_-]{1,32}")) {
                    renderer.printLine("Usage: /hello <username> (1-32 letters, digits, _ or -)");
                    return true;
                }
                serviceHandler.close();
                if (connectionThread != null) {
                    connectionThread.join();
                }
                connectionThread = new Thread(serviceHandler, "server-connection");
                if (serviceHandler.connect(parts[1])) {
                    renderer.printLine("connected");
                    connectionThread.start();
                } else {
                    connectionThread = null;
                    renderer.printLine("retry");
                }
                break;
            case "/send":
                if (parts.length != 2 || !parts[1].matches("[A-Za-z0-9_-]{1,32}")) {
                    renderer.printLine("Usage: /send <friend> (1-32 letters, digits, _ or -)");
                } else {
                    setFriend(parts[1]);
                }
                break;
            case "/quit":
                if (parts.length == 1) {
                    return false;
                }
                renderer.printLine("Usage: /quit");
                break;
            default:
                renderer.printLine("Unknown command: " + parts[0]);
        }
        return true;
    }
}
