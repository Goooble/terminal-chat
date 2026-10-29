package terminalchat.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/**
 * Owns terminal input and output for one client application.
 * One console thread calls readLine; other threads may call printLine.
 * Close after the console loop ends and connection workers have been stopped.
 */
public final class Renderer implements AutoCloseable {
    private final Terminal terminal;
    private final LineReader reader;
    private volatile boolean closed;

    public Renderer() throws IOException {
        terminal = TerminalBuilder.builder()
                .name("Terminal Chat")
                .system(true)
                .encoding(StandardCharsets.UTF_8)
                .build();
        try {
            reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .appName("Terminal Chat")
                    .parser(new DefaultParser()
                            .eofOnUnclosedQuote(false)
                            .eofOnEscapedNewLine(false))
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .option(LineReader.Option.ERASE_LINE_ON_FINISH, true)
                    .build();
        } catch (RuntimeException | Error failure) {
            try {
                terminal.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /**
     * Reads one editable line, without holding the output lock while waiting.
     * Returns null on Ctrl+D, Ctrl+C, or if the renderer is already closed.
     * The caller decides which commands to accept and when to quit.
     */
    public String readLine(String prompt) {
        Objects.requireNonNull(prompt, "prompt");
        if (closed) {
            return null;
        }
        try {
            return reader.readLine(prompt);
        } catch (EndOfFileException | UserInterruptException endOfInput) {
            return null;
        }
    }

    /**
     * Prints above any unfinished input and restores the input and cursor.
     * Safe for concurrent callers; late output during shutdown is ignored.
     */
    public synchronized void printLine(String message) {
        Objects.requireNonNull(message, "message");
        if (!closed) {//closed is volatile
            reader.printAbove(message);
        }
    }

    /** Restores and releases the terminal. Repeated calls are harmless. */
    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            terminal.close();
        }
    }
}
