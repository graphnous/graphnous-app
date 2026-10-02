package dev.graphnous.application.scan;

import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.COMPLETED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.FAILED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.PENDING;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.RUNNING;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.SKIPPED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.CHECKOUT;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.ENHANCE_RESULTS;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.ENHANCE_SCAN;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.PLAN;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.SCAN;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.STORE;
import static org.assertj.core.api.Assertions.assertThat;

class ScanStepsTest {

    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

    private final InMemoryScanStepRepository repository = new InMemoryScanStepRepository();

    private final ScanSteps steps = new ScanSteps(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    @Test
    void givesANewScanEveryStepInOrder() {
        steps.create(scanId);

        assertThat(repository.statuses(scanId)).containsExactly(
            Map.entry(CHECKOUT, PENDING),
            Map.entry(PLAN, PENDING),
            Map.entry(SCAN, PENDING),
            Map.entry(STORE, PENDING),
            Map.entry(ENHANCE_RESULTS, PENDING),
            Map.entry(ENHANCE_SCAN, PENDING)
        );
    }

    @Test
    void recordsARunningAndCompletedStep() {
        steps.create(scanId);

        steps.start(scanId, CHECKOUT);
        assertThat(repository.step(scanId, CHECKOUT).status()).isEqualTo(RUNNING);
        assertThat(repository.step(scanId, CHECKOUT).startedAt()).isEqualTo(NOW);

        steps.complete(scanId, CHECKOUT);
        assertThat(repository.step(scanId, CHECKOUT).status()).isEqualTo(COMPLETED);
        assertThat(repository.step(scanId, CHECKOUT).finishedAt()).isEqualTo(NOW);
    }

    @Test
    void skipsAStepThatDoesNotApply() {
        steps.create(scanId);

        steps.skip(scanId, CHECKOUT);

        assertThat(repository.step(scanId, CHECKOUT).status()).isEqualTo(SKIPPED);
        assertThat(repository.step(scanId, PLAN).status()).isEqualTo(PENDING);
    }

    @Test
    void skipsTheStepsAfterAFailedOne() {
        steps.create(scanId);
        steps.start(scanId, CHECKOUT);
        steps.complete(scanId, CHECKOUT);
        steps.start(scanId, PLAN);

        steps.fail(scanId, PLAN, "No pom.xml");

        assertThat(repository.step(scanId, PLAN).error()).isEqualTo("No pom.xml");
        assertThat(repository.statuses(scanId).values())
            .containsExactly(COMPLETED, FAILED, SKIPPED, SKIPPED, SKIPPED, SKIPPED);
    }

    @Test
    void failsTheRunningStepOfAScanThatWillNotFinish() {
        steps.create(scanId);
        steps.start(scanId, CHECKOUT);
        steps.complete(scanId, CHECKOUT);
        steps.start(scanId, PLAN);
        steps.complete(scanId, PLAN);
        steps.start(scanId, SCAN);

        steps.failRunning(scanId, "Scan timed out: no progress in PT1H");

        assertThat(repository.step(scanId, SCAN).error()).isEqualTo("Scan timed out: no progress in PT1H");
        assertThat(repository.statuses(scanId).values())
            .containsExactly(COMPLETED, COMPLETED, FAILED, SKIPPED, SKIPPED, SKIPPED);
    }

    @Test
    void keepsAStepThatRecoveryFailed() {
        steps.create(scanId);
        steps.start(scanId, SCAN);
        steps.failRunning(scanId, "Scan interrupted by a server restart");

        // The scan's thread reports afterwards
        steps.complete(scanId, SCAN);
        steps.start(scanId, STORE);

        assertThat(repository.step(scanId, SCAN).status()).isEqualTo(FAILED);
        assertThat(repository.step(scanId, STORE).status()).isEqualTo(SKIPPED);
    }
}
