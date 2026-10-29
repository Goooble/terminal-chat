# Terminal Chat learning record

## Mentoring preferences

- The user designs classes, objects, functions, and control flow in plain English or pseudocode. Codex helps vet the design before implementing it.
- Introduce useful unfamiliar concepts and alternatives when the project creates a relevant opportunity; explain their benefits and tradeoffs.
- Keep this record current for future agent reference. An explanation alone does not demonstrate understanding.

## Introduced

| Topic | Project connection | Evidence and next check |
| --- | --- | --- |
| Terminal input loop | Read a line, interpret a command or message, and repeat until exit. | Explained by Codex; user understanding has not yet been checked. |
| Application state | The friends collection and selected recipient persist between input lines; the initial recipient is `me`. | Explained by Codex; user defined the desired selection behavior, but has not yet proposed state ownership in code. |
| Set and insertion order | A `LinkedHashSet` prevents duplicate friend names while preserving insertion order. | Explained by Codex using the initial implementation; understanding has not yet been checked. |
| Java packages and source layout | A package provides a namespace and an access boundary; source folders conventionally mirror its dotted name under `src/main/java`. Tests conventionally go under `src/test/java`. | User proposed packages by technical role; path and access rules have not yet been checked. |
| Imports and package boundaries | Imports let source code use simple type names; subpackages are distinct packages and do not share package-private access. | Introduced; understanding has not yet been checked. |
| Models versus interfaces | Models represent application concepts and data; an interface defines a behavioral contract. Interfaces belong near the responsibility they describe, not automatically in models. | User asked whether models define interfaces; distinction explained with Friend and Message examples. Awaiting application. |
| Controller and service responsibilities | A controller interprets user intent and coordinates work; a service owns application rules such as adding a friend. Terminal input/output should remain unaware of those rules. | User proposed separate display/input classes and a controller that routes commands to a service. This is a concrete application of separation of concerns. |
| Model/value object tradeoff | A `Friend` model can represent a friend and later hold identity or status, but a `String` may be enough while a friend has only a name. Add a model when it protects rules or carries meaningful data. | Introduced while designing `/add`; user has not yet selected between `Friend` and `String`. |
| Operation result types | A service can return `boolean` for two outcomes or an enum/result object for distinct outcomes such as added, duplicate, and invalid. The controller maps that outcome to display text. | Introduced for `addFriend`; awaiting the user's method contract. |
| Network endpoint versus user identity | TCP connects an IP address and port to another IP address and port; those endpoints identify running processes, not stable people. An application layer maps a user ID to the user's current connection. | Introduced after the user questioned how `/add` works across devices; architecture choice is pending. |
| Client-server relay architecture | Each terminal client makes an outbound TCP connection to one known server. The server tracks connected users and relays messages to the recipient's socket. This avoids requiring every user to expose a listening port. | Recommended as the first network architecture; user has not yet selected it. |
| Peer-to-peer tradeoffs | Direct peers need a reachable address and listening port. LAN addresses can work locally, while communication across routers generally requires public reachability, port forwarding, NAT traversal, or a relay. | Introduced as an alternative to a central server. |
| Stable ID versus username/display name | A server-generated ID can remain stable while a username or display name changes. Usernames are convenient lookup keys but require uniqueness and later authentication to prevent impersonation. | Introduced for deciding what `/add` accepts. |
| Application protocol | TCP supplies an ordered byte stream; the app must define message boundaries and request types such as register, add contact, and send message. | Introduced conceptually; protocol format has not yet been designed. |
| Plain Java TCP server | `ServerSocket` binds a port and waits in `accept()`; each accepted `Socket` represents one client connection. A client creates a `Socket` using the server host and port. | Introduced after the user asked how to run a server without Spring Boot; no implementation designed yet. |
| Framework versus server process | A server is a long-running program that listens for connections. Spring Boot provides higher-level application and HTTP infrastructure but is not required to create a raw TCP server. | Explained conceptually; understanding has not yet been checked. |
| Blocking I/O and connection concurrency | `accept()` and socket reads wait for work. A chat server must avoid letting one client's read loop prevent it from accepting or reading other clients; a thread-per-client/executor is a simple Java 11 starting model. | Introduced; class ownership and lifecycle remain to be designed. |
| Persistent account directory versus session registry | Registered accounts must survive disconnects and server restarts; live sessions only map authenticated user IDs to current client connections and disappear on disconnect. | Introduced while vetting the user's signup/login design; component ownership remains to be designed. |
| Authentication versus identification | A claimed username identifies an account, while successful password verification authenticates that the connection may act as that account. Later commands should use the identity attached to the authenticated session. | Introduced through the proposed login flow; protocol details remain pending. |
| Repository abstraction | A `UserRepository` contract can let authentication use the same operations with an in-memory implementation first and durable storage later. This is a concrete reason to introduce an interface. | Introduced as an option; user has not decided whether to use it. |
| Credential protection | Passwords should be stored as salted password hashes rather than plaintext, and real network login requires TLS so credentials are not exposed in transit. | Introduced as a correctness/security constraint; hashing and TLS implementation are deferred. |
| Atomic uniqueness | Two simultaneous signup requests for one username must not both succeed; uniqueness must be enforced as one atomic repository operation or by a database constraint. | Introduced as a concurrency edge case; behavior is agreed conceptually but implementation is pending. |
| Session nickname identity | Without account authentication, a unique username can identify one active connection for routing. It is a temporary handle rather than proof of a person's identity and may be reclaimed after disconnect. | Proposed as the intentionally limited identity model for the networking-focused MVP. |
| Connection lifecycle and registration timing | `accept()` provides a socket before the server knows its username. The handler must read the initial username, atomically register itself, and unregister in cleanup when the connection ends. | Introduced while vetting the user's server design; revised lifecycle pseudocode is pending. |
| Thread-safe shared registry | Multiple client-handler threads read and modify the same username map. Registration needs an atomic operation such as `putIfAbsent`, and disconnect cleanup should remove the expected handler mapping. | Introduced for duplicate names and concurrent disconnects; implementation is pending. |
| Serialized socket writes | Several sender threads may route messages to one recipient simultaneously. A recipient's `send` operation must serialize writes so protocol lines do not interfere with each other. | Introduced during message-routing review; user has not yet incorporated it into the design. |
| Expected outcomes versus exceptions | Choose based on the method contract and caller's response, not simply user error versus programmer error. A name reservation can normally succeed or be refused; a broken socket interrupts communication. Exceptions can represent external failures and domain rejections too. Use an enum/result when a boolean cannot distinguish meaningful outcomes. | User proposed the user-error/program-error distinction; refined with registration retry versus socket failure. Return-value alternative remains unselected. |

