package dev.graphnous.application.enhancer;

import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.application.enhancer.model.EnhancerManifestSchema.Scope;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.model.ScanResultSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the installed enhancers on the results of a scan: first every
 * target-scoped enhancer on each result on its own, then every scan-scoped
 * enhancer on all results together. Within each group, an enhancer runs
 * after the enhancers it depends on, and otherwise in installed order.
 */
public class EnhancerPipeline {

    private final Enhancer enhancer;
    private final ScanEnhancer scanEnhancer;

    public EnhancerPipeline(
        final Enhancer enhancer,
        final ScanEnhancer scanEnhancer
    ) {
        this.enhancer = enhancer;
        this.scanEnhancer = scanEnhancer;
    }

    /**
     * Runs the target-scoped enhancers on each scan result on its own.
     *
     * @return per scan result, in scan order, the enhancers that ran on it
     * @throws IllegalArgumentException when the manifests cannot be ordered:
     *                                  duplicate ids, a dependency that is
     *                                  not installed, a target-scoped
     *                                  enhancer depending on a scan-scoped
     *                                  one, or a dependency cycle
     */
    public List<TargetEnhancements> enhanceTargets(
        final Scan.ScanId scanId,
        final List<ScanResultSchema> scanResults,
        final List<EnhancerManifestSchema> manifests
    ) {
        final var targetScoped = ordered(manifests, Scope.TARGET);

        return scanResults.stream()
            .map(scanResult -> new TargetEnhancements(
                scanResult.getTarget(),
                targetScoped.stream()
                    .map(manifest -> enhancer.enhance(scanId, scanResult, manifest))
                    .toList()
            ))
            .toList();
    }

    /**
     * Runs the scan-scoped enhancers on all results together; for after
     * {@link #enhanceTargets}.
     *
     * @return the enhancers, in the order they ran
     * @throws IllegalArgumentException as {@link #enhanceTargets} does
     */
    public List<Enhancement> enhanceScan(
        final Scan.ScanId scanId,
        final List<ScanResultSchema> scanResults,
        final List<EnhancerManifestSchema> manifests
    ) {
        return ordered(manifests, Scope.SCAN)
            .stream()
            .map(manifest -> scanEnhancer.enhance(scanId, scanResults, manifest))
            .toList();
    }

    private static List<EnhancerManifestSchema> ordered(
        final List<EnhancerManifestSchema> manifests,
        final Scope scope
    ) {
        return order(manifests)
            .stream()
            .filter(manifest -> manifest.getScope() == scope)
            .toList();
    }

    /**
     * Orders the manifests so each comes after its dependencies, keeping the
     * installed order where the dependencies allow it.
     *
     * @throws IllegalArgumentException as {@link #enhanceTargets} does, and for a
     *                                  manifest version this engine does not
     *                                  support
     */
    public static List<EnhancerManifestSchema> order(final List<EnhancerManifestSchema> manifests) {
        final Map<String, EnhancerManifestSchema> byId = new LinkedHashMap<>();

        for (final var manifest : manifests) {
            Manifests.requireSupportedVersion(manifest);

            if (byId.putIfAbsent(manifest.getId(), manifest) != null) {
                throw new IllegalArgumentException("Enhancer %s is installed twice".formatted(manifest.getId()));
            }
        }

        for (final var manifest : manifests) {
            for (final var dependencyId : manifest.getDependsOn()) {
                final var dependency = byId.get(dependencyId);

                if (dependency == null) {
                    throw new IllegalArgumentException(
                        "Enhancer %s depends on %s, which is not installed".formatted(manifest.getId(), dependencyId)
                    );
                }

                // Target-scoped enhancers all run before any scan-scoped one
                if (manifest.getScope() == Scope.TARGET && dependency.getScope() == Scope.SCAN) {
                    throw new IllegalArgumentException(
                        "Target-scoped enhancer %s cannot depend on scan-scoped enhancer %s"
                            .formatted(manifest.getId(), dependencyId)
                    );
                }
            }
        }

        final List<EnhancerManifestSchema> ordered = new ArrayList<>();
        final List<EnhancerManifestSchema> remaining = new ArrayList<>(manifests);

        while (!remaining.isEmpty()) {
            final var next = remaining.stream()
                .filter(manifest -> manifest.getDependsOn().stream()
                    .allMatch(dependencyId -> ordered.stream().anyMatch(done -> done.getId().equals(dependencyId))))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "Enhancers depend on each other in a cycle: %s".formatted(
                        remaining.stream().map(EnhancerManifestSchema::getId).toList()
                    )
                ));

            ordered.add(next);
            remaining.remove(next);
        }

        return ordered;
    }
}
