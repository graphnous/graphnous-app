package dev.graphnous.api.project.scanner.docker;

import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ScannerMount;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScannersPropertiesTest {

    @Test
    void configuresAScannerImageForEachLanguageInApplicationYml() throws IOException {
        final var scanners = bind();

        assertThat(scanners.scanners()).containsOnlyKeys(ScanTarget.Language.JAVA, ScanTarget.Language.TYPESCRIPT);
        assertThat(scanners.scanners().get(ScanTarget.Language.JAVA).image())
            .startsWith("ghcr.io/graphnous/graphnous-java-scanner:");
        assertThat(scanners.scanners().get(ScanTarget.Language.TYPESCRIPT).image())
            .startsWith("ghcr.io/graphnous/graphnous-typescript-scanner:");
    }

    @Test
    void runsTheJavaScannerWithTheTargetFromApplicationYml() throws IOException {
        final var java = bind().definitions().stream()
            .filter(definition -> definition.supports(target(ScanTarget.Language.JAVA)))
            .findFirst()
            .orElseThrow();

        assertThat(java.command("/checkouts/org/repo", target(ScanTarget.Language.JAVA))).containsExactly(
            "java", "-jar", "/opt/graphnous/java-scanner.jar",
            "--path", "/checkouts/org/repo",
            "--target", "backend",
            "--java-version", "",
            "--output", "/output/scan-result.json",
            "--download-dependencies"
        );
        assertThat(java.output()).isEqualTo("/output/scan-result.json");
    }

    @Test
    void keepsTheLibrariesOfJavaProjectsInAVolumeOfTheJavaScannerAlone() throws IOException {
        final var definitions = bind().definitions();

        assertThat(definitions)
            .filteredOn(definition -> definition.supports(target(ScanTarget.Language.JAVA)))
            .singleElement()
            .satisfies(java -> assertThat(java.mounts()).containsExactly(
                new ScannerMount(ScannerMount.Type.VOLUME, "graphnous-maven", "/maven-repository", false)
            ));
        assertThat(definitions)
            .filteredOn(definition -> definition.supports(target(ScanTarget.Language.TYPESCRIPT)))
            .singleElement()
            .satisfies(typescript -> {
                assertThat(typescript.mounts()).isEmpty();
                assertThat(typescript.environment()).isEmpty();
            });
    }

    @Test
    void bindsTheMountsAndEnvironmentOfAScanner() {
        final var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
            "graphnous.scanners.JAVA.image", "scanner:1",
            "graphnous.scanners.JAVA.command[0]", "scan",
            "graphnous.scanners.JAVA.output", "/output/scan-result.json",
            "graphnous.scanners.JAVA.mounts[0].type", "bind",
            "graphnous.scanners.JAVA.mounts[0].source", "/etc/graphnous/maven-settings",
            "graphnous.scanners.JAVA.mounts[0].target", "/maven-settings",
            "graphnous.scanners.JAVA.mounts[0].read-only", "true",
            "graphnous.scanners.JAVA.environment.NEXUS_TOKEN", "secret",
            "graphnous.scanners.JAVA.files[0].source", "/etc/graphnous/maven-settings.xml",
            "graphnous.scanners.JAVA.files[0].target", "/maven-settings/settings.xml"
        )));

        final var java = Binder.get(environment).bind("graphnous", ScannersProperties.class).get().definitions().getFirst();

        assertThat(java.mounts()).containsExactly(
            new ScannerMount(ScannerMount.Type.BIND, "/etc/graphnous/maven-settings", "/maven-settings", true)
        );
        assertThat(java.environment()).containsExactly(Map.entry("NEXUS_TOKEN", "secret"));

        assertThat(Binder.get(environment).bind("graphnous", ScannersProperties.class).get()
            .scanners().get(ScanTarget.Language.JAVA).files())
            .containsExactly(new ScannersProperties.File(
                Path.of("/etc/graphnous/maven-settings.xml"),
                "/maven-settings/settings.xml"
            ));
    }

    @Test
    void rejectsAFileWithATargetThatIsNotAFileInADirectory() {
        assertThatThrownBy(() -> new ScannersProperties.Scanner(
            "scanner:1", List.of("scan"), "/output/scan-result.json", null, null,
            List.of(new ScannersProperties.File(Path.of("/settings.xml"), "/settings.xml"))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be an absolute file path in a directory");
    }

    @Test
    void runsTheTypescriptScannerWithTheTargetFromApplicationYml() throws IOException {
        final var typescript = bind().definitions().stream()
            .filter(definition -> definition.supports(target(ScanTarget.Language.TYPESCRIPT)))
            .findFirst()
            .orElseThrow();

        assertThat(typescript.command("/checkouts/org/repo", target(ScanTarget.Language.TYPESCRIPT))).containsExactly(
            "node", "/opt/graphnous/scanner.js",
            "--path", "/checkouts/org/repo",
            "--target", "backend",
            "--output", "/output/scan-result.json"
        );
    }

    @Test
    void hasNoScannersWhenNoneAreConfigured() {
        assertThat(new ScannersProperties(null).definitions()).isEmpty();
    }

    private static ScannersProperties bind() throws IOException {
        final var environment = new StandardEnvironment();

        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .forEach(environment.getPropertySources()::addLast);

        return Binder.get(environment).bind("graphnous", ScannersProperties.class).get();
    }

    private static ScanTarget target(final ScanTarget.Language language) {
        final var target = new ScanTarget();
        target.setPath("backend");
        target.setLanguage(language);

        return target;
    }
}