## Currently learning

- Client concurrency design review: user proposed two classes, `Display` for terminal read/print and `ConnectionHandler` for socket communication and coordinating two loops. One thread reads server messages and prints them; the other reads console input and sends it. Serialize short output operations, but never hold that output lock during blocking terminal input. A print lock prevents application output interleaving but does not preserve partially typed terminal input. Shutdown must account for console reads that do not reliably unblock through thread interruption.
- Registration versus deregistration locking: registry insertion and conditional removal are already atomic via `ConcurrentHashMap`. The handler's registration monitor coordinates publishing the handler with sending its first `OK`; it does not enforce username uniqueness across different handler objects. Cleanup requires only atomic owned removal, tolerates in-flight send failures, and can close a socket without waiting for a blocked sender's monitor. Explained in response to the user's lock-asymmetry question; understanding not yet demonstrated.
- Thread execution and shared state in the implemented server: `Thread.start()` runs each handler's `run()` independently; calling another handler's `send()` still executes on the caller's thread. Local variables/input reader are confined to one handler thread, while the registry and outgoing writers are shared. Explained with Alice and Charlie sending to Bob; not yet demonstrated by the user.
- Monitor locks: synchronized instance methods lock the receiver (`this`); one writer at a time per recipient, with independent locks for different handlers. Java monitors are reentrant, allowing `handleHello` to call `send` while holding the same lock. Handshake lock orders `OK` before forwarded messages. Explained; understanding not yet checked.
- Concurrent registry operations: atomic `putIfAbsent` prevents duplicate-name races, conditional removal protects other registrations, and thread-safe lookup does not guarantee the recipient stays connected until a later send. Existing I/O error handling covers that race. Slow recipients can block senders; an outgoing queue per connection is a later alternative.
- `ClientHandler.run()` lifecycle: socket byte streams are wrapped in UTF-8 character readers/writers and buffered text I/O; `readLine()` waits for a line or EOF, assigns the result in the loop condition, and branches on registration state. `catch` reports I/O failure; `finally` unregisters the owning handler and closes its socket. Introduced through the user's selected code; understanding has not yet been demonstrated.
- `try` versus try-with-resources: `try (ServerSocket listener = ...)` closes the successfully created listener when the block exits, including on exceptions. `throws IOException` propagates I/O failures rather than catching them. The `while (true)` accept loop is intentionally indefinite and blocks in `accept()` between connections. Current `ChatServer.java` compiles; the exact IDE diagnostic on `while` is not yet known.
- `Optional<ClientHandler>` in `ConnectionRegistry.find`: `Map.get` returns null for an absent name; `Optional.ofNullable` represents a present handler or an empty lookup result explicitly. Introduced while explaining the selected method; user understanding is not yet demonstrated.
- Low-level design: assigning responsibilities to classes and objects, deciding who owns state, defining function inputs and outputs, and describing interactions in pseudocode. Design practice is pending.
- Plain Java server design: accept multiple TCP clients, assign each active connection a unique username, and route messages. Authentication, durable accounts, password handling, and TLS are deferred.
- First server slice implemented in `terminalchat.server`: `ConnectionRegistry`, `ClientHandler`, and `ChatServer`. User's reading order: registry first, handler second, server startup third. Understanding of the Java concurrency syntax remains to be checked.

