package dev.graphnous.scanner.docker;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineSplitterTest {

    private final List<String> lines = new ArrayList<>();

    private final LineSplitter splitter = new LineSplitter(lines::add);

    @Test
    void emitsEachCompleteLine() {
        accept("first\nsecond\n");

        assertThat(lines).containsExactly("first", "second");
    }

    @Test
    void joinsLinesSplitAcrossChunks() {
        accept("hel");
        accept("lo\nwor");
        accept("ld\n");

        assertThat(lines).containsExactly("hello", "world");
    }

    @Test
    void keepsIncompleteLineUntilFlushed() {
        accept("done\npartial");

        assertThat(lines).containsExactly("done");

        splitter.flush();

        assertThat(lines).containsExactly("done", "partial");
    }

    @Test
    void flushWithoutPendingOutputEmitsNothing() {
        accept("line\n");
        splitter.flush();

        assertThat(lines).containsExactly("line");
    }

    @Test
    void stripsWindowsLineEndings() {
        accept("first\r\nsecond\r\n");

        assertThat(lines).containsExactly("first", "second");
    }

    @Test
    void keepsEmptyLines() {
        accept("a\n\nb\n");

        assertThat(lines).containsExactly("a", "", "b");
    }

    @Test
    void decodesCharactersSplitAcrossChunks() {
        final var bytes = "café ✓\n".getBytes(StandardCharsets.UTF_8);

        for (final var b : bytes) {
            splitter.accept(new byte[] {b});
        }

        assertThat(lines).containsExactly("café ✓");
    }

    private void accept(final String text) {
        splitter.accept(text.getBytes(StandardCharsets.UTF_8));
    }
}
