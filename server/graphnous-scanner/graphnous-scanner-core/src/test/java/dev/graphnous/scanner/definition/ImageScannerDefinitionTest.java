package dev.graphnous.scanner.definition;

import dev.graphnous.core.model.ScanTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageScannerDefinitionTest {

    private static final String IMAGE = "ghcr.io/graphnous/graphnous-java-scanner:0.1.0";
    private static final String OUTPUT = "/output/scan-result.json";

    @Test
    void replacesThePlaceholdersInTheCommand() {
        final var scanner = scanner(List.of(
            "java", "-jar", "/opt/graphnous/java-scanner.jar",
            "--path", "{repository}",
            "--target", "{target}",
            "--java-version", "{languageVersion}",
            "--output", "{output}"
        ));

        assertThat(scanner.command("/checkouts/org/repo", target("backend", "21"))).containsExactly(
            "java", "-jar", "/opt/graphnous/java-scanner.jar",
            "--path", "/checkouts/org/repo",
            "--target", "backend",
            "--java-version", "21",
            "--output", OUTPUT
        );
    }

    @Test
    void replacesPlaceholdersWithinAnArgument() {
        final var scanner = scanner(List.of("--path={repository}/{target}", "--output={output}"));

        assertThat(scanner.command("/workspace", target("app", null)))
            .containsExactly("--path=/workspace/app", "--output=" + OUTPUT);
    }

    @Test
    void passesUnknownValuesAsEmpty() {
        final var scanner = scanner(List.of("--target", "{target}", "--java-version", "{languageVersion}"));

        assertThat(scanner.command("/workspace", target(null, null)))
            .containsExactly("--target", "", "--java-version", "");
    }

    @Test
    void keepsReplacementCharactersInValues() {
        final var scanner = scanner(List.of("{repository}"));

        assertThat(scanner.command("/checkouts/$1\\repo", target("", null)))
            .containsExactly("/checkouts/$1\\repo");
    }

    @Test
    void supportsTargetsOfItsLanguage() {
        final var scanner = scanner(List.of("scan"));

        assertThat(scanner.supports(target("", null))).isTrue();

        final var typescript = target("", null);
        typescript.setLanguage(ScanTarget.Language.TYPESCRIPT);

        assertThat(scanner.supports(typescript)).isFalse();
    }

    @Test
    void rejectsAnUnknownPlaceholder() {
        assertThatThrownBy(() -> scanner(List.of("--path", "{repo}")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown placeholder {repo} in the JAVA scanner's command");
    }

    @Test
    void rejectsAMissingImageOrCommand() {
        assertThatThrownBy(() -> new ImageScannerDefinition(ScanTarget.Language.JAVA, " ", List.of("scan"), OUTPUT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The JAVA scanner needs an image");

        assertThatThrownBy(() -> new ImageScannerDefinition(ScanTarget.Language.JAVA, IMAGE, List.of(), OUTPUT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("The JAVA scanner needs a command");
    }

    @Test
    void rejectsAnOutputThatIsNotAnAbsoluteFileInADirectory() {
        for (final var output : new String[] {null, "scan-result.json", "/scan-result.json", "/output/"}) {
            assertThatThrownBy(() -> new ImageScannerDefinition(ScanTarget.Language.JAVA, IMAGE, List.of("scan"), output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("output must be an absolute file path in a directory");
        }
    }

    private static ImageScannerDefinition scanner(final List<String> command) {
        return new ImageScannerDefinition(ScanTarget.Language.JAVA, IMAGE, command, OUTPUT);
    }

    private static ScanTarget target(final String path, final String languageVersion) {
        final var target = new ScanTarget();
        target.setPath(path);
        target.setLanguage(ScanTarget.Language.JAVA);
        target.setLanguageVersion(languageVersion);

        return target;
    }
}
