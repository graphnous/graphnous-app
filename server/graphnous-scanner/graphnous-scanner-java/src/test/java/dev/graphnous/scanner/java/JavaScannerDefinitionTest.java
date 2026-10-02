package dev.graphnous.scanner.java;

import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JavaScannerDefinitionTest {

    private final Path jar = Path.of("scanner", "java-scanner.jar");

    private final JavaScannerDefinition definition = new JavaScannerDefinition(jar);

    @Test
    void supportsJavaTargetsOnly() {
        assertThat(definition.supports(target(ScanTarget.Language.JAVA))).isTrue();
        assertThat(definition.supports(target(ScanTarget.Language.TYPESCRIPT))).isFalse();
    }

    @Test
    void runsTheScannerJarWithTheCurrentJava() {
        final var target = target(ScanTarget.Language.JAVA);
        target.setPath("backend");

        final var java = Path.of(System.getProperty("java.home"), "bin", "java");

        assertThat(definition.command(Path.of("repo"), target)).containsExactly(
            java.toString(),
            "-jar",
            jar.toString(),
            "--path",
            "repo",
            "--target",
            "backend"
        );
    }

    @Test
    void commandCanBeExtended() {
        final var target = target(ScanTarget.Language.JAVA);
        target.setPath(".");

        final var command = definition.command(Path.of("repo"), target);
        command.add("--output");

        assertThat(command).endsWith("--output");
    }

    @Test
    void passesTheJavaVersionOfTheTarget() {
        final var target = target(ScanTarget.Language.JAVA);
        target.setPath("backend");
        target.setLanguageVersion("17");

        assertThat(definition.command(Path.of("repo"), target))
            .endsWith("--target", "backend", "--java-version", "17");

        assertThat(definition.containerCommand("/scanner/java-scanner.jar", "/workspace", target))
            .endsWith("--target", "backend", "--java-version", "17");
    }

    @Test
    void passesVerboseWhenEnabled() {
        final var target = target(ScanTarget.Language.JAVA);
        target.setPath(".");

        assertThat(new JavaScannerDefinition(jar, true).command(Path.of("repo"), target))
            .endsWith("--verbose");
        assertThat(definition.command(Path.of("repo"), target))
            .doesNotContain("--verbose");
    }

    @Test
    void omitsTheJavaVersionWhenUnknown() {
        final var target = target(ScanTarget.Language.JAVA);
        target.setPath("backend");

        assertThat(definition.command(Path.of("repo"), target))
            .doesNotContain("--java-version");
    }

    @Test
    void exposesTheScannerJar() {
        assertThat(definition.scanner()).isEqualTo(jar);
    }

    @Test
    void runsTheMountedJarWithTheContainerJava() {
        final var target = target(ScanTarget.Language.JAVA);
        target.setPath("backend");

        assertThat(definition.containerCommand("/scanner/java-scanner.jar", "/workspace", target))
            .containsExactly(
                "java",
                "-jar",
                "/scanner/java-scanner.jar",
                "--path",
                "/workspace",
                "--target",
                "backend"
            );
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "17", "21", "25"})
    void usesTheScannerRuntimeImageWhateverTheProjectVersion(final String version) {
        final var target = target(ScanTarget.Language.JAVA);
        target.setLanguageVersion(version);

        assertThat(definition.image(target)).isEqualTo("eclipse-temurin:25-jre");
    }

    private static ScanTarget target(final ScanTarget.Language language) {
        final var target = new ScanTarget();
        target.setLanguage(language);

        return target;
    }
}