## Current learning resources

- Concurrency reading path (recommended, not yet read or demonstrated by the user):
  1. [Defining and Starting a Thread](https://docs.oracle.com/javase/tutorial/essential/concurrency/runthread.html): `Runnable`, `Thread.start()`, and why direct method calls stay on the calling thread.
  2. [Thread Interference](https://docs.oracle.com/javase/tutorial/essential/concurrency/interfere.html): interleaving and races in shared operations.
  3. [Synchronized Methods](https://docs.oracle.com/javase/tutorial/essential/concurrency/syncmeth.html): per-object exclusion for `ClientHandler.send()`.
  4. [Intrinsic Locks and Synchronization](https://docs.oracle.com/javase/tutorial/essential/concurrency/locksync.html): `synchronized(this)`, monitors, and reentrant calls during the handshake.
  5. [ConcurrentHashMap, Java 11](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): focus on `putIfAbsent`, `get`, and conditional `remove(key, value)` in the registry.
  6. [Memory Consistency Errors](https://docs.oracle.com/javase/tutorial/essential/concurrency/memconsist.html): happens-before and cross-thread visibility, after the basic locking flow is understood.
- These Oracle tutorials target JDK 8; the thread and monitor fundamentals apply to this Java 11 project. Use Java 11 API references for the exact available methods.
- Oracle, **All About Sockets**: conceptual overview of TCP sockets and the client/server roles.
- Oracle, **Reading from and Writing to a Socket**: connecting a client and wrapping socket streams in `BufferedReader` and `PrintWriter`.
- Oracle, **Writing the Server Side of a Socket**: `accept()`, per-client sockets, a protocol object, and the thread-per-client extension.
- Java SE 11 API references for `ServerSocket` and `Socket`: use as precise method references while designing and coding.
- Java SE 11 `Executors` reference: defer until the basic single-client exchange is understood, then use it when comparing connection concurrency approaches.

## Demonstrated learning

- Initial application of separation of concerns: the user explicitly separated terminal reading/display from friend management and chat state, and chose organization by technical role. More detailed class responsibilities remain pending.
- The user proposed separate console input and display classes plus a controller that routes commands to services, demonstrating the intended controller coordination role at a high level.
- The user designed the central identity lifecycle: unique signup credentials create a server-side registry entry, later connections log in, and `/add username` asks the server to resolve a registered user.
- The user corrected the project scope: authentication was suggested only for usability and is not a learning focus. The current focus is TCP server behavior and chat routing.
- The user assigned core server responsibilities coherently: `ChatServer` accepts sockets, `ClientHandler` owns socket input/output, and the registry maps usernames to handlers used for routing.
- The user correctly placed `HELLO <username>` parsing before registry registration and described duplicate-name and missing-recipient replies. Disconnect cleanup still needs registry removal in addition to socket closure.

## Agreed product behavior and design status

- `/add <name>` adds a friend; `/friends` lists friends.
- `/chat <name>` selects the recipient until another chat is selected.
- Startup selects self-chat (`me`), and ordinary messages use the current recipient.
- The initial local implementation was written by Codex before the user requested an explicit design-first workflow. Its internal architecture should not be treated as the user's design or evidence of their understanding.
- User chose organization by technical role: console for display/input, services for application behavior, and models for application concepts.
- Proposed interaction: terminal input goes to a controller; the controller routes `/add` to a service. Display is separate from input. Exact method contracts and state ownership remain pending.
- `/add` cannot be designed correctly until its product meaning is chosen: a local contact shortcut, a unilateral server-side contact, or a mutual friend request.
- Recommended learning path: run one relay server and two clients locally first, then place clients on separate devices that connect to the same server address.
- The server can be a normal Java `main` program using `ServerSocket`; Spring Boot is not a prerequisite.
- Central relay architecture selected. Registered identity is server-owned, and clients use usernames for lookup.
- The conceptual `ChatServer` contains the system, but its listening, account storage, authentication, live-session, and friend responsibilities should be assigned deliberately rather than placed in one class.
- Authentication and durable registration are explicitly deferred. The proposed MVP uses a unique username per active server connection; it does not claim secure or persistent identity.
- Agreed routing direction: an authenticated-free registered `ClientHandler` parses an addressed message, resolves the target handler through the registry, and calls the target's send operation. The server derives the sender name from the source handler.
- On the user's request to start implementation, Codex disclosed the return-value API choice: boolean `tryRegister`, `Optional<ClientHandler>` from `find`, and owner-specific `unregister`. This replaces the originally proposed domain exceptions; socket failures still use `IOException`.
- User selected retry after a duplicate username: keep the socket open, remain unregistered, and accept another `HELLO` on the same connection.
- Implemented a UTF-8 line protocol: `HELLO <username>` then `SEND <recipient> <text>`. Names are case-sensitive, 1–32 letters/digits/underscores/hyphens; `me` is reserved and resolves to the sender for `SEND me`.
- Implementation refinements: thread per client; atomic map registration; synchronized recipient writes; handshake acknowledgment ordered before forwarded messages; failed recipient writes do not disconnect the sender; cleanup removes only the owning registration.
- Verified on Java 11: compilation with `-Xlint:all` and real TCP checks for handshake retry, malformed commands, duplicate-name contention, UTF-8 delivery, self-chat, missing users, concurrent writes, rejected-client cleanup, and reconnect. The TCP check required running outside the sandbox to bind a local port.
- Limitations: no terminal client yet, no contacts/history, no delivery acknowledgment, unbounded thread count and input line lengths, and blocking writes to slow recipients. These are scoped learning-server limitations.
- Latest user client proposal supersedes the earlier separate input/display/controller sketch: two classes, `Display` (terminal reading and printing) and `ConnectionHandler` (socket ownership, sending/receiving, coordinating console and network threads). This is a reasonable small-client design; no separate controller is required for the first slice.

## Next step

Owner: user. Review the concurrency refinements to the two-class client: synchronized printing only, independent console/network reads, and explicit shutdown behavior. Start with HELLO/direct SEND before contact and chat-selection commands.

Owner: Codex. Commit and push the server/learning checkpoint as requested, explain the client-design review, then implement the resolved minimal client in a later slice and verify two terminals can exchange messages.
