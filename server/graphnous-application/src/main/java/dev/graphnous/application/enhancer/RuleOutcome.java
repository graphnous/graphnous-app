package dev.graphnous.application.enhancer;

/**
 * What one rule of an enhancer did to a scan's graph.
 *
 * @param ruleId               the rule's id in its manifest
 * @param matched              the nodes the rule matched
 * @param labelsAdded          labels added to those nodes
 * @param propertiesSet        properties written, including the record of
 *                             which rule wrote them
 * @param relationshipsCreated relationships the rule created
 * @param skipped              matched nodes left without a relationship
 *                             because an 'exactlyOne' target was missing or
 *                             not unique
 */
public record RuleOutcome(
    String ruleId,
    long matched,
    long labelsAdded,
    long propertiesSet,
    long relationshipsCreated,
    long skipped
) {
}
