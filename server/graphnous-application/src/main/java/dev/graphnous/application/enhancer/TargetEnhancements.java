package dev.graphnous.application.enhancer;

import dev.graphnous.scanner.model.ScanTarget;

import java.util.List;

/**
 * What the target-scoped enhancers did to one scan result.
 *
 * @param target       the scanned target
 * @param enhancements the enhancers, in the order they ran
 */
public record TargetEnhancements(
    ScanTarget target,
    List<Enhancement> enhancements
) {

    public TargetEnhancements {
        enhancements = List.copyOf(enhancements);
    }
}
