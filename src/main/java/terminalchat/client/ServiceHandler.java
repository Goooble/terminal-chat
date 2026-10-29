package terminalchat.client;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Owns the client's connection to the chat server. */
public final class ServiceHandler implements Runnable, AutoCloseable {
    private final Renderer renderer;
    private Socket socket;
    private BufferedWriter output;
    private BufferedReader input;
    private volatile boolean intentionalClose;

    public ServiceHandler(Renderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /**
     * Replaces any previous connection and sends one HELLO request.
     * Blocks until the server accepts the username, rejects it, or times out.
     * True means registration succeeded and the receiver can be started.
     */
    public synchronized boolean connect(String username) {
        Objects.requireNonNull(username, "username");
        if (username.indexOf('\n') >= 0 || username.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Username must fit on one protocol line");
        }

        try {
            close();
            socket = new Socket();
            intentionalClose = false;
            socket.connect(new InetSocketAddress("localhost", 5000), 3000);
            socket.setSoTimeout(3000);
            output = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            input = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));

            output.write("HELLO " + username);
            output.write('\n');
            output.flush();
            String response = input.readLine();
            if (!("OK " + username).equals(response)) {
                close();
                return false;
            }
            socket.setSoTimeout(0);
            return true;
        } catch (IOException failure) {
            try {
                close();
            } catch (IOException closeFailure) {//TODO: so who is closing this?
                failure.addSuppressed(closeFailure);
            }
            return false;
        }
    }

    /** Runs only after connect() has consumed the registration response. */
    @Override
    public void run() {
        BufferedReader reader;
        synchronized (this) {
            reader = input;
        }
        if (reader == null) {
            return;
        }
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("FROM ")) {
                    String[] parts = line.split(" ", 3);
                    renderer.printLine(parts.length == 3 ? parts[1] + " > " + parts[2] : line);
                } else {
                    renderer.printLine(line);
                }
            }
        } catch (IOException failure) {
            // Closing the socket also releases this blocking read.
        } finally {
            boolean unexpected = !intentionalClose;
            try {
                close();
            } catch (IOException failure) {
                renderer.printLine("could not close connection: " + failure.getMessage());
            }
            if (unexpected) {
                renderer.printLine("server error");
            }
        }
    }

    /** Writes an outgoing message; the local echo is not delivery confirmation. */
    public synchronized boolean text(String friend, String message) {
        Objects.requireNonNull(friend, "friend");
        Objects.requireNonNull(message, "message");
        if (socket == null || socket.isClosed() || output == null) {
            renderer.printLine("not connected");
            return false;
        }
        if (!friend.matches("[A-Za-z0-9_-]{1,32}")
                || message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0) {
            renderer.printLine("Recipient and message must fit on one protocol line");
            return false;
        }
        try {
            output.write("SEND " + friend + " " + message);
            output.write('\n');
            output.flush();
            renderer.printLine("you:" + friend + " > " + message);
            return true;
        } catch (IOException failure) {
            try {
                close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            renderer.printLine("server error: " + failure.getMessage());
            return false;
        }
    }

    /** Retains resource references if closing fails so cleanup can be retried. */
    @Override
    public synchronized void close() throws IOException {
        intentionalClose = true;
        if (socket != null) {
            socket.close();
        }
        socket = null;
        output = null;
        input = null;
    }
}
