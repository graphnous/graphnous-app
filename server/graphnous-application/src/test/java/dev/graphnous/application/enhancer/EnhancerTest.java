package dev.graphnous.application.enhancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.application.enhancer.model.Selector;
import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnhancerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<String> calls = new ArrayList<>();

    private final Enhancer enhancer = new Enhancer(TestManifests.recording(calls));

    @Test
    void readsAManifestIntoTheGeneratedModel() throws IOException {
        final var manifest = manifest("""
            {
              "$schema": "https://graphnous.dev/schema/enhancer/enhancer-manifest.schema.json",
              "schemaVersion": "1.0",
              "id": "io.acme.spring-endpoints",
              "name": "Spring endpoints",
              "version": "1.0.0",
              "namespace": "Acme",
              "compatibility": { "scanResult": "^2.0.0", "languages": ["java"] },
              "rules": [
                {
                  "id": "rest-controllers",
                  "match": {
                    "kind": "Class",
                    "where": { "property": "name", "endsWith": "Controller" }
                  },
                  "actions": {
                    "addLabels": ["RestController"],
                    "setProperties": { "endpoint": { "template": "${node.name}Endpoint" } }
                  }
                }
              ]
            }
            """);

        assertThat(manifest.getNamespace()).isEqualTo("Acme");
        assertThat(manifest.getCompatibility().getLanguages()).containsExactly("java");

        final var rule = manifest.getRules().getFirst();
        assertThat(rule.getMatch().getKind()).isEqualTo(Selector.NodeKind.CLASS);
        // Conditions combine several shapes, so the model keeps them as JSON
        assertThat(rule.getMatch().getWhere()).isEqualTo(Map.of("property", "name", "endsWith", "Controller"));
        assertThat(rule.getActions().getAddLabels()).containsExactly("RestController");
        assertThat(rule.getActions().getSetProperties().getAdditionalProperties())
            .containsEntry("endpoint", Map.of("template", "${node.name}Endpoint"));
    }

    @Test
    void refusesToReadARuleForAKindOutsideTheScan() {
        // Dependencies are shared between scans, so enhancers cannot match them
        assertThatThrownBy(() -> manifest("""
            {
              "schemaVersion": "1.0", "id": "io.acme.libraries", "name": "Libraries", "version": "1.0.0", "namespace": "Acme",
              "rules": [
                { "id": "libraries", "match": { "kind": "Dependency" }, "actions": { "addLabels": ["Library"] } }
              ]
            }
            """))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Dependency");
    }

    @Test
    void runsTheRulesOfAnEnhancerForTheScannedLanguage() throws IOException {
        final var enhancement = enhancer.enhance(TestManifests.SCAN, scanResult(ScanTarget.Language.JAVA), manifest("""
            {
              "schemaVersion": "1.0", "id": "io.acme.spring", "name": "Spring", "version": "1.0.0", "namespace": "Acme",
              "compatibility": { "languages": ["java"] },
              "rules": [
                { "id": "controllers", "match": { "kind": "Class" }, "actions": { "addLabels": ["Controller"] } },
                { "id": "services", "match": { "kind": "Class" }, "actions": { "addLabels": ["Service"] } }
              ]
            }
            """));

        assertThat(enhancement.enhancerId()).isEqualTo("io.acme.spring");
        assertThat(enhancement.applied()).isTrue();
        assertThat(enhancement.ruleIds()).containsExactly("controllers", "services");
        // Limited to the scanned target
        assertThat(calls).containsExactly(".:controllers", ".:services");
    }

    @Test
    void skipsAnEnhancerForAnotherLanguage() throws IOException {
        final var enhancement = enhancer.enhance(TestManifests.SCAN, scanResult(ScanTarget.Language.TYPESCRIPT), manifest("""
            {
              "schemaVersion": "1.0", "id": "io.acme.spring", "name": "Spring", "version": "1.0.0", "namespace": "Acme",
              "compatibility": { "languages": ["java", "kotlin"] },
              "rules": [
                { "id": "controllers", "match": { "kind": "Class" }, "actions": { "addLabels": ["Controller"] } }
              ]
            }
            """));

        assertThat(enhancement.applied()).isFalse();
        assertThat(enhancement.rules()).isEmpty();
    }

    @Test
    void appliesAnEnhancerWithoutLanguagesToEveryLanguage() throws IOException {
        final var manifest = manifest("""
            {
              "schemaVersion": "1.0", "id": "io.acme.naming", "name": "Naming", "version": "1.0.0", "namespace": "Acme",
              "rules": [
                { "id": "tests", "match": { "kind": "File" }, "actions": { "addLabels": ["Test"] } }
              ]
            }
            """);

        assertThat(enhancer.enhance(TestManifests.SCAN, scanResult(ScanTarget.Language.JAVA), manifest).applied()).isTrue();
        assertThat(enhancer.enhance(TestManifests.SCAN, scanResult(ScanTarget.Language.TYPESCRIPT), manifest).applied()).isTrue();
    }

    @Test
    void leavesScanScopedEnhancersToTheWholeScan() throws IOException {
        final var enhancement = enhancer.enhance(TestManifests.SCAN, scanResult(ScanTarget.Language.JAVA), manifest("""
            {
              "schemaVersion": "1.0", "id": "io.acme.http-links", "name": "HTTP links", "version": "1.0.0",
              "namespace": "Acme", "scope": "scan",
              "rules": [
                { "id": "calls", "match": { "kind": "Method", "language": "typescript" }, "actions": { "addLabels": ["Call"] } }
              ]
            }
            """));

        assertThat(enhancement.applied()).isFalse();
        assertThat(enhancement.rules()).isEmpty();
    }

    @Test
    void treatsAnEnhancerWithoutScopeAsTargetScoped() throws IOException {
        final var manifest = manifest("""
            {
              "schemaVersion": "1.0", "id": "io.acme.naming", "name": "Naming", "version": "1.0.0", "namespace": "Acme",
              "rules": [
                { "id": "tests", "match": { "kind": "File", "language": "java" }, "actions": { "addLabels": ["Test"] } }
              ]
            }
            """);

        assertThat(manifest.getScope()).isEqualTo(EnhancerManifestSchema.Scope.TARGET);
        assertThat(manifest.getRules().getFirst().getMatch().getLanguage()).isEqualTo("java");
        assertThat(enhancer.enhance(TestManifests.SCAN, scanResult(ScanTarget.Language.JAVA), manifest).applied()).isTrue();
    }

    @Test
    void leavesOutDisabledRules() throws IOException {
        final var enhancement = enhancer.enhance(TestManifests.SCAN, scanResult(ScanTarget.Language.JAVA), manifest("""
            {
              "schemaVersion": "1.0", "id": "io.acme.spring", "name": "Spring", "version": "1.0.0", "namespace": "Acme",
              "rules": [
                { "id": "on", "match": { "kind": "Class" }, "actions": { "addLabels": ["On"] } },
                { "id": "off", "enabled": false, "match": { "kind": "Class" }, "actions": { "addLabels": ["Off"] } },
                { "id": "explicitly-on", "enabled": true, "match": { "kind": "Class" }, "actions": { "addLabels": ["On"] } }
              ]
            }
            """));

        assertThat(enhancement.ruleIds()).containsExactly("on", "explicitly-on");
    }

    @Test
    void refusesAManifestVersionItDoesNotKnow() throws IOException {
        final var manifest = manifest("""
            {
              "schemaVersion": "2.0", "id": "io.acme.future", "name": "Future", "version": "1.0.0", "namespace": "Acme",
              "rules": [
                { "id": "rule", "match": { "kind": "Class" }, "actions": { "addLabels": ["Future"] } }
              ]
            }
            """);

        assertThatThrownBy(() -> enhancer.enhance(TestManifests.SCAN, scanResult(ScanTarget.Language.JAVA), manifest))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("io.acme.future")
            .hasMessageContaining("2.0");
    }

    private EnhancerManifestSchema manifest(final String json) throws IOException {
        return objectMapper.readValue(json, EnhancerManifestSchema.class);
    }

    private static ScanResultSchema scanResult(final ScanTarget.Language language) {
        final var target = new ScanTarget();
        target.setPath(".");
        target.setLanguage(language);

        final var result = new ScanResultSchema();
        result.setTarget(target);

        return result;
    }
}
