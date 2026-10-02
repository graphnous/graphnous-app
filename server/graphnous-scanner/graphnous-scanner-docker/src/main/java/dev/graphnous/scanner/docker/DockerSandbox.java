package dev.graphnous.scanner.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.listener.ScanProcessListener;
import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;
import dev.graphnous.scanner.sandbox.ScanSandbox;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Runs a scanner in a container of its image, with its command as the
 * entrypoint. The repository is the only mount, made available read-only
 * as the {@link DockerWorkspace} describes. The directory of the scanner's
 * output is created writable before the container starts, and the result
 * is copied out of it after it stops, so no paths of this process have to
 * exist on the Docker host.
 */
public class DockerSandbox implements ScanSandbox {

    private final DockerClient docker;
    private final DockerWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final ScanProcessListener scanProcessListener;
    private final Map<String, String> labels;

    public DockerSandbox(
        final ObjectMapper objectMapper,
        final ScanProcessListener scanProcessListener
    ) {
        this(defaultClient(), objectMapper, scanProcessListener);
    }

    public DockerSandbox(
        final DockerClient docker,
        final ObjectMapper objectMapper,
        final ScanProcessListener scanProcessListener
    ) {
        this(docker, DockerWorkspace.hostDirectory(), objectMapper, scanProcessListener);
    }

    public DockerSandbox(
        final DockerClient docker,
        final DockerWorkspace workspace,
        final ObjectMapper objectMapper,
        final ScanProcessListener scanProcessListener
    ) {
        this(docker, workspace, objectMapper, scanProcessListener, Map.of());
    }

    /**
     * @param labels put on every scanner container, so they can be found
     *               again, for example to stop them
     */
    public DockerSandbox(
        final DockerClient docker,
        final DockerWorkspace workspace,
        final ObjectMapper objectMapper,
        final ScanProcessListener scanProcessListener,
        final Map<String, String> labels
    ) {
        this.docker = docker;
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.scanProcessListener = scanProcessListener;
        this.labels = Map.copyOf(labels);
    }

    /**
     * A client for the local Docker daemon, honouring {@code DOCKER_HOST}.
     */
    public static DockerClient defaultClient() {
        final var config = DefaultDockerClientConfig
            .createDefaultConfigBuilder()
            .build();

        final var httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig())
            .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    @Override
    public ScanResultSchema execute(
        final ScannerDefinition definition,
        final Path path,
        final ScanTarget target
    ) {
        final var repository = workspace.mount(path);

        final var image = definition.image();
        final var output = definition.output();

        String containerId = null;

        try {
            Containers.pullIfMissing(docker, image);

            containerId = docker
                .createContainerCmd(image)
                .withEntrypoint(definition.command(repository.repository(), target))
                .withLabels(labels)
                .withHostConfig(
                    HostConfig.newHostConfig()
                        .withMounts(List.of(repository.mount()))
                )
                .exec()
                .getId();

            docker.copyArchiveToContainerCmd(containerId)
                .withTarInputStream(new ByteArrayInputStream(
                    ContainerArchive.writableDirectory(output.substring(0, output.lastIndexOf('/')))
                ))
                .withRemotePath("/")
                .exec();

            final var exitCode = Containers.runAndForwardOutput(docker, containerId, scanProcessListener);

            if (exitCode != 0) {
                throw new IllegalStateException(
                    "Scanner failed with exit code " + exitCode
                );
            }

            return readResult(containerId, output);

        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to transfer files to or from the scanner container",
                e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "Scanner execution was interrupted",
                e
            );
        } finally {
            if (containerId != null) {
                Containers.remove(docker, containerId);
            }
        }
    }

    private ScanResultSchema readResult(
        final String containerId,
        final String output
    ) throws IOException {
        try (final var archive = docker.copyArchiveFromContainerCmd(containerId, output).exec()) {
            return objectMapper.readValue(
                ContainerArchive.firstFile(archive),
                ScanResultSchema.class
            );
        } catch (NotFoundException e) {
            throw new IllegalStateException(
                "Scanner did not write a result to " + output,
                e
            );
        }
    }
}
