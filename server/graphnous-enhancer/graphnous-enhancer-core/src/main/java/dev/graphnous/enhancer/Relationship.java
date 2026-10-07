package dev.graphnous.enhancer;

/**
 * A relationship an enhancer adds to a scan's graph, between nodes of the
 * scan result or nodes enhancers add, by their ids (see {@link Node}). The
 * {@code ENHANCE} relationship from a node's source is added for it.
 *
 * @param sourceId the id of the node it starts at
 * @param targetId the id of the node it ends at
 * @param type     the relationship's type
 */
public record Relationship(
    String sourceId,
    String targetId,
    String type
) {
}
