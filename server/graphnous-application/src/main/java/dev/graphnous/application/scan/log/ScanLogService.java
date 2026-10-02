package dev.graphnous.application.scan.log;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.project.scanner.logs.ScanLogPublisher;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog;

import java.time.Instant;
import java.util.List;

/**
 * Writes scan logs for the scans the server runs, and reads them for callers
 * that may read the scan.
 */
public class ScanLogService {

    private final ScanLogRepository repository;
    private final ScanLogPublisher publisher;

    private final ScanService scanService;

    public ScanLogService(
        final ScanLogRepository repository,
        final ScanLogPublisher publisher,
        final ScanService scanService
    ) {
        this.repository = repository;
        this.publisher = publisher;

        this.scanService = scanService;
    }

    public ScanLog log(
        final Scan.ScanId scanId,
        final ScanLog.ScanLogLevel level,
        final String message
    ) {
        final var log = new ScanLog(
            ScanLog.ScanLogId.generate(),
            scanId,
            nextSequence(scanId),
            Instant.now(),
            level,
            message
        );

        final var saved = repository.save(log);

        publisher.publish(saved);

        return saved;
    }

    /**
     * A page of the scan's logs, ordered by sequence.
     *
     * @param page the zero-based page number
     * @param size the number of logs per page, from 1 to 1000
     */
    public Page<ScanLog> getLogs(
        final RequestContext context,
        final Scan.ScanId scanId,
        final int page,
        final int size
    ) {
        if (page < 0 || size < 1 || size > 1000) {
            throw new IllegalArgumentException(
                "Invalid page %d of size %d".formatted(page, size)
            );
        }

        // Checks the caller may read the scan, and that it exists
        scanService.getScan(context, scanId);

        final var logs = repository.findByScanId(scanId);

        final var from = (int) Math.min((long) page * size, logs.size());
        final var to = Math.min(from + size, logs.size());

        return new Page<>(
            logs.subList(from, to),
            page,
            size,
            logs.size(),
            (logs.size() + size - 1) / size
        );
    }

    public List<ScanLog> getLogsAfter(
        final RequestContext context,
        final Scan.ScanId scanId,
        final long sequence
    ) {
        scanService.getScan(context, scanId);

        return repository.findByScanIdAfter(scanId, sequence);
    }

    private long nextSequence(final Scan.ScanId scanId) {
        return this.repository.findLatestSequence(scanId)
                .orElse(0L) + 1;
    }
}
