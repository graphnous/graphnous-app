package dev.graphnous.application.scan;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps scan steps in memory, for tests of what records a scan's execution.
 */
public class InMemoryScanStepRepository implements ScanStepRepository {

    private final Map<ScanStep.ScanStepId, ScanStep> steps = new LinkedHashMap<>();

    @Override
    public void saveAll(final List<ScanStep> steps) {
        steps.forEach(this::save);
    }

    @Override
    public ScanStep save(final ScanStep step) {
        steps.put(step.id(), step);

        return step;
    }

    @Override
    public List<ScanStep> findByScanId(final Scan.ScanId scanId) {
        return steps.values()
            .stream()
            .filter(step -> step.scanId().equals(scanId))
            .sorted(Comparator.comparing(ScanStep::type))
            .toList();
    }

    @Override
    public void deleteByScanId(final Scan.ScanId scanId) {
        steps.values().removeIf(step -> step.scanId().equals(scanId));
    }

    /**
     * The status of each of the scan's steps, in order.
     */
    public Map<ScanStep.ScanStepType, ScanStep.ScanStepStatus> statuses(final Scan.ScanId scanId) {
        final var statuses = new LinkedHashMap<ScanStep.ScanStepType, ScanStep.ScanStepStatus>();

        findByScanId(scanId).forEach(step -> statuses.put(step.type(), step.status()));

        return statuses;
    }

    public ScanStep step(
        final Scan.ScanId scanId,
        final ScanStep.ScanStepType type
    ) {
        return findByScanId(scanId).stream().filter(step -> step.type() == type).findFirst().orElseThrow();
    }
}
