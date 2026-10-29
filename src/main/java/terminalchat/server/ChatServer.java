package terminalchat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public final class ChatServer {
    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 5000 : Integer.parseInt(args[0]);
        ConnectionRegistry registry = new ConnectionRegistry();

        try (ServerSocket listener = new ServerSocket(port)) {
            System.out.println("Chat server listening on port " + listener.getLocalPort());
            while(true) {
                Socket client = listener.accept();
                ClientHandler handler = new ClientHandler(client, registry);
                new Thread(handler, "chat-client-" + client.getRemoteSocketAddress()).start();
            }
        }
    }
}
