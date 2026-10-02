package dev.graphnous.application.enhancer;

import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.application.enhancer.model.Rule;
import dev.graphnous.domain.scan.Scan;

/**
 * Applies enhancer rules to the graph of a scan's results.
 */
public interface EnhancementRepository {

    /**
     * Matches the rule's nodes in the scan and applies its actions to them,
     * with everything it produces named in the manifest's namespace.
     *
     * @param targetPath the path of the scanned target a target-scoped rule
     *                   is limited to; null for a scan-scoped rule, which
     *                   sees every target of the scan
     * @throws IllegalArgumentException when the rule cannot be applied, such
     *                                  as for an unknown node kind or an
     *                                  invalid name
     */
    RuleOutcome apply(
        Scan.ScanId scanId,
        String targetPath,
        EnhancerManifestSchema manifest,
        Rule rule
    );

}
