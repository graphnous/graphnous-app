package dev.graphnous.scanner.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.definition.ImageScannerDefinition;
import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.listener.ScanProcessListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the local Docker daemon; skipped when it is not reachable.
 */
@EnabledIf("dockerAvailable")
class DockerSandboxTest {

    private static final String IMAGE = "alpine:3.22";

    private static final String RESULT = """
        {"format":"graphnous-scan-result","version":"1",\
        "target":{"path":"backend","language":"JAVA","languageVersion":"25"},\
        "modules":[{"path":"core","files":[]}]}""";

    private final DockerClient docker = DockerSandbox.defaultClient();

    private final List<String> stdout = new CopyOnWriteArrayList<>();
    private final List<String> stderr = new CopyOnWriteArrayList<>();

    private final DockerSandbox sandbox = new DockerSandbox(
        docker,
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

    @TempDir
    Path repository;

    static boolean dockerAvailable() {
        try {
            DockerSandbox.defaultClient().pingCmd().exec();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Test
    void readsTheResultTheScannerWritesToOutput() throws IOException {
        final var scanner = scanner("""
            printf '%s' '%s' > "$6"
            """.formatted("%s", RESULT));

        final var result = sandbox.execute(scanner, repository, target());

        assertThat(result.getFormat()).isEqualTo("graphnous-scan-result");
        assertThat(result.getTarget().getPath()).isEqualTo("backend");
        assertThat(result.getModules())
            .singleElement()
            .satisfies(module -> assertThat(module.getPath()).isEqualTo("core"));
    }

    @Test
    void passesContainerPathsToTheScanner() throws IOException {
        final var scanner = scanner("""
            echo "$0 $@"
            printf '%s' '%s' > "$6"
            """.formatted("%s", RESULT));

        sandbox.execute(scanner, repository, target());

        assertThat(stdout).containsExactly(
            "scanner --path /workspace --target backend --output /output/scan-result.json"
        );
    }

    @Test
    void mountsTheRepositoryReadOnly() throws IOException {
        Files.writeString(repository.resolve("Service.java"), "class Service { }\n");

        final var scanner = scanner("""
            cat /workspace/Service.java
            touch /workspace/created 2>/dev/null || echo read-only
            printf '%s' '%s' > "$6"
            """.formatted("%s", RESULT));

        sandbox.execute(scanner, repository, target());

        assertThat(stdout).containsExactly("class Service { }", "read-only");
        assertThat(repository.resolve("created")).doesNotExist();
    }

    @Test
    void forwardsStdoutAndStderrToTheListener() throws IOException {
        final var scanner = scanner("""
            echo out-1
            echo err-1 >&2
            echo out-2
            printf 'no newline'
            printf '%s' '%s' > "$6"
            """.formatted("%s", RESULT));

        sandbox.execute(scanner, repository, target());

        assertThat(stdout).containsExactly("out-1", "out-2", "no newline");
        assertThat(stderr).containsExactly("err-1");
    }

    @Test
    void failsWhenTheScannerExitsWithAnError() throws IOException {
        final var scanner = scanner("""
            echo broken >&2
            exit 3
            """);

        assertThatThrownBy(() -> sandbox.execute(scanner, repository, target()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Scanner failed with exit code 3");

        assertThat(stderr).containsExactly("broken");
    }

    @Test
    void failsWhenTheScannerWritesNoResult() throws IOException {
        final var scanner = scanner("echo nothing to report");

        assertThatThrownBy(() -> sandbox.execute(scanner, repository, target()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Scanner did not write a result to /output/scan-result.json");
    }

    @Test
    void letsANonRootScannerWriteItsResult() throws IOException {
        // Images may run as any user, so the output directory must be
        // writable for all. The script writes as alpine's "nobody".
        final var scanner = scanner("""
            printf '%s' '%s' > /tmp/result
            exec su -s /bin/sh nobody -c "id -u; cp /tmp/result $6"
            """.formatted("%s", RESULT));

        final var result = sandbox.execute(scanner, repository, target());

        assertThat(stdout).containsExactly("65534");
        assertThat(result.getFormat()).isEqualTo("graphnous-scan-result");
    }

    @Test
    void readsTheResultFromTheScannersOutput() throws IOException {
        final var scanner = new ImageScannerDefinition(
            ScanTarget.Language.JAVA,
            IMAGE,
            List.of("sh", "-c", "printf '%s' '%s' > \"$1\"".formatted("%s", RESULT), "scanner", "{output}"),
            "/results/java/result.json"
        );

        final var result = sandbox.execute(scanner, repository, target());

        assertThat(result.getFormat()).isEqualTo("graphnous-scan-result");
    }

    @Test
    void removesTheContainerAfterwards() throws IOException {
        final var scanner = scanner("""
            printf '%s' '%s' > "$6"
            """.formatted("%s", RESULT));

        final var before = containers();

        sandbox.execute(scanner, repository, target());

        assertThat(containers()).isEqualTo(before);
    }

    @Test
    void removesTheContainerWhenTheScannerFails() {
        final var scanner = scanner("exit 1");

        final var before = containers();

        assertThatThrownBy(() -> sandbox.execute(scanner, repository, target()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(containers()).isEqualTo(before);
    }

    @Test
    void readsTheRepositoryFromAVolumeAtTheSamePath() throws IOException, InterruptedException {
        final var volume = "graphnous-test-" + UUID.randomUUID();

        docker.createVolumeCmd().withName(volume).exec();

        try {
            // Check out a "repository" into the volume, as a clone job would
            runInVolume(volume, "mkdir -p /checkouts/org/repo && echo hello > /checkouts/org/repo/README");

            final var sandbox = new DockerSandbox(
                docker,
                DockerWorkspace.volume(volume, Path.of("/checkouts")),
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

            final var scanner = scanner("""
                echo "$2"
                cat "$2/README"
                touch "$2/created" 2>/dev/null || echo read-only
                printf '%s' '%s' > "$6"
                """.formatted("%s", RESULT));

            sandbox.execute(scanner, Path.of("/checkouts/org/repo"), target());

            assertThat(stdout).containsExactly("/checkouts/org/repo", "hello", "read-only");
        } finally {
            docker.removeVolumeCmd(volume).exec();
        }
    }

    @Test
    void rejectsRepositoryOutsideTheVolumeBeforeStartingAContainer() throws IOException {
        final var sandbox = new DockerSandbox(
            docker,
            DockerWorkspace.volume("checkouts", Path.of("/checkouts")),
            new ObjectMapper(),
            new ScanProcessListener() {
                @Override
                public void stdout(final String line) { }

                @Override
                public void stderr(final String line) { }
            }
        );

        final var scanner = scanner("exit 0");
        final var before = containers();

        assertThatThrownBy(() -> sandbox.execute(scanner, Path.of("/elsewhere/repo"), target()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not inside volume checkouts");

        assertThat(containers()).isEqualTo(before);
    }

    private void runInVolume(final String volume, final String script) throws InterruptedException {
        final var id = docker.createContainerCmd(IMAGE)
            .withCmd("sh", "-c", script)
            .withHostConfig(HostConfig.newHostConfig().withMounts(List.of(
                new Mount().withType(MountType.VOLUME).withSource(volume).withTarget("/checkouts")
            )))
            .exec()
            .getId();

        try {
            docker.startContainerCmd(id).exec();
            docker.waitContainerCmd(id).exec(new WaitContainerResultCallback()).awaitStatusCode();
        } finally {
            docker.removeContainerCmd(id).withForce(true).exec();
        }
    }

    private long containers() {
        return docker.listContainersCmd()
            .withShowAll(true)
            .withAncestorFilter(List.of(IMAGE))
            .exec()
            .size();
    }

    private static ScanTarget target() {
        final var target = new ScanTarget();
        target.setPath("backend");
        target.setLanguage(ScanTarget.Language.JAVA);

        return target;
    }

    /**
     * A scanner that runs the script with sh in {@value #IMAGE}; its
     * arguments are --path $2 --target $4 --output $6.
     */
    private static ScannerDefinition scanner(final String script) {
        return new ImageScannerDefinition(
            ScanTarget.Language.JAVA,
            IMAGE,
            List.of(
                "sh", "-c", script, "scanner",
                "--path", ImageScannerDefinition.REPOSITORY,
                "--target", ImageScannerDefinition.TARGET,
                "--output", ImageScannerDefinition.OUTPUT
            ),
            "/output/scan-result.json"
        );
    }
}
