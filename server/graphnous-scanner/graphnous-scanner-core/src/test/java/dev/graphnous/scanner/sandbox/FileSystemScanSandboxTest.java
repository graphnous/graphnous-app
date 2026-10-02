package dev.graphnous.scanner.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.listener.ScanProcessListener;
import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledOnOs(OS.WINDOWS)
class FileSystemScanSandboxTest {

    private static final String RESULT = """
        {
          "format": "graphnous-scan-result",
          "version": "1",
          "target": { "path": "backend", "language": "JAVA", "languageVersion": "25" },
          "modules": [ { "path": "core", "files": [] } ]
        }
        """;

    private final List<String> stdout = new CopyOnWriteArrayList<>();
    private final List<String> stderr = new CopyOnWriteArrayList<>();

    private final FileSystemScanSandbox sandbox = new FileSystemScanSandbox(
        new ObjectMapper(),
        new ScanProcessListener() {
            @Override
            public void stdout(final String line) {
                stdout.add(line);
            }

            @Override
            public void stderr(final String line) {
                stderr.add(line);
            }
        }
    );

    @Test
    void readsTheResultTheScannerWritesToOutput() {
        // The sandbox appends "--output <file>", which the script receives as $1 and $2.
        final var scanner = shell("echo scanning; printf '%s' '" + RESULT + "' > \"$2\"");

        final var result = sandbox.execute(scanner, Path.of("repo"), new ScanTarget());

        assertThat(result.getFormat()).isEqualTo("graphnous-scan-result");
        assertThat(result.getTarget().getPath()).isEqualTo("backend");
        assertThat(result.getModules())
            .singleElement()
            .satisfies(module -> assertThat(module.getPath()).isEqualTo("core"));
    }

    @Test
    void forwardsProcessOutputToTheListener() {
        final var scanner = shell("echo out; echo err >&2; printf '%s' '" + RESULT + "' > \"$2\"");

        sandbox.execute(scanner, Path.of("repo"), new ScanTarget());

        assertThat(stdout).containsExactly("out");
        assertThat(stderr).containsExactly("err");
    }

    @Test
    void failsWhenTheScannerExitsWithAnError() {
        final var scanner = shell("echo broken >&2; exit 3");

        assertThatThrownBy(() -> sandbox.execute(scanner, Path.of("repo"), new ScanTarget()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Scanner failed with exit code 3");

        assertThat(stderr).containsExactly("broken");
    }

    @Test
    void removesTheOutputFileAfterwards() {
        final var scanner = shell("echo \"$2\"; printf '%s' '" + RESULT + "' > \"$2\"");

        sandbox.execute(scanner, Path.of("repo"), new ScanTarget());

        assertThat(stdout).singleElement().satisfies(output ->
            assertThat(Path.of(output)).doesNotExist()
        );
    }

    @Test
    void reportsWhenTheOutputFileCannotBeRemoved() throws Exception {
        // Replacing the output file with a non-empty directory makes the cleanup fail.
        final var scanner = shell("echo \"$2\"; rm \"$2\"; mkdir \"$2\"; touch \"$2/blocker\"");

        try {
            assertThatThrownBy(() -> sandbox.execute(scanner, Path.of("repo"), new ScanTarget()));

            assertThat(stderr).singleElement().satisfies(line ->
                assertThat(line).startsWith("Failed to delete scan output file " + stdout.getFirst())
            );
        } finally {
            final var output = Path.of(stdout.getFirst());

            Files.deleteIfExists(output.resolve("blocker"));
            Files.deleteIfExists(output);
        }
    }

    private static ScannerDefinition shell(final String script) {
        return new ScannerDefinition() {
            @Override
            public boolean supports(final ScanTarget target) {
                return true;
            }

            @Override
            public Path scanner() {
                return Path.of("sh");
            }

            @Override
            public List<String> command(final Path repository, final ScanTarget target) {
                return new ArrayList<>(List.of("sh", "-c", script, "sh"));
            }

            @Override
            public List<String> containerCommand(
                final String scanner,
                final String repository,
                final ScanTarget target
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String image(final ScanTarget target) {
                return "shell";
            }
        };
    }
}
