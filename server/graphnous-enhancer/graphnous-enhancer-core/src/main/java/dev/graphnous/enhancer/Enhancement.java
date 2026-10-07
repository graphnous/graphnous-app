package dev.graphnous.enhancer;

import java.util.List;

/**
 * One addition an enhancer made to a scan result.
 *
 * @param nodes         the nodes it adds
 * @param relationships the relationships it adds
 */
public record Enhancement(
    List<Node> nodes,
    List<Relationship> relationships
) {

    public Enhancement {
        nodes = List.copyOf(nodes);
        relationships = List.copyOf(relationships);
    }
}
