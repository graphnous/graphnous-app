package dev.graphnous.api.project.scanner.docker;

import dev.graphnous.application.project.scanner.ScanLogger;
import dev.graphnous.application.project.scanner.SourceCheckout;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;
import dev.graphnous.scanner.docker.DockerGitCheckout;
import dev.graphnous.scanner.listener.ScanProcessListener;

import java.nio.file.Path;

/**
 * Checks out each scan into its own directory of the workspace, named
 * after the scan.
 */
public class DockerSourceCheckout implements SourceCheckout {

    private final DockerGitCheckout gitCheckout;
    private final DockerScanContainers containers;

    public DockerSourceCheckout(
        final DockerGitCheckout gitCheckout,
        final DockerScanContainers containers
    ) {
        this.gitCheckout = gitCheckout;
        this.containers = containers;
    }

    @Override
    public Path checkout(
        final Scan.ScanId scanId,
        final String gitUrl,
        final Scan.SourceRevision revision,
        final ScanLogger logger
    ) {
        final var source = new DockerGitCheckout.GitSource(
            gitUrl,
            revision == null ? null : revision.branch(),
            revision == null ? null : revision.revision()
        );

        return gitCheckout.checkout(name(scanId), source, output(logger), containers.labels(scanId));
    }

    @Override
    public void remove(
        final Scan.ScanId scanId,
        final ScanLogger logger
    ) {
        gitCheckout.remove(name(scanId), output(logger));
    }

    private static String name(final Scan.ScanId scanId) {
        return scanId.id().toString();
    }

    /**
     * Git reports progress on stderr, so both streams are informational;
     * a failed checkout is reported by the exception it throws.
     */
    private static ScanProcessListener output(final ScanLogger logger) {
        return new ScanProcessListener() {
            @Override
            public void stdout(final String line) {
                logger.log(ScanLogLevel.INFO, line);
            }

            @Override
            public void stderr(final String line) {
                logger.log(ScanLogLevel.INFO, line);
            }
        };
    }
}
