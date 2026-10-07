package dev.graphnous.api.project.scanner.docker;

import dev.graphnous.core.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

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
            "--output", "/output/scan-result.json"
        );
        assertThat(java.output()).isEqualTo("/output/scan-result.json");
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
