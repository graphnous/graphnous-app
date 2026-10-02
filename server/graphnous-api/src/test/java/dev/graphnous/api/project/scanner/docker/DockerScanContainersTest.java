package dev.graphnous.api.project.scanner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.docker.DockerSandbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the local Docker daemon; skipped when it is not reachable.
 * Each test uses its own instance name, so it only touches its own
 * containers.
 */
@EnabledIf("dockerAvailable")
class DockerScanContainersTest {

    private static final String IMAGE = "alpine:3";

    private final DockerClient docker = DockerSandbox.defaultClient();

    private final String instance = "graphnous-test-" + UUID.randomUUID();

    private final DockerScanContainers containers = new DockerScanContainers(docker, instance);

    private final List<String> started = new ArrayList<>();

    static boolean dockerAvailable() {
        try {
            DockerSandbox.defaultClient().pingCmd().exec();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @BeforeAll
    static void pullImage() throws InterruptedException {
        DockerSandbox.defaultClient()
            .pullImageCmd(IMAGE)
            .exec(new PullImageResultCallback())
            .awaitCompletion();
    }

    @AfterEach
    void removeContainers() {
        for (final var id : started) {
            try {
                docker.removeContainerCmd(id).withForce(true).exec();
            } catch (final NotFoundException ignored) {
                // Removed by the test
            }
        }
    }

    @Test
    void stopsTheContainersOfTheCancelledScanOnly() {
        final var cancelled = Scan.ScanId.generate();
        final var other = Scan.ScanId.generate();

        final var cancelledContainer = start(containers.labels(cancelled));
        final var otherContainer = start(containers.labels(other));

        containers.cancel(cancelled);

        assertThat(exists(cancelledContainer)).isFalse();
        assertThat(exists(otherContainer)).isTrue();

        assertThat(containers.isCancelled(cancelled)).isTrue();
        assertThat(containers.isCancelled(other)).isFalse();
    }

    @Test
    void stopsEveryContainerOfTheInstance() {
        final var first = start(containers.labels(Scan.ScanId.generate()));
        final var second = start(containers.labels(Scan.ScanId.generate()));

        // Another server on the same daemon
        final var otherInstance = new DockerScanContainers(docker, instance + "-other");
        final var foreign = start(otherInstance.labels(Scan.ScanId.generate()));

        containers.cancelAll();

        assertThat(exists(first)).isFalse();
        assertThat(exists(second)).isFalse();
        assertThat(exists(foreign)).isTrue();
    }

    @Test
    void cancelsAScanWithoutContainers() {
        final var scanId = Scan.ScanId.generate();

        containers.cancel(scanId);

        assertThat(containers.isCancelled(scanId)).isTrue();
    }

    private String start(final Map<String, String> labels) {
        final var id = docker.createContainerCmd(IMAGE)
            .withCmd("sleep", "300")
            .withLabels(labels)
            .exec()
            .getId();

        started.add(id);
        docker.startContainerCmd(id).exec();

        return id;
    }

    private boolean exists(final String id) {
        try {
            docker.inspectContainerCmd(id).exec();
            return true;
        } catch (final NotFoundException e) {
            return false;
        }
    }
}
