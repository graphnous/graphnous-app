package dev.graphnous.application.scan;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;
import dev.graphnous.domain.scan.ScanStep.ScanStepStatus;
import dev.graphnous.domain.scan.ScanStep.ScanStepType;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Records the execution of scans: each scan's steps and how they went.
 * <p>
 * A failed step skips the steps after it. Steps only move forward (see
 * {@link ScanStep}), so a scan thread that reports after recovery already
 * failed its step changes nothing.
 */
public class ScanSteps {

    private final ScanStepRepository repository;
    private final Clock clock;

    public ScanSteps(
        final ScanStepRepository repository,
        final Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Gives a new scan every step, pending.
     */
    public void create(final Scan.ScanId scanId) {
        repository.saveAll(
            Arrays.stream(ScanStepType.values())
                .map(type -> ScanStep.pending(scanId, type))
                .toList()
        );
    }

    public List<ScanStep> steps(final Scan.ScanId scanId) {
        return repository.findByScanId(scanId);
    }

    public void start(
        final Scan.ScanId scanId,
        final ScanStepType type
    ) {
        update(scanId, type, step -> step.start(clock.instant()));
    }

    public void complete(
        final Scan.ScanId scanId,
        final ScanStepType type
    ) {
        update(scanId, type, step -> step.complete(clock.instant()));
    }

    /**
     * Skips a step that does not apply to the scan, such as the checkout of
     * a scan whose results were uploaded.
     */
    public void skip(
        final Scan.ScanId scanId,
        final ScanStepType type
    ) {
        update(scanId, type, step -> step.skip(clock.instant()));
    }

    /**
     * Fails the step with the error, and skips the steps after it.
     */
    public void fail(
        final Scan.ScanId scanId,
        final ScanStepType type,
        final String error
    ) {
        update(scanId, type, step -> step.fail(clock.instant(), error));
        skipAfter(scanId, type);
    }

    /**
     * Fails whichever step is running, for a scan that will never finish,
     * and skips the steps that did not start.
     */
    public void failRunning(
        final Scan.ScanId scanId,
        final String error
    ) {
        final var now = clock.instant();

        for (final var step : repository.findByScanId(scanId)) {
            final var updated = step.status() == ScanStepStatus.RUNNING
                ? step.fail(now, error)
                : step.skip(now);

            if (!updated.equals(step)) {
                repository.save(updated);
            }
        }
    }

    private void skipAfter(
        final Scan.ScanId scanId,
        final ScanStepType failed
    ) {
        final var now = clock.instant();

        for (final var step : repository.findByScanId(scanId)) {
            if (step.type().ordinal() > failed.ordinal()) {
                final var skipped = step.skip(now);

                if (!skipped.equals(step)) {
                    repository.save(skipped);
                }
            }
        }
    }

    private void update(
        final Scan.ScanId scanId,
        final ScanStepType type,
        final UnaryOperator<ScanStep> change
    ) {
        repository.findByScanId(scanId)
            .stream()
            .filter(step -> step.type() == type)
            .findFirst()
            .ifPresent(step -> {
                final var updated = change.apply(step);

                if (!updated.equals(step)) {
                    repository.save(updated);
                }
            });
    }
}
