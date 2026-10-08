package dev.graphnous.scanner.docker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Which commit a checkout is of, read as the checkout script leaves it; with
 * git here rather than in a container, so it runs without Docker.
 */
class DockerGitCheckoutRevisionTest {

    @TempDir
    Path checkout;

    @BeforeEach
    void commit() throws Exception {
        git("init", "-q", "-b", "main");
        Files.writeString(checkout.resolve("file.txt"), "first");
        git("add", "file.txt");
        git("-c", "user.name=test", "-c", "user.email=test@example.com", "commit", "-q", "-m", "first");
    }

    @Test
    void isTheCommitOfADetachedHead() throws Exception {
        final var commit = git("rev-parse", "HEAD");
        git("-c", "advice.detachedHead=false", "checkout", "-q", "--detach");

        assertThat(DockerGitCheckout.revision(checkout)).isEqualTo(commit).hasSize(40);
    }

    @Test
    void refusesAHeadThatNamesABranch() {
        assertThatThrownBy(() -> DockerGitCheckout.revision(checkout))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ref: refs/heads/main");
    }

    @Test
    void refusesADirectoryThatIsNotACheckout(@TempDir final Path empty) {
        assertThatThrownBy(() -> DockerGitCheckout.revision(empty))
            .isInstanceOf(UncheckedIOException.class);
    }

    private String git(final String... args) throws IOException, InterruptedException {
        final var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));

        final var process = new ProcessBuilder(command)
            .directory(checkout.toFile())
            .redirectErrorStream(true)
            .start();

        final var out = new String(process.getInputStream().readAllBytes()).trim();

        if (process.waitFor() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + out);
        }

        return out;
    }
}
