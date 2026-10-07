package dev.graphnous.application.enhancer;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.enhancer.Enhancements;

import java.util.List;

/**
 * Stores what enhancers added to a scan in the graph of its results.
 */
public interface EnhancementRepository {

    /**
     * Records each enhancer that ran on the scan, by its name and version,
     * then creates the nodes it added, each enhancing its source node, and
     * then their relationships, matching both ends by id among the scan's
     * nodes. Runs apart from storing the results, so a failure leaves the
     * stored results alone.
     *
     * @throws IllegalArgumentException when a label or relationship type is
     *                                  not a valid name
     */
    void save(Scan.ScanId scanId, List<Enhancements> enhancements);

}
