package dev.graphnous.application.enhancer;

import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.model.ScanResultSchema;

import java.util.List;

/**
 * Applies a scan-scoped enhancer manifest to all results of a scan together,
 * after every target-scoped enhancer ran on them. Its rules see every
 * target, so they can connect nodes across targets and languages.
 */
public class ScanEnhancer {

    private final EnhancementRepository enhancementRepository;

    public ScanEnhancer(final EnhancementRepository enhancementRepository) {
        this.enhancementRepository = enhancementRepository;
    }

    public Enhancement enhance(
        final Scan.ScanId scanId,
        final List<ScanResultSchema> scanResults,
        final EnhancerManifestSchema manifest
    ) {
        Manifests.requireSupportedVersion(manifest);

        if (manifest.getScope() != EnhancerManifestSchema.Scope.SCAN || !appliesTo(scanResults, manifest)) {
            return Enhancement.notApplied(manifest.getId());
        }

        final var outcomes = Manifests.enabledRules(manifest)
            .stream()
            .map(rule -> enhancementRepository.apply(scanId, null, manifest, rule))
            .toList();

        return new Enhancement(manifest.getId(), true, outcomes);
    }

    /**
     * Whether the scan has a target in one of the manifest's languages.
     */
    private static boolean appliesTo(
        final List<ScanResultSchema> scanResults,
        final EnhancerManifestSchema manifest
    ) {
        return scanResults
            .stream()
            .anyMatch(scanResult -> Manifests.isForLanguageOf(manifest, scanResult));
    }
}
