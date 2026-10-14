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

    /**
     * Saves the enhancements as {@link #save} does, after removing what the
     * enhancers of the same names added to the scan before, in any version:
     * their record and the nodes they added. Both happen together, so a
     * failure leaves what was there. Relationships an enhancer added
     * between nodes of the scan result itself are kept.
     *
     * @throws IllegalArgumentException when a label or relationship type is
     *                                  not a valid name
     */
    void replace(Scan.ScanId scanId, List<Enhancements> enhancements);

}
