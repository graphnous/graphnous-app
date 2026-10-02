package dev.graphnous.scanner.typescript;

import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TypescriptScannerDefinitionTest {

    private final Path script = Path.of("scanner", "scanner.js");

    private final TypescriptScannerDefinition definition = new TypescriptScannerDefinition(script);

    @Test
    void supportsTypescriptTargetsOnly() {
        assertThat(definition.supports(target(ScanTarget.Language.TYPESCRIPT))).isTrue();
        assertThat(definition.supports(target(ScanTarget.Language.JAVA))).isFalse();
    }

    @Test
    void runsTheScannerScriptWithNode() {
        final var target = target(ScanTarget.Language.TYPESCRIPT);
        target.setPath("frontend");

        assertThat(definition.command(Path.of("repo"), target)).containsExactly(
            "node",
            script.toString(),
            "--path",
            "repo",
            "--target",
            "frontend"
        );
    }

    @Test
    void commandCanBeExtended() {
        final var target = target(ScanTarget.Language.TYPESCRIPT);
        target.setPath(".");

        final var command = definition.command(Path.of("repo"), target);
        command.add("--output");

        assertThat(command).endsWith("--output");
    }

    @Test
    void exposesTheScannerScript() {
        assertThat(definition.scanner()).isEqualTo(script);
    }

    @Test
    void runsTheMountedScriptWithTheContainerNode() {
        final var target = target(ScanTarget.Language.TYPESCRIPT);
        target.setPath("frontend");

        assertThat(definition.containerCommand("/scanner/scanner.js", "/workspace", target))
            .containsExactly(
                "node",
                "/scanner/scanner.js",
                "--path",
                "/workspace",
                "--target",
                "frontend"
            );
    }

    @Test
    void usesNodeImageForTheLanguageVersion() {
        final var target = target(ScanTarget.Language.TYPESCRIPT);
        target.setLanguageVersion("24");

        assertThat(definition.image(target)).isEqualTo("node:24");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void fallsBackToTheDefaultNodeVersionWhenUnknown(final String version) {
        final var target = target(ScanTarget.Language.TYPESCRIPT);
        target.setLanguageVersion(version);

        assertThat(definition.image(target)).isEqualTo("node:24");
    }

    @Test
    void usesTheConfiguredFallbackNodeVersion() {
        final var target = target(ScanTarget.Language.TYPESCRIPT);

        assertThat(new TypescriptScannerDefinition(script, "22").image(target)).isEqualTo("node:22");
    }

    private static ScanTarget target(final ScanTarget.Language language) {
        final var target = new ScanTarget();
        target.setLanguage(language);

        return target;
    }
}
