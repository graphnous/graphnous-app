package dev.graphnous.application.enhancer;

import java.util.List;

/**
 * What an enhancer did to a scan result, or to a whole scan.
 *
 * @param enhancerId the id of the enhancer's manifest
 * @param applied    whether the enhancer applies, for example to the
 *                   scanned language
 * @param rules      what each rule that ran did, in manifest order; empty
 *                   when the enhancer does not apply
 */
public record Enhancement(
    String enhancerId,
    boolean applied,
    List<RuleOutcome> rules
) {

    public Enhancement {
        rules = List.copyOf(rules);
    }

    public static Enhancement notApplied(final String enhancerId) {
        return new Enhancement(enhancerId, false, List.of());
    }

    public List<String> ruleIds() {
        return rules.stream().map(RuleOutcome::ruleId).toList();
    }
}
