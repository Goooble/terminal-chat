# terminal-chat

A Java 11 TCP chat project. The first server slice supports multiple concurrent
clients, unique active usernames, direct messages, and self-messages. Usernames
are temporary handles; registration lasts until the connection ends.

## Build and dependencies

With Maven installed, run `mvn compile` to download dependencies and compile for
Java 11. `pom.xml` declares JLine 3.30.0 for the upcoming terminal client. In
IntelliJ, open `pom.xml` as a Maven project or choose **Add as Maven Project**.
Maven generates compiled output in `target/`.

The JLine client is being built in small slices. `terminalchat.client.Renderer`
provides editable `readLine(prompt)`, concurrent `printLine(message)` above the
current input, and terminal cleanup through `close()`. Input returns `null` on
Ctrl+C or Ctrl+D. Run the eventual client in a terminal for full cursor editing
and input redisplay; a redirected stream or basic IDE output pane may lack those
capabilities.

## Current client slice

Run `terminalchat.client.Console.main()` from IntelliJ. The client reads lines
with a `[<current-friend>] > ` prompt (initially `[me] > `). `/hello <username>`
creates a Thread with the ServiceHandler but calls `connect` on the console
thread, blocking command processing until registration succeeds or fails. Each
request opens a fresh connection to `localhost:5000`. `/send <friend>` selects a recipient locally;
ordinary text calls `ServiceHandler.text(friend, message)`. Only lines starting
with `/` enter the command handler. `/quit`, Ctrl+C, or Ctrl+D ends input, closes
the connection, waits for the worker, and releases the terminal.

`connect` reads the registration reply itself: exact `OK <username>` returns true;
rejection, EOF, or failure returns false and closes the attempt. Connection setup
and waiting for the reply each have a three-second timeout. Failure prints
`retry` and clears the unstarted thread reference. Success prints `connected`
and starts the receiver thread. The handshake and receiver share one buffered
reader so messages arriving with OK are retained. The accepted socket has no read
timeout while idle. Incoming messages display as `<name> > <message>`.

A new `/hello` closes the previous socket and waits for the old receiver before
opening another connection. There is no separate registered boolean. The basic
text method checks transport readiness and prints `not connected` without a
socket; its outgoing echo is not delivery confirmation. Specific handshake error
messages are deferred; failures currently share the `retry` response.

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
| `HELLO alice` when taken | Reply `ERROR USERNAME_TAKEN`; close connection |
| Invalid initial command | Reply `ERROR EXPECTED_HELLO`; close connection |
| Invalid username | Reply `ERROR INVALID_USERNAME`; close connection |
| `SEND bob Hello!` | Bob receives `FROM alice Hello!` |
| `SEND me A note` | Sender receives `FROM alice A note` |
| Send to an unknown name | Reply `ERROR USER_NOT_FOUND <name>`; keep connection open |
| Write to a disconnected recipient fails | Reply `ERROR RECIPIENT_UNAVAILABLE <name>` |
| Invalid command after registration | Reply `ERROR EXPECTED_SEND`; keep connection open |

Each connection registers once; a second `HELLO` after success is invalid.
The client opens a fresh connection for every `/hello` request, closing its
previous connection first. After a rejected registration, retry with a new
connection rather than sending another HELLO on the rejected socket.
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
