package dev.graphnous.scanner.docker;

import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;

import java.nio.file.Path;

/**
 * How the repository to scan is made available to scanner containers.
 */
public sealed interface DockerWorkspace {

    /**
     * Where the repository is mounted in the scanner container when it is
     * a plain directory on the Docker host.
     */
    String WORKSPACE = "/workspace";

    /**
     * The repository is a directory on the Docker host, bind-mounted
     * read-only at {@value #WORKSPACE}. Works when this process runs
     * directly on the Docker host.
     */
    static DockerWorkspace hostDirectory() {
        return new HostDirectory();
    }

    /**
     * The repository is checked out in a Docker volume, which this process
     * sees mounted at {@code mountPath} (for example when it runs in a
     * container itself). Scanner containers get the same volume at the
     * same path, so the repository path stays valid inside them.
     */
    static DockerWorkspace volume(
        final String name,
        final Path mountPath
    ) {
        return new NamedVolume(name, mountPath);
    }

    /**
     * The mount giving a scanner container access to the repository, and
     * the repository path to pass to the scanner inside that container.
     */
    Mounted mount(Path repository);

    record Mounted(
        Mount mount,
        String repository
    ) { }

    record HostDirectory() implements DockerWorkspace {

        @Override
        public Mounted mount(final Path repository) {
            return new Mounted(
                new Mount()
                    .withType(MountType.BIND)
                    .withSource(repository.toAbsolutePath().normalize().toString())
                    .withTarget(WORKSPACE)
                    .withReadOnly(true),
                WORKSPACE
            );
        }
    }

    record NamedVolume(
        String name,
        Path mountPath
    ) implements DockerWorkspace {

        public NamedVolume {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Volume name is required");
            }

            if (mountPath == null || !mountPath.isAbsolute()) {
                throw new IllegalArgumentException(
                    "Volume mount path must be absolute: " + mountPath
                );
            }

            mountPath = mountPath.normalize();
        }

        @Override
        public Mounted mount(final Path repository) {
            final var path = repository.toAbsolutePath().normalize();

            if (!path.startsWith(mountPath)) {
                throw new IllegalStateException(
                    "Repository " + path + " is not inside volume " + name
                    + ", which is mounted at " + mountPath
                );
            }

            return new Mounted(
                new Mount()
                    .withType(MountType.VOLUME)
                    .withSource(name)
                    .withTarget(mountPath.toString())
                    .withReadOnly(true),
                path.toString()
            );
        }
    }
}
