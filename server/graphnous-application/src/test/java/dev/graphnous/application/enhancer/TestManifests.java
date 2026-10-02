package dev.graphnous.application.enhancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds manifests and scan results for the enhancer tests.
 */
final class TestManifests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static final Scan.ScanId SCAN = Scan.ScanId.generate();

    private TestManifests() {
    }

    /**
     * Stands in for the graph: every rule matches one node, and each call is
     * recorded as "<target path or 'scan'>:<rule id>".
     */
    static EnhancementRepository recording(final List<String> calls) {
        return (scanId, targetPath, manifest, rule) -> {
            calls.add((targetPath == null ? "scan" : targetPath) + ":" + rule.getId());

            return new RuleOutcome(rule.getId(), 1, 0, 0, 0, 0);
        };
    }

    /**
     * A manifest with one rule, named after the enhancer.
     */
    static EnhancerManifestSchema manifest(
        final String id,
        final String scope,
        final List<String> languages,
        final List<String> dependsOn
    ) {
        return read("""
            {
              "schemaVersion": "1.0", "id": "%s", "name": "%s", "version": "1.0.0", "namespace": "Acme",
              "scope": "%s",
              "compatibility": { "languages": [%s] },
              "dependsOn": [%s],
              "rules": [
                { "id": "rule", "match": { "kind": "Class" }, "actions": { "addLabels": ["Enhanced"] } }
              ]
            }
            """.formatted(id, id, scope, quoted(languages), quoted(dependsOn)));
    }

    static EnhancerManifestSchema read(final String json) {
        try {
            return OBJECT_MAPPER.readValue(json, EnhancerManifestSchema.class);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ScanResultSchema scanResult(final ScanTarget.Language language, final String path) {
        final var target = new ScanTarget();
        target.setPath(path);
        target.setLanguage(language);

        final var result = new ScanResultSchema();
        result.setTarget(target);

        return result;
    }

    private static String quoted(final List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(", "));
    }
}
