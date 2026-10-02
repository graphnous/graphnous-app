package dev.graphnous.application.enhancer;

import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.graphnous.application.enhancer.TestManifests.manifest;
import static dev.graphnous.application.enhancer.TestManifests.scanResult;
import static org.assertj.core.api.Assertions.assertThat;

class ScanEnhancerTest {

    private final List<String> calls = new ArrayList<>();

    private final ScanEnhancer scanEnhancer = new ScanEnhancer(TestManifests.recording(calls));

    private final List<ScanResultSchema> javaAndTypescript = List.of(
        scanResult(ScanTarget.Language.JAVA, "backend"),
        scanResult(ScanTarget.Language.TYPESCRIPT, "frontend")
    );

    @Test
    void runsWhenTheScanHasATargetInOneOfItsLanguages() {
        final var enhancement = scanEnhancer.enhance(TestManifests.SCAN, 
            javaAndTypescript,
            manifest("io.acme.http-links", "scan", List.of("java", "kotlin"), List.of())
        );

        assertThat(enhancement.applied()).isTrue();
        assertThat(enhancement.ruleIds()).containsExactly("rule");
        // Over the whole scan, not one target
        assertThat(calls).containsExactly("scan:rule");
    }

    @Test
    void skipsAScanWithoutItsLanguages() {
        final var enhancement = scanEnhancer.enhance(TestManifests.SCAN, 
            List.of(scanResult(ScanTarget.Language.TYPESCRIPT, "frontend")),
            manifest("io.acme.spring-links", "scan", List.of("java"), List.of())
        );

        assertThat(enhancement.applied()).isFalse();
    }

    @Test
    void runsOnEveryScanWhenItNamesNoLanguages() {
        assertThat(scanEnhancer.enhance(TestManifests.SCAN, javaAndTypescript, manifest("io.acme.all", "scan", List.of(), List.of())).applied())
            .isTrue();
    }

    @Test
    void hasNothingToDoForAScanWithoutResults() {
        assertThat(scanEnhancer.enhance(TestManifests.SCAN, List.of(), manifest("io.acme.all", "scan", List.of(), List.of())).applied())
            .isFalse();
    }

    @Test
    void leavesTargetScopedEnhancersToEachResult() {
        assertThat(scanEnhancer.enhance(TestManifests.SCAN, javaAndTypescript, manifest("io.acme.spring", "target", List.of(), List.of())).applied())
            .isFalse();
    }
}
