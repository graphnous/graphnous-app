package dev.graphnous.api.project.scanner.docker;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Where scanners run; {@link ScannersProperties} configures the scanners
 * themselves.
 *
 * @param instance       the name of this server on the Docker daemon;
 *                       its containers carry it, so a server only stops
 *                       its own
 * @param workspace      where repositories are checked out for scanning
 * @param gitImage       the image the checkout runs in
 * @param sshKnownHosts  the host keys of the git servers private
 *                       repositories are fetched from over SSH, in the
 *                       format of OpenSSH's {@code known_hosts}
 */
@ConfigurationProperties("graphnous.scanner")
public record ScannerProperties(
    String instance,
    Workspace workspace,
    String gitImage,
    String sshKnownHosts
) {

    /**
     * @param type   VOLUME: the named volume {@code volume}, which this
     *               server must have mounted at {@code path}. HOST: the
     *               directory {@code path} on the Docker host, for running
     *               the server directly on it.
     * @param volume the volume name, for type VOLUME
     * @param path   where this server sees the checkouts
     */
    public record Workspace(
        Type type,
        String volume,
        Path path
    ) {

        public enum Type {
            VOLUME,
            HOST
        }
    }
}
