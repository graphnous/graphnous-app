package dev.graphnous.scanner.plan;

import dev.graphnous.core.model.ScanTarget;

import java.util.List;

/**
 * @param targets  the targets to scan
 * @param warnings problems found while planning that do not stop a target
 *                 from being scanned, such as an undetectable language version
 * @param leftOut  the targets found in languages the plan was not asked to
 *                 scan
 */
public record ScanPlan(
    List<ScanTarget> targets,
    List<String> warnings,
    List<ScanTarget> leftOut
) {

    public ScanPlan {
        targets = List.copyOf(targets);
        warnings = List.copyOf(warnings);
        leftOut = List.copyOf(leftOut);
    }

    public ScanPlan(
        final List<ScanTarget> targets,
        final List<String> warnings
    ) {
        this(targets, warnings, List.of());
    }

    public ScanPlan(final List<ScanTarget> targets) {
        this(targets, List.of(), List.of());
    }
}
