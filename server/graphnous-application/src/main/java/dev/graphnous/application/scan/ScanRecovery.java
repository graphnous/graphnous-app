package dev.graphnous.application.scan;

import dev.graphnous.application.project.scanner.ScanCanceller;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;

/**
 * Fails scans that will never finish, so they show what happened and can be
 * deleted: scans left active by a previous run of the server, and scans that
 * have not changed status within the timeout. What still runs for them, such
 * as their containers, is stopped.
 * <p>
 * Scans run inside the server, so this assumes a single server instance: on
 * startup, every active scan belonged to a run that is gone.
 */
public class ScanRecovery {

    private final ScanRepository scanRepository;
    private final ScanLogService scanLogService;
    private final ScanCanceller scanCanceller;
    private final ScanSteps scanSteps;

    private final Duration timeout;
    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(ScanRecovery.class);

    public ScanRecovery(
        final ScanRepository scanRepository,
        final ScanLogService scanLogService,
        final ScanCanceller scanCanceller,
        final ScanSteps scanSteps,
        final Duration timeout,
        final Clock clock
    ) {
        this.scanRepository = scanRepository;
        this.scanLogService = scanLogService;
        this.scanCanceller = scanCanceller;
        this.scanSteps = scanSteps;

        this.timeout = timeout;
        this.clock = clock;
    }

    /**
     * Fails every active scan and stops whatever a previous run left behind;
     * for use on startup, before new scans start.
     */
    public void failInterrupted() {
        for (final var scan : scanRepository.findActive()) {
            fail(scan, "Scan interrupted by a server restart");
        }

        // Also when no scan is active: the scans may not have been stored
        try {
            scanCanceller.cancelAll();
        } catch (final RuntimeException e) {
            log.error("Stopping the work of a previous run failed", e);
        }
    }

    /**
     * Fails the active scans whose status has not changed within the timeout.
     */
    public void failTimedOut() {
        final var deadline = clock.instant().minus(timeout);

        for (final var scan : scanRepository.findActive()) {
            if (scan.updatedAt().isBefore(deadline)
                && fail(scan, "Scan timed out: no progress in " + timeout)) {
                cancel(scan);
            }
        }
    }

    /**
     * Stops the scan's work after it was failed, so its thread sees the
     * status and leaves it.
     */
    private void cancel(final Scan scan) {
        try {
            scanCanceller.cancel(scan.id());
        } catch (final RuntimeException e) {
            log.error("Stopping scan scanId={} failed", scan.id().id(), e);
        }
    }

    /**
     * @return whether the scan was failed
     */
    private boolean fail(
        final Scan scan,
        final String reason
    ) {
        try {
            scanRepository.save(scan.withStatus(Scan.ScanStatus.FAILED, clock.instant()));

            log.warn("Failed scan scanId={} status={}: {}", scan.id().id(), scan.status(), reason);
        } catch (final RuntimeException e) {
            // One scan that cannot be updated does not keep the others active
            log.error("Failing scan scanId={} failed", scan.id().id(), e);
            return false;
        }

        try {
            scanSteps.failRunning(scan.id(), reason);
        } catch (final RuntimeException e) {
            log.warn("Failing the steps of scan scanId={} failed", scan.id().id(), e);
        }

        try {
            scanLogService.log(scan.id(), ScanLogLevel.ERROR, reason);
        } catch (final RuntimeException e) {
            log.warn("Logging failure of scan scanId={} failed", scan.id().id(), e);
        }

        return true;
    }
}
