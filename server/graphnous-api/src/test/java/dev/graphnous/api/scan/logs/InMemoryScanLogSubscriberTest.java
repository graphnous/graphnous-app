package dev.graphnous.api.scan.logs;

import dev.graphnous.application.project.scanner.logs.ScanLogListener;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class InMemoryScanLogSubscriberTest {

    private final InMemoryScanLogSubscriber subscriber = new InMemoryScanLogSubscriber();

    private final Scan.ScanId scan = Scan.ScanId.generate();

    @Test
    void deliversTheLogsOfTheSubscribedScanOnly() {
        final var received = new CopyOnWriteArrayList<String>();
        subscriber.subscribe(scan, log -> received.add(log.message()));

        subscriber.publish(log(scan, "mine"));
        subscriber.publish(log(Scan.ScanId.generate(), "someone else's"));

        assertThat(received).containsExactly("mine");
    }

    @Test
    void deliversToEveryListenerOfTheScan() {
        final var first = new CopyOnWriteArrayList<String>();
        final var second = new CopyOnWriteArrayList<String>();
        subscriber.subscribe(scan, log -> first.add(log.message()));
        subscriber.subscribe(scan, log -> second.add(log.message()));

        subscriber.publish(log(scan, "hello"));

        assertThat(first).containsExactly("hello");
        assertThat(second).containsExactly("hello");
    }

    @Test
    void stopsDeliveringAfterUnsubscribing() {
        final var received = new CopyOnWriteArrayList<String>();
        final ScanLogListener listener = log -> received.add(log.message());

        subscriber.subscribe(scan, listener);
        subscriber.publish(log(scan, "before"));
        subscriber.unsubscribe(scan, listener);
        subscriber.publish(log(scan, "after"));

        assertThat(received).containsExactly("before");
    }

    @Test
    void publishesWithoutListeners() {
        assertThatCode(() -> subscriber.publish(log(scan, "nobody listens"))).doesNotThrowAnyException();
    }

    @Test
    void keepsDeliveringWhenAListenerFails() {
        final var received = new CopyOnWriteArrayList<String>();

        // Like a log stream the client already closed
        subscriber.subscribe(scan, log -> {
            throw new IllegalStateException("ResponseBodyEmitter has already completed");
        });
        subscriber.subscribe(scan, log -> received.add(log.message()));

        assertThatCode(() -> subscriber.publish(log(scan, "still delivered"))).doesNotThrowAnyException();
        assertThat(received).containsExactly("still delivered");
    }

    @Test
    void keepsListenersThatSubscribeWhileOthersUnsubscribe() throws InterruptedException {
        final int rounds = 2_000;
        final var delivered = new AtomicInteger();
        final var executor = Executors.newFixedThreadPool(2);
        final var done = new CountDownLatch(2);

        try {
            // One thread keeps subscribing listeners that stay, while the
            // other subscribes and unsubscribes its own
            executor.submit(() -> {
                for (int i = 0; i < rounds; i++) {
                    subscriber.subscribe(scan, log -> delivered.incrementAndGet());
                }
                done.countDown();
            });
            executor.submit(() -> {
                for (int i = 0; i < rounds; i++) {
                    final ScanLogListener listener = log -> { };
                    subscriber.subscribe(scan, listener);
                    subscriber.unsubscribe(scan, listener);
                }
                done.countDown();
            });

            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        subscriber.publish(log(scan, "count"));

        // Every listener that stayed is still subscribed
        assertThat(delivered).hasValue(rounds);
    }

    private static ScanLog log(final Scan.ScanId scanId, final String message) {
        return new ScanLog(
            ScanLog.ScanLogId.generate(),
            scanId,
            1,
            Instant.now(),
            ScanLog.ScanLogLevel.INFO,
            message
        );
    }
}
