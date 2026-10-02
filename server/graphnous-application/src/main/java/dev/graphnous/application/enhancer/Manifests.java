package dev.graphnous.application.enhancer;

import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.application.enhancer.model.Rule;
import dev.graphnous.scanner.model.ScanResultSchema;

import java.util.List;
import java.util.Objects;

/**
 * What target- and scan-scoped enhancers read from a manifest the same way.
 */
final class Manifests {

    /**
     * The manifest format this engine understands.
     */
    static final String SCHEMA_VERSION = "1.0";

    private Manifests() {
    }

    static void requireSupportedVersion(final EnhancerManifestSchema manifest) {
        if (!SCHEMA_VERSION.equals(manifest.getSchemaVersion())) {
            throw new IllegalArgumentException(
                "Enhancer %s has manifest version %s; only %s is supported"
                    .formatted(manifest.getId(), manifest.getSchemaVersion(), SCHEMA_VERSION)
            );
        }
    }

    /**
     * The rules that run, in manifest order: rules are enabled unless they
     * say otherwise.
     */
    static List<Rule> enabledRules(final EnhancerManifestSchema manifest) {
        return manifest.getRules()
            .stream()
            .filter(rule -> !Objects.equals(rule.getEnabled(), Boolean.FALSE))
            .toList();
    }

    /**
     * Whether the manifest is for the scan result's language. A manifest that
     * names no languages is for all. Manifests name languages in any case
     * ("java"), scan results in upper case ("JAVA").
     */
    static boolean isForLanguageOf(
        final EnhancerManifestSchema manifest,
        final ScanResultSchema scanResult
    ) {
        final var compatibility = manifest.getCompatibility();

        if (compatibility == null || compatibility.getLanguages().isEmpty()) {
            return true;
        }

        final var target = scanResult.getTarget();

        if (target == null || target.getLanguage() == null) {
            return false;
        }

        final var language = target.getLanguage().value();

        return compatibility.getLanguages()
            .stream()
            .anyMatch(language::equalsIgnoreCase);
    }
}
