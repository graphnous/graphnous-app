package dev.graphnous.domain.scan;

import java.time.Instant;
import java.util.UUID;

/**
 * One step of executing a scan. A scan has every step, in order, from the
 * moment it is created; each step moves from PENDING to RUNNING to
 * COMPLETED or FAILED, or is SKIPPED when an earlier step failed.
 *
 * @param startedAt  when the step started running; null until then
 * @param finishedAt when it completed, failed or was skipped
 * @param error      why the step failed; null otherwise
 */
public record ScanStep(
    ScanStepId id,
    Scan.ScanId scanId,
    ScanStepType type,
    ScanStepStatus status,
    Instant startedAt,
    Instant finishedAt,
    String error
) {

    public record ScanStepId(UUID id) {
        public static ScanStepId generate() {
            return new ScanStepId(UUID.randomUUID());
        }
    }

    /**
     * The steps of a scan, in the order they run.
     */
    public enum ScanStepType {
        CHECKOUT,
        PLAN,
        SCAN,
        STORE,
        ENHANCE_RESULTS,
        ENHANCE_SCAN
    }

    public enum ScanStepStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        SKIPPED;

        public boolean isFinished() {
            return this == COMPLETED || this == FAILED || this == SKIPPED;
        }
    }

    public static ScanStep pending(
        final Scan.ScanId scanId,
        final ScanStepType type
    ) {
        return new ScanStep(ScanStepId.generate(), scanId, type, ScanStepStatus.PENDING, null, null, null);
    }

    /**
     * Only a pending step starts.
     */
    public ScanStep start(final Instant at) {
        return status == ScanStepStatus.PENDING
            ? new ScanStep(id, scanId, type, ScanStepStatus.RUNNING, at, null, null)
            : this;
    }

    /**
     * Only a running step completes: a step that recovery already failed
     * stays failed.
     */
    public ScanStep complete(final Instant at) {
        return status == ScanStepStatus.RUNNING
            ? new ScanStep(id, scanId, type, ScanStepStatus.COMPLETED, startedAt, at, null)
            : this;
    }

    /**
     * Only a running step fails; it keeps the first error.
     */
    public ScanStep fail(
        final Instant at,
        final String reason
    ) {
        return status == ScanStepStatus.RUNNING
            ? new ScanStep(id, scanId, type, ScanStepStatus.FAILED, startedAt, at, reason)
            : this;
    }

    /**
     * Only a pending step is skipped.
     */
    public ScanStep skip(final Instant at) {
        return status == ScanStepStatus.PENDING
            ? new ScanStep(id, scanId, type, ScanStepStatus.SKIPPED, null, at, null)
            : this;
    }
}
