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

    @Test
    void isOfTheCommitItCheckedOutAndKeepsWhatItWasAskedFor() {
        final var commit = "4f2a9c1e88d0a19c3e7f0b42c0ffee1234567890";
        final var requested = new Scan(
            Scan.ScanId.generate(),
            Project.ProjectId.generate(),
            Scan.ScanStatus.RUNNING,
            Scan.SourceRevision.requested("v1.2.0", "main"),
            CREATED,
            CREATED,
            CREATED
        );

        assertThat(requested.revision()).isEqualTo(new Scan.SourceRevision(null, "main", "v1.2.0"));

        final var checkedOut = requested.withRevision(commit);

        assertThat(checkedOut.revision()).isEqualTo(new Scan.SourceRevision(commit, "main", "v1.2.0"));
        assertThat(checkedOut).usingRecursiveComparison().ignoringFields("revision").isEqualTo(requested);
    }

    @Test
    void wasAskedForTheRevisionItWasUploadedWith() {
        assertThat(new Scan.SourceRevision("abc123", "main").requestedRevision()).isEqualTo("abc123");
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
