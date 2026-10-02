package dev.graphnous.scanner.typescript.language;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NodeVersionDetectorTest {

    private final NodeVersionDetector detector = new NodeVersionDetector(new ObjectMapper(), "24");

    @TempDir
    Path repository;

    @Test
    void supportsTypescriptTargetsOnly() {
        assertThat(detector.supports(target(ScanTarget.Language.TYPESCRIPT))).isTrue();
        assertThat(detector.supports(target(ScanTarget.Language.JAVA))).isFalse();
    }

    @Test
    void prefersNvmrc() throws IOException {
        write(".nvmrc", "v22.3.0\n");
        write(".node-version", "20");
        write("package.json", """
            { "engines": { "node": "18" } }
            """);

        assertThat(detect()).isEqualTo("22.3.0");
    }

    @Test
    void fallsBackToNodeVersionFile() throws IOException {
        write(".node-version", "20.11.1");
        write("package.json", """
            { "engines": { "node": "18" } }
            """);

        assertThat(detect()).isEqualTo("20.11.1");
    }

    @Test
    void fallsBackToEnginesInPackageJson() throws IOException {
        write("package.json", """
            { "engines": { "node": "v18" } }
            """);

        assertThat(detect()).isEqualTo("18");
    }

    @Test
    void usesFallbackVersionWithoutEngines() throws IOException {
        write("package.json", """
            { "name": "web" }
            """);

        assertThat(detect()).isEqualTo("24");
    }

    @Test
    void usesFallbackVersionWithoutNodeEngine() throws IOException {
        write("package.json", """
            { "engines": { "npm": "10" } }
            """);

        assertThat(detect()).isEqualTo("24");
    }

    @Test
    void usesFallbackVersionWithoutAnyVersionFile() {
        assertThat(detect()).isEqualTo("24");
    }

    @Test
    void resolvesFilesRelativeToTheTarget() throws IOException {
        Files.createDirectories(repository.resolve("apps/web"));
        Files.writeString(repository.resolve("apps/web/.nvmrc"), "22");
        write(".nvmrc", "18");

        final var target = target(ScanTarget.Language.TYPESCRIPT);
        target.setPath("apps/web");

        assertThat(detector.detect(repository, target)).isEqualTo("22");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = ':', value = {
        "20.11.1 : 20.11.1",
        "v20.11.1 : 20.11.1",
        "=20.11.1 : 20.11.1",
        "20.11 : 20.11",
        "20 : 20",
        "18.x : 18",
        "18.* : 18",
        "20.11.x : 20.11",
        "^20.11.0 : 20",
        "^v20 : 20",
        "~20.11.0 : 20.11",
        "~20 : 20",
        ">=18.0.0 : 18",
        ">18 : 18",
        ">=18 <21 : 18",
        "<21 >=18 : 18",
        "18 - 20 : 18",
        "^18 || ^20 : 18",
        ">=18.17.0 || >=20 : 18"
    })
    void resolvesEngineRangesToAVersion(final String range, final String version) throws IOException {
        write("package.json", """
            { "engines": { "node": "%s" } }
            """.formatted(range));

        assertThat(detect()).isEqualTo(version);
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "<21", "<=20.0.0", "latest", "lts/*", "lts/iron", ""})
    void usesFallbackForRangesWithoutLowerBound(final String range) throws IOException {
        write("package.json", """
            { "engines": { "node": "%s" } }
            """.formatted(range));

        assertThat(detect()).isEqualTo("24");
    }

    @Test
    void continuesToNextSourceWhenNvmrcHasNoVersion() throws IOException {
        write(".nvmrc", "lts/*");
        write(".node-version", "20");

        assertThat(detect()).isEqualTo("20");
    }

    @Test
    void resolvesRangesInVersionFiles() throws IOException {
        write(".nvmrc", "^22");

        assertThat(detect()).isEqualTo("22");
    }

    private String detect() {
        final var target = target(ScanTarget.Language.TYPESCRIPT);
        target.setPath(".");

        return detector.detect(repository, target);
    }

    private void write(final String file, final String content) throws IOException {
        Files.writeString(repository.resolve(file), content);
    }

    private static ScanTarget target(final ScanTarget.Language language) {
        final var target = new ScanTarget();
        target.setLanguage(language);

        return target;
    }
}
