package dev.graphnous.domain.scan;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ScanStepTest {

    private static final Instant STARTED = Instant.parse("2026-09-30T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-09-30T10:01:00Z");

    private final ScanStep pending = ScanStep.pending(Scan.ScanId.generate(), ScanStep.ScanStepType.CHECKOUT);

    @Test
    void runsAndCompletes() {
        final var completed = pending.start(STARTED).complete(FINISHED);

        assertThat(completed.status()).isEqualTo(ScanStep.ScanStepStatus.COMPLETED);
        assertThat(completed.startedAt()).isEqualTo(STARTED);
        assertThat(completed.finishedAt()).isEqualTo(FINISHED);
        assertThat(completed.error()).isNull();
    }

    @Test
    void keepsTheErrorOfAFailedStep() {
        final var failed = pending.start(STARTED).fail(FINISHED, "Repository not found");

        assertThat(failed.status()).isEqualTo(ScanStep.ScanStepStatus.FAILED);
        assertThat(failed.error()).isEqualTo("Repository not found");
        assertThat(failed.finishedAt()).isEqualTo(FINISHED);
    }

    @Test
    void staysFailedWhenItLaterCompletes() {
        // A scan thread that finishes after recovery failed its step
        final var failed = pending.start(STARTED).fail(FINISHED, "Scan timed out");

        assertThat(failed.complete(FINISHED.plusSeconds(60))).isEqualTo(failed);
        assertThat(failed.fail(FINISHED.plusSeconds(60), "Other")).isEqualTo(failed);
    }

    @Test
    void skipsOnlyAPendingStep() {
        final var skipped = pending.skip(FINISHED);

        assertThat(skipped.status()).isEqualTo(ScanStep.ScanStepStatus.SKIPPED);
        assertThat(skipped.startedAt()).isNull();

        final var running = pending.start(STARTED);
        assertThat(running.skip(FINISHED)).isEqualTo(running);
    }

    @Test
    void startsOnlyOnce() {
        final var running = pending.start(STARTED);

        assertThat(running.start(FINISHED)).isEqualTo(running);
        assertThat(pending.complete(FINISHED)).isEqualTo(pending);
    }
}
