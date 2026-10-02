package dev.graphnous.application.enhancer;

import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.model.ScanResultSchema;

/**
 * Applies a target-scoped enhancer manifest to the result of scanning one
 * target: its rules only see the nodes of that target. Scan-scoped
 * enhancers run later, over all results of a scan together (see
 * {@link ScanEnhancer}), so they do not apply here.
 * <p>
 * Enhancers only add to a scan's graph, never change or remove what the
 * scanner found.
 */
public class Enhancer {

    private final EnhancementRepository enhancementRepository;

    public Enhancer(final EnhancementRepository enhancementRepository) {
        this.enhancementRepository = enhancementRepository;
    }

    public Enhancement enhance(
        final Scan.ScanId scanId,
        final ScanResultSchema scanResult,
        final EnhancerManifestSchema manifest
    ) {
        Manifests.requireSupportedVersion(manifest);

        if (manifest.getScope() != EnhancerManifestSchema.Scope.TARGET
            || !Manifests.isForLanguageOf(manifest, scanResult)) {
            return Enhancement.notApplied(manifest.getId());
        }

        final var targetPath = scanResult.getTarget().getPath();

        final var outcomes = Manifests.enabledRules(manifest)
            .stream()
            .map(rule -> enhancementRepository.apply(scanId, targetPath, manifest, rule))
            .toList();

        return new Enhancement(manifest.getId(), true, outcomes);
    }
}
