package terminalchat.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final ConnectionRegistry registry;
    private BufferedWriter output;
    private String username;

    public ClientHandler(Socket socket, ConnectionRegistry registry) {
        this.socket = socket;
        this.registry = registry;
    }

    @Override
    public void run() {
        try {
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String line;
            while ((line = input.readLine()) != null) {
                if (username == null) {
                    handleHello(line);
                } else {
                    handleMessage(line);
                }
            }
        } catch (IOException e) {//detects stream ending when reading, writing a and setting up fails
            System.err.println("Client connection ended: " + e.getMessage());
        } finally {
            if (username != null) {
                registry.unregister(username, this);
            }
            closeSocket();
        }
    }

    private void handleHello(String line) throws IOException {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 2 || !parts[0].equals("HELLO")) {
            send("ERROR EXPECTED_HELLO");
            return;
        }
        String requestedName = parts[1];
        if (!requestedName.matches("[A-Za-z0-9_-]{1,32}") || requestedName.equals("me")) {
            send("ERROR INVALID_USERNAME");
            return;
        }

        // Hold the same lock as send() so another sender cannot overtake OK
        // after this handler becomes visible in the shared registry.
        synchronized (this) {
            if (!registry.tryRegister(requestedName, this)) {
                send("ERROR USERNAME_TAKEN");
                return;
            }
            username = requestedName;
            send("OK " + username);
        }
    }

    private void handleMessage(String line) throws IOException {
        String[] parts = line.split(" ", 3);
        if (parts.length != 3 || !parts[0].equals("SEND") || parts[1].isEmpty()
                || parts[2].trim().isEmpty()) {
            send("ERROR EXPECTED_SEND");
            return;
        }
        String recipient = parts[1].equals("me") ? username : parts[1];
        Optional<ClientHandler> target = registry.find(recipient);
        if (!target.isPresent()) {
            send("ERROR USER_NOT_FOUND " + recipient);
            return;
        }
        try {
            target.get().send("FROM " + username + " " + parts[2]);
        } catch (IOException e) {
            // A recipient's failed write must not close the sender's socket.
            registry.unregister(recipient, target.get());
            target.get().closeSocket();
            send("ERROR RECIPIENT_UNAVAILABLE " + recipient);
        }
    }

    public synchronized void send(String line) throws IOException {
        output.write(line);
        output.write('\n');
        output.flush();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println("Could not close client socket: " + e.getMessage());
        }
    }
}
