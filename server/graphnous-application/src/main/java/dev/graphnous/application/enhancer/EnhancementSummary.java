package dev.graphnous.application.enhancer;

import dev.graphnous.enhancer.Enhancements;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Says what enhancers added, for the log of a scan.
 */
public final class EnhancementSummary {

    private EnhancementSummary() {
    }

    /**
     * What each enhancer added over all results, e.g. "spring 1.0.0: 3
     * node(s) and 2 relationship(s)".
     */
    public static List<String> describe(final List<Enhancements> enhancements) {
        final var nodes = new LinkedHashMap<String, Integer>();
        final var relationships = new LinkedHashMap<String, Integer>();

        for (final var enhancer : enhancements) {
            final var name = enhancer.name() + " " + enhancer.version();

            for (final var enhancement : enhancer.enhancements()) {
                nodes.merge(name, enhancement.nodes().size(), Integer::sum);
                relationships.merge(name, enhancement.relationships().size(), Integer::sum);
            }

            nodes.putIfAbsent(name, 0);
            relationships.putIfAbsent(name, 0);
        }

        return nodes.keySet()
            .stream()
            .map(name -> name + ": " + nodes.get(name) + " node(s) and " + relationships.get(name) + " relationship(s)")
            .toList();
    }
}
