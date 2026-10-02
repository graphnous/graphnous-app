package dev.graphnous.scanner.docker;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Turns chunks of process output into lines. Docker delivers output in
 * frames that can end in the middle of a line (or a UTF-8 character), so
 * bytes are buffered until a newline arrives.
 */
class LineSplitter {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private final Consumer<String> lines;

    LineSplitter(final Consumer<String> lines) {
        this.lines = lines;
    }

    synchronized void accept(final byte[] bytes) {
        for (final var b : bytes) {
            if (b == '\n') {
                emit();
            } else {
                buffer.write(b);
            }
        }
    }

    /**
     * Emits the last line when the output does not end with a newline.
     */
    synchronized void flush() {
        if (buffer.size() > 0) {
            emit();
        }
    }

    private void emit() {
        var line = buffer.toString(StandardCharsets.UTF_8);

        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }

        buffer.reset();
        lines.accept(line);
    }
}
