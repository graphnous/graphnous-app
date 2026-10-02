package dev.graphnous.domain.scan;

import dev.graphnous.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScanTest {

    private static final Instant CREATED = Instant.parse("2026-09-30T10:00:00Z");

    @Test
    void recordsWhenItStartsRunning() {
        final var started = Instant.parse("2026-09-30T10:01:00Z");

        final var running = scan(Scan.ScanStatus.QUEUED).withStatus(Scan.ScanStatus.RUNNING, started);

        assertThat(running.status()).isEqualTo(Scan.ScanStatus.RUNNING);
        assertThat(running.startedAt()).isEqualTo(started);
        assertThat(running.updatedAt()).isEqualTo(started);
        assertThat(running.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void keepsWhenItStartedAfterwards() {
        final var started = Instant.parse("2026-09-30T10:01:00Z");
        final var finished = Instant.parse("2026-09-30T10:05:00Z");

        final var completed = scan(Scan.ScanStatus.QUEUED)
            .withStatus(Scan.ScanStatus.RUNNING, started)
            .withStatus(Scan.ScanStatus.COMPLETED, finished);

        assertThat(completed.startedAt()).isEqualTo(started);
        assertThat(completed.updatedAt()).isEqualTo(finished);
    }

    @Test
    void hasNoStartTimeWhenItFailsBeforeRunning() {
        final var failed = scan(Scan.ScanStatus.PENDING)
            .withStatus(Scan.ScanStatus.QUEUED, CREATED.plusSeconds(1))
            .withStatus(Scan.ScanStatus.FAILED, CREATED.plusSeconds(2));

        assertThat(failed.startedAt()).isNull();
    }

    private static Scan scan(final Scan.ScanStatus status) {
        return new Scan(
            Scan.ScanId.generate(),
            Project.ProjectId.generate(),
            status,
            new Scan.SourceRevision("abc123", "main"),
            CREATED,
            CREATED,
            null
        );
    }
}
