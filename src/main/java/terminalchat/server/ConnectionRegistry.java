package terminalchat.server;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConnectionRegistry {
    private final ConcurrentMap<String, ClientHandler> connections = new ConcurrentHashMap<>();

    public boolean tryRegister(String username, ClientHandler handler) {
        return connections.putIfAbsent(username, handler) == null;
    }

    public Optional<ClientHandler> find(String username) {
        return Optional.ofNullable(connections.get(username));
    }

    public void unregister(String username, ClientHandler handler) {
        connections.remove(username, handler);
    }
}
