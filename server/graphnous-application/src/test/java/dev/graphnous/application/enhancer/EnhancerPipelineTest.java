package dev.graphnous.application.enhancer;

import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.graphnous.application.enhancer.TestManifests.manifest;
import static dev.graphnous.application.enhancer.TestManifests.scanResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class EnhancerPipelineTest {

    private final List<String> calls = new ArrayList<>();

    private final EnhancerPipeline pipeline = new EnhancerPipeline(
        new Enhancer(TestManifests.recording(calls)),
        new ScanEnhancer(TestManifests.recording(calls))
    );

    @Test
    void runsTargetScopedEnhancersOnEachResultThenScanScopedOnes() {
        final var backend = scanResult(ScanTarget.Language.JAVA, "backend");
        final var frontend = scanResult(ScanTarget.Language.TYPESCRIPT, "frontend");

        final var manifests = List.of(
            manifest("io.acme.http-links", "scan", List.of(), List.of("io.acme.spring", "io.acme.fetch")),
            manifest("io.acme.spring", "target", List.of("java"), List.of()),
            manifest("io.acme.fetch", "target", List.of("typescript"), List.of())
        );

        final var targets = pipeline.enhanceTargets(TestManifests.SCAN, List.of(backend, frontend), manifests);
        final var scan = pipeline.enhanceScan(TestManifests.SCAN, List.of(backend, frontend), manifests);

        assertThat(targets).hasSize(2);

        final var backendRun = targets.get(0);
        assertThat(backendRun.target().getPath()).isEqualTo("backend");
        assertThat(backendRun.enhancements())
            .extracting(Enhancement::enhancerId, Enhancement::applied)
            .containsExactly(
                tuple("io.acme.spring", true),
                tuple("io.acme.fetch", false)
            );

        final var frontendRun = targets.get(1);
        assertThat(frontendRun.enhancements())
            .extracting(Enhancement::enhancerId, Enhancement::applied)
            .containsExactly(
                tuple("io.acme.spring", false),
                tuple("io.acme.fetch", true)
            );

        assertThat(scan)
            .extracting(Enhancement::enhancerId, Enhancement::applied)
            .containsExactly(tuple("io.acme.http-links", true));

        // Each target on its own first, then the whole scan
        assertThat(calls).containsExactly("backend:rule", "frontend:rule", "scan:rule");
    }

    @Test
    void runsAnEnhancerAfterWhatItDependsOn() {
        final var ordered = EnhancerPipeline.order(List.of(
            manifest("io.acme.c", "target", List.of(), List.of("io.acme.b")),
            manifest("io.acme.a", "target", List.of(), List.of()),
            manifest("io.acme.b", "target", List.of(), List.of("io.acme.a")),
            manifest("io.acme.d", "target", List.of(), List.of())
        ));

        assertThat(ordered)
            .extracting(EnhancerManifestSchema::getId)
            .containsExactly("io.acme.a", "io.acme.b", "io.acme.c", "io.acme.d");
    }

    @Test
    void keepsTheInstalledOrderWithoutDependencies() {
        final var ordered = EnhancerPipeline.order(List.of(
            manifest("io.acme.z", "target", List.of(), List.of()),
            manifest("io.acme.y", "scan", List.of(), List.of()),
            manifest("io.acme.x", "target", List.of(), List.of())
        ));

        assertThat(ordered)
            .extracting(EnhancerManifestSchema::getId)
            .containsExactly("io.acme.z", "io.acme.y", "io.acme.x");
    }

    @Test
    void refusesADependencyThatIsNotInstalled() {
        assertThatThrownBy(() -> EnhancerPipeline.order(List.of(
            manifest("io.acme.a", "target", List.of(), List.of("io.acme.missing"))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Enhancer io.acme.a depends on io.acme.missing, which is not installed");
    }

    @Test
    void refusesATargetScopedEnhancerDependingOnAScanScopedOne() {
        assertThatThrownBy(() -> EnhancerPipeline.order(List.of(
            manifest("io.acme.links", "scan", List.of(), List.of()),
            manifest("io.acme.a", "target", List.of(), List.of("io.acme.links"))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Target-scoped enhancer io.acme.a cannot depend on scan-scoped enhancer io.acme.links");
    }

    @Test
    void refusesADependencyCycle() {
        assertThatThrownBy(() -> EnhancerPipeline.order(List.of(
            manifest("io.acme.free", "target", List.of(), List.of()),
            manifest("io.acme.a", "target", List.of(), List.of("io.acme.b")),
            manifest("io.acme.b", "target", List.of(), List.of("io.acme.a"))
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cycle")
            .hasMessageContaining("io.acme.a")
            .hasMessageContaining("io.acme.b")
            .hasMessageNotContaining("io.acme.free");
    }

    @Test
    void refusesAnEnhancerInstalledTwice() {
        assertThatThrownBy(() -> EnhancerPipeline.order(List.of(
            manifest("io.acme.a", "target", List.of(), List.of()),
            manifest("io.acme.a", "scan", List.of(), List.of())
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Enhancer io.acme.a is installed twice");
    }

    @Test
    void enhancesAScanWithoutEnhancers() {
        final var results = List.of(scanResult(ScanTarget.Language.JAVA, "backend"));

        assertThat(pipeline.enhanceTargets(TestManifests.SCAN, results, List.of())).singleElement()
            .satisfies(target -> assertThat(target.enhancements()).isEmpty());
        assertThat(pipeline.enhanceScan(TestManifests.SCAN, results, List.of())).isEmpty();
    }
}
