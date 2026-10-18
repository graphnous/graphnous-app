package dev.graphnous.api.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SseHeartbeatTest {

    private final SseHeartbeat heartbeat = new SseHeartbeat();

    @Test
    void sendsEachStreamAComment() {
        final var first = new RecordingEmitter(false);
        final var second = new RecordingEmitter(false);

        heartbeat.register(first);
        heartbeat.register(second);

        heartbeat.beat();
        heartbeat.beat();

        assertThat(first.sent).containsExactly(":keepalive\n\n", ":keepalive\n\n");
        assertThat(second.sent).containsExactly(":keepalive\n\n", ":keepalive\n\n");
        assertThat(heartbeat.size()).isEqualTo(2);
    }

    @Test
    void endsAStreamWhoseClientWentAway() {
        final var gone = new RecordingEmitter(true);
        final var open = new RecordingEmitter(false);

        heartbeat.register(gone);
        heartbeat.register(open);

        heartbeat.beat();

        assertThat(gone.completed).isTrue();
        assertThat(open.completed).isFalse();
        assertThat(heartbeat.size()).isEqualTo(1);

        heartbeat.beat();

        assertThat(open.sent).hasSize(2);
    }

    @Test
    void beatsNothingWithoutStreams() {
        heartbeat.beat();

        assertThat(heartbeat.size()).isZero();
    }

    /**
     * An emitter without a response, which keeps what it was sent, or
     * fails to send it as one whose client went away.
     */
    private static final class RecordingEmitter extends SseEmitter {

        private final boolean gone;
        private final List<String> sent = new ArrayList<>();
        private boolean completed;

        RecordingEmitter(final boolean gone) {
            super(0L);
            this.gone = gone;
        }

        @Override
        public void send(final SseEventBuilder builder) throws IOException {
            if (gone) {
                throw new IOException("Broken pipe");
            }

            sent.add(builder.build().stream()
                .map(part -> part.getData() instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(part.getData()))
                .collect(Collectors.joining()));
        }

        @Override
        public synchronized void complete() {
            completed = true;
        }
    }
}
