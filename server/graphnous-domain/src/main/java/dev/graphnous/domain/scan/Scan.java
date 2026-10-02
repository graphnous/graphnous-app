package dev.graphnous.domain.scan;

import dev.graphnous.domain.project.Project;

import java.time.Instant;
import java.util.UUID;

/**
 * @param startedAt when the scan started running; null until then
 */
public record Scan(
    ScanId id,
    Project.ProjectId projectId,
    ScanStatus status,
    SourceRevision revision,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt
) {

    /**
     * The scan with a new status at {@code at}. The first move to RUNNING
     * records when the scan started.
     */
    public Scan withStatus(
        final ScanStatus newStatus,
        final Instant at
    ) {
        final var started = startedAt == null && newStatus == ScanStatus.RUNNING
            ? at
            : startedAt;

        return new Scan(id, projectId, newStatus, revision, createdAt, at, started);
    }

    public record ScanId(UUID id) {
        public static Scan.ScanId generate() {
            return new Scan.ScanId(UUID.randomUUID());
        }
    }

    public enum ScanStatus {
        PENDING(false),
        QUEUED(false),
        RUNNING(false),
        COMPLETED(true),
        FAILED(true)
        ;

        private boolean endState;

        ScanStatus(boolean endState) {
            this.endState = endState;
        }

        public boolean isEndState() {
            return this.endState;
        }
    }

    public record SourceRevision(String revision, String branch)
    { }
}
