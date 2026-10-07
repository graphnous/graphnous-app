package dev.graphnous.scanner.docker;

import com.github.dockerjava.api.DockerClient;
import dev.graphnous.scanner.docker.DockerGitCheckout.GitSource;
import dev.graphnous.scanner.listener.ScanProcessListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the local Docker daemon; skipped when it is not reachable.
 * The origin repository lives inside the checkout directory so the git
 * container can clone it over file://.
 */
@EnabledIf("dockerAvailable")
class DockerGitCheckoutTest {

    private final DockerClient docker = DockerSandbox.defaultClient();

    private final List<String> output = new CopyOnWriteArrayList<>();

    private final ScanProcessListener listener = new ScanProcessListener() {
        @Override
        public void stdout(final String line) {
            output.add(line);
        }

        @Override
        public void stderr(final String line) {
            output.add(line);
        }
    };

    @TempDir
    Path temp;

    private Path root;
    private Path origin;
    private DockerGitCheckout checkout;

    static boolean dockerAvailable() {
        return DockerSandboxTest.dockerAvailable();
    }

    @BeforeEach
    void createOrigin() throws Exception {
        // Docker Desktop shares /private/var, not the /var symlink
        root = temp.toRealPath();
        origin = root.resolve("origin");

        Files.createDirectories(origin);
        git(origin, "init", "-q", "-b", "main");
        commit("first");

        checkout = DockerGitCheckout.inHostDirectory(docker, root, DockerGitCheckout.DEFAULT_IMAGE);
    }

    /**
     * The checkouts belong to the git container's user (root on Linux), so
     * they are removed through a container before the temp directory is.
     */
    @AfterEach
    void removeCheckouts() throws IOException {
        try (final var entries = Files.list(root)) {
            for (final var entry : entries.toList()) {
                if (!entry.equals(origin)) {
                    checkout.remove(entry.getFileName().toString(), listener);
                }
            }
        }
    }

    @Test
    void checksOutTheDefaultBranch() throws Exception {
        commit("second");

        final var path = checkout.checkout("scan-1", source(null, null), listener);

        assertThat(path).isEqualTo(root.resolve("scan-1"));
        assertThat(path.resolve("file.txt")).hasContent("second");
    }

    @Test
    void checksOutABranch() throws Exception {
        git(origin, "checkout", "-q", "-b", "feature");
        commit("feature");
        git(origin, "checkout", "-q", "main");
        commit("main");

        final var path = checkout.checkout("scan-1", source("feature", null), listener);

        assertThat(path.resolve("file.txt")).hasContent("feature");
    }

    @Test
    void checksOutARevision() throws Exception {
        final var first = git(origin, "rev-parse", "HEAD");
        commit("second");

        final var path = checkout.checkout("scan-1", source("main", first), listener);

        assertThat(path.resolve("file.txt")).hasContent("first");
        assertThat(output).contains("Checked out " + first);
    }

    @Test
    void replacesAnEarlierCheckout() throws Exception {
        // Left behind by an earlier checkout. Written from here, not into a
        // checkout: those belong to the git container's user (root on Linux).
        Files.createDirectories(root.resolve("scan-1"));
        Files.writeString(root.resolve("scan-1").resolve("leftover"), "x");

        final var path = checkout.checkout("scan-1", source(null, null), listener);

        assertThat(path.resolve("leftover")).doesNotExist();
        assertThat(path.resolve("file.txt")).hasContent("first");

        commit("second");
        checkout.checkout("scan-1", source(null, null), listener);

        assertThat(path.resolve("file.txt")).hasContent("second");
    }

    @Test
    void checksOutOtherUrlsWithoutTheSshKey() throws Exception {
        commit("second");

        final var source = new GitSource("file://" + origin, null, null, "not a real key", null);

        final var path = checkout.checkout("scan-1", source, listener);

        assertThat(path.resolve("file.txt")).hasContent("second");
    }

    @Test
    void usesTheSshKeyForSshUrlsOnly() {
        final var https = new GitSource("https://github.com/org/repo.git", null, null, "secret", null);

        assertThat(https.sshKey()).isNull();
        assertThat(https.knownHosts()).isNull();

        for (final var url : List.of("git@github.com:org/repo.git", "github.com:org/repo.git", "ssh://git@github.com/org/repo.git")) {
            final var ssh = new GitSource(url, null, null, "secret", "github.com ssh-ed25519 AAAA");

            assertThat(ssh.sshKey()).as(url).isEqualTo("secret");
        }
    }

    @Test
    void leavesTheSshKeyOutOfItsDescription() {
        final var source = new GitSource("git@example.com:org/repo.git", null, null, "secret", "example.com ssh-ed25519 AAAA");

        assertThat(source.toString()).doesNotContain("secret");
    }

    @Test
    void requiresKnownHostsWithAnSshKey() {
        assertThatThrownBy(() -> new GitSource("git@example.com:org/repo.git", null, null, "secret", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Known hosts");
    }

    @Test
    void removesACheckout() {
        final var path = checkout.checkout("scan-1", source(null, null), listener);

        checkout.remove("scan-1", listener);

        assertThat(path).doesNotExist();
        assertThat(origin).exists();
    }

    @Test
    void failsForAMissingRepository() {
        final var missing = new GitSource("file://" + root.resolve("missing"), null, null);

        assertThatThrownBy(() -> checkout.checkout("scan-1", missing, listener))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("failed with exit code");
    }

    @Test
    void givesTheScannerTheVolumeItChecksOutInto() {
        final var volume = new DockerWorkspace.NamedVolume("checkouts", Path.of("/checkouts"));

        final var inVolume = DockerGitCheckout.inVolume(docker, volume, DockerGitCheckout.DEFAULT_IMAGE);

        assertThat(inVolume.workspace()).isEqualTo(volume);
        assertThat(inVolume.workspace().mount(Path.of("/checkouts/scan-1")).repository())
            .isEqualTo("/checkouts/scan-1");
    }

    @Test
    void givesTheScannerTheHostDirectory() {
        assertThat(checkout.workspace()).isEqualTo(DockerWorkspace.hostDirectory());
    }

    @Test
    void rejectsNamesOutsideTheWorkspace() {
        assertThatThrownBy(() -> checkout.checkout("../escape", source(null, null), listener))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> checkout.remove("..", listener))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSourcesThatLookLikeOptions() {
        assertThatThrownBy(() -> new GitSource("--upload-pack=touch /tmp/x", null, null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new GitSource("https://example.com/repo.git", "-b", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private GitSource source(final String branch, final String revision) {
        return new GitSource("file://" + origin, branch, revision);
    }

    private void commit(final String content) throws Exception {
        Files.writeString(origin.resolve("file.txt"), content);
        git(origin, "add", "file.txt");
        git(origin, "-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-q", "-m", content);
    }

    private static String git(final Path directory, final String... args) throws IOException, InterruptedException {
        final var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));

        final var process = new ProcessBuilder(command)
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .start();

        final var out = new String(process.getInputStream().readAllBytes()).trim();

        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.join(" ", command) + " failed: " + out);
        }

        return out;
    }
}
