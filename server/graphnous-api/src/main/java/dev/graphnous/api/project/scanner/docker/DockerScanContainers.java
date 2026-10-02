package dev.graphnous.api.project.scanner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import dev.graphnous.application.project.scanner.ScanCanceller;
import dev.graphnous.domain.scan.Scan;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Labels the containers started for a scan, and stops them by those labels.
 * <p>
 * A cancelled scan is remembered, so it does not start containers for the
 * targets it has not scanned yet. Only timed-out scans are cancelled, so the
 * set stays small.
 */
public class DockerScanContainers implements ScanCanceller {

    /**
     * On every container this server starts for a scan, with the name of the
     * server instance, so an instance only stops its own containers.
     */
    static final String INSTANCE = "dev.graphnous.instance";

    /**
     * The ID of the scan the container belongs to.
     */
    static final String SCAN = "dev.graphnous.scan";

    private final DockerClient docker;
    private final String instance;

    private final Set<Scan.ScanId> cancelled = ConcurrentHashMap.newKeySet();

    /**
     * @param instance the name of this server instance on the Docker daemon
     */
    public DockerScanContainers(
        final DockerClient docker,
        final String instance
    ) {
        this.docker = docker;
        this.instance = instance;
    }

    public Map<String, String> labels(final Scan.ScanId scanId) {
        return Map.of(
            INSTANCE, instance,
            SCAN, scanId.id().toString()
        );
    }

    public boolean isCancelled(final Scan.ScanId scanId) {
        return cancelled.contains(scanId);
    }

    @Override
    public void cancel(final Scan.ScanId scanId) {
        cancelled.add(scanId);

        remove(labels(scanId));
    }

    @Override
    public void cancelAll() {
        remove(Map.of(INSTANCE, instance));
    }

    private void remove(final Map<String, String> labels) {
        final var containers = docker.listContainersCmd()
            .withShowAll(true)
            .withLabelFilter(labels)
            .exec();

        for (final var container : containers) {
            try {
                // Forced, so a running container is stopped first
                docker.removeContainerCmd(container.getId())
                    .withForce(true)
                    .exec();
            } catch (final NotFoundException ignored) {
                // Already gone, for example removed by the scan itself
            }
        }
    }
}
