# terminal-chat

A Java 11 TCP chat project. The first server slice supports multiple concurrent
clients, unique active usernames, direct messages, and self-messages. Usernames
are temporary handles; registration lasts until the connection ends.

## Run the server

From the repository root, with JDK 11 or later:

```sh
javac -encoding UTF-8 -d out src/main/java/terminalchat/server/*.java
java -cp out terminalchat.server.ChatServer 5000
```

The optional port defaults to 5000. Stop the server with Ctrl+C.

To try the protocol manually, open two more terminals and, if netcat is installed,
run `nc localhost 5000` in each. Enter `HELLO alice` in the first and `HELLO bob`
in the second, then enter `SEND bob Hello!` in Alice's terminal. Bob receives
`FROM alice Hello!`.

## Protocol

Commands and responses are UTF-8 text, one line per message. Names are
case-sensitive, contain 1–32 letters, digits, underscores, or hyphens, and cannot
be `me`, which is reserved as the self-chat alias.

| Request | Response or effect |
| --- | --- |
| `HELLO alice` | Register the active name; reply `OK alice` |
| `HELLO alice` when taken | Reply `ERROR USERNAME_TAKEN`; accept another `HELLO` |
| Invalid initial command | Reply `ERROR EXPECTED_HELLO`; allow retry |
| Invalid username | Reply `ERROR INVALID_USERNAME`; allow retry |
| `SEND bob Hello!` | Bob receives `FROM alice Hello!` |
| `SEND me A note` | Sender receives `FROM alice A note` |
| Send to an unknown name | Reply `ERROR USER_NOT_FOUND <name>`; keep connection open |
| Write to a disconnected recipient fails | Reply `ERROR RECIPIENT_UNAVAILABLE <name>` |
| Invalid command after registration | Reply `ERROR EXPECTED_SEND`; keep connection open |

Each connection registers once; a second `HELLO` after success is invalid.
Delivery uses the sender's registered name. There is no delivery acknowledgment;
a successful socket write does not prove that the recipient displayed a message.

## Current scope

- `ChatServer`: listen and start a thread for each client.
- `ClientHandler`: handshake, read commands, route messages, serialize writes, and clean up.
- `ConnectionRegistry`: atomically register names, look up handlers, and remove owned registrations.

The terminal client with `/add`, `/friends`, `/chat`, and default self-chat is the
next feature. Contacts, history, persistent accounts, and authentication are not
implemented yet. This is a small blocking-I/O learning server: client threads and
input-line lengths are not bounded, and a slow reader can block a sender.
