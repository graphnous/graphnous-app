package dev.graphnous.api.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the open server-sent event streams alive: a comment to each, now
 * and then, which a browser's EventSource leaves unseen, so a proxy in
 * between does not close a stream with nothing to say for a while. A
 * stream whose client went away fails to take it, and is completed then,
 * which ends what listens for it.
 */
@Component
public class SseHeartbeat {

    static final String COMMENT = "keepalive";

    private static final Logger log = LoggerFactory.getLogger(SseHeartbeat.class);

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    /**
     * Keeps the stream alive until it ends, however it ends.
     */
    public void register(final SseEmitter emitter) {
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));
    }

    @Scheduled(
        fixedRateString = "${graphnous.sse.heartbeat-interval:PT25S}",
        initialDelayString = "${graphnous.sse.heartbeat-interval:PT25S}"
    )
    public void beat() {
        for (final var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment(COMMENT));
            } catch (final IOException | IllegalStateException e) {
                // The client went away, or the stream ended meanwhile
                log.debug("A stream took no heartbeat: ending it", e);
                emitters.remove(emitter);
                emitter.complete();
            }
        }
    }

    int size() {
        return emitters.size();
    }
}
