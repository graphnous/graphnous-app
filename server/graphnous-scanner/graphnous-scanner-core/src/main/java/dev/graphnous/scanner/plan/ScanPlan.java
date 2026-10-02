package dev.graphnous.scanner.plan;

import dev.graphnous.scanner.model.ScanTarget;

import java.util.List;

/**
 * @param targets  the targets to scan
 * @param warnings problems found while planning that do not stop a target
 *                 from being scanned, such as an undetectable language version
 */
public record ScanPlan(
    List<ScanTarget> targets,
    List<String> warnings
) {

    public ScanPlan {
        targets = List.copyOf(targets);
        warnings = List.copyOf(warnings);
    }

    public ScanPlan(final List<ScanTarget> targets) {
        this(targets, List.of());
    }
}
