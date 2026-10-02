package dev.graphnous.api.scan.logs;

import dev.graphnous.application.project.scanner.logs.ScanLogListener;
import dev.graphnous.application.project.scanner.logs.ScanLogPublisher;
import dev.graphnous.application.project.scanner.logs.ScanLogSubscriber;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Delivers the logs of a scan to the listeners subscribed to it, such as open
 * log streams. Scans publish from their own threads while requests subscribe
 * and unsubscribe, so the listeners are kept in concurrent collections.
 */
@Component
public class InMemoryScanLogSubscriber implements ScanLogSubscriber, ScanLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryScanLogSubscriber.class);

    private final Map<UUID, List<ScanLogListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void subscribe(Scan.ScanId scanId, ScanLogListener listener) {
        this.listeners
            .computeIfAbsent(
                scanId.id(),
                ignored -> new CopyOnWriteArrayList<>()
            ).add(listener);
    }

    @Override
    public void unsubscribe(Scan.ScanId scanId, ScanLogListener listener) {
        // Atomic, so a listener subscribing meanwhile is not dropped with the list
        this.listeners.computeIfPresent(scanId.id(), (id, scanListeners) -> {
            scanListeners.remove(listener);

            return scanListeners.isEmpty() ? null : scanListeners;
        });
    }

    /**
     * Delivers the log to every listener of its scan. A listener that fails,
     * for example a stream the client already closed, does not keep the log
     * from the others, nor fail the scan that wrote it.
     */
    @Override
    public void publish(
        final ScanLog scanLog
    ) {
        final var scanListeners = this.listeners.get(scanLog.scanId().id());

        if (Objects.isNull(scanListeners)) {
            return;
        }

        for (final var listener : scanListeners) {
            try {
                listener.onLog(scanLog);
            } catch (final RuntimeException e) {
                log.debug("Delivering a log of scan scanId={} failed", scanLog.scanId().id(), e);
            }
        }
    }
}
