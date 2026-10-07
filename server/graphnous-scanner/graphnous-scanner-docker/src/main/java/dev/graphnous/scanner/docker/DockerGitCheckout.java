package dev.graphnous.scanner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import dev.graphnous.scanner.listener.ScanProcessListener;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks out git repositories in a container, into a directory of the
 * workspace that the scanner containers mount afterward (see
 * {@link #workspace()}). The workspace is mounted in the git container at
 * the same path this process sees it at, so the returned checkout path is
 * valid both here, for planning, and for the {@link DockerSandbox}.
 */
public class DockerGitCheckout {

    public static final String DEFAULT_IMAGE = "alpine/git:2.54.0";

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /**
     * Fetches only the requested revision or branch when the server allows
     * it, and falls back to fetching the branch (or everything) for servers
     * that refuse to serve a commit by its hash. Arguments are passed as
     * positional parameters so none of them is interpreted by the shell.
     * A private repository is fetched over SSH with the key and known
     * hosts copied to {@link #SSH_DIRECTORY}, when there is a key. Only
     * servers in the known hosts are trusted.
     */
    private static final String SCRIPT = """
        set -eu
        dir="$1"; url="$2"; branch="$3"; revision="$4"
        if [ -f "%1$s/id_key" ]; then
          export GIT_SSH_COMMAND="ssh -i %1$s/id_key -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=%1$s/known_hosts -o GlobalKnownHostsFile=/dev/null"
        fi
        rm -rf "$dir"
        git init -q "$dir"
        cd "$dir"
        git remote add origin "$url"
        if [ -n "$revision" ]; then
          echo "Fetching $revision"
          git fetch --depth 1 origin "$revision" \\
            || git fetch origin ${branch:+"$branch"}
          git -c advice.detachedHead=false checkout -q "$revision"
        else
          echo "Fetching ${branch:-HEAD}"
          git fetch --depth 1 origin "${branch:-HEAD}"
          git -c advice.detachedHead=false checkout -q FETCH_HEAD
        fi
        echo "Checked out $(git rev-parse HEAD)"
        """;

    /**
     * Where the SSH key and known hosts are copied in the git container;
     * outside the workspace, so they are never visible to the scanners.
     */
    private static final String SSH_DIRECTORY = "/graphnous-ssh";

    private final DockerClient docker;
    private final DockerWorkspace workspace;
    private final Path root;
    private final Mount mount;
    private final String image;

    private DockerGitCheckout(
        final DockerClient docker,
        final DockerWorkspace workspace,
        final Path root,
        final Mount mount,
        final String image
    ) {
        this.docker = docker;
        this.workspace = workspace;
        this.root = root;
        this.mount = mount;
        this.image = image;
    }

    /**
     * Checks out into the named volume, which this process must see
     * mounted at the volume's mount path (for example because it runs in
     * a container itself).
     */
    public static DockerGitCheckout inVolume(
        final DockerClient docker,
        final DockerWorkspace.NamedVolume volume,
        final String image
    ) {
        return new DockerGitCheckout(
            docker,
            volume,
            volume.mountPath(),
            new Mount()
                .withType(MountType.VOLUME)
                .withSource(volume.name())
                .withTarget(volume.mountPath().toString()),
            image
        );
    }

    /**
     * Checks out into a directory on the Docker host. Only works when this
     * process runs directly on the Docker host.
     */
    public static DockerGitCheckout inHostDirectory(
        final DockerClient docker,
        final Path directory,
        final String image
    ) {
        if (directory == null || !directory.isAbsolute()) {
            throw new IllegalArgumentException(
                "Checkout directory must be absolute: " + directory
            );
        }

        final var root = directory.normalize();

        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to create checkout directory " + root,
                e
            );
        }

        return new DockerGitCheckout(
            docker,
            DockerWorkspace.hostDirectory(),
            root,
            new Mount()
                .withType(MountType.BIND)
                .withSource(root.toString())
                .withTarget(root.toString()),
            image
        );
    }

    /**
     * The workspace to give the {@link DockerSandbox} so the scanner
     * containers see the checkouts.
     */
    public DockerWorkspace workspace() {
        return workspace;
    }

    /**
     * Checks out the source into the workspace directory {@code name},
     * replacing whatever is there, and returns its path.
     */
    public Path checkout(
        final String name,
        final GitSource source,
        final ScanProcessListener listener
    ) {
        return checkout(name, source, listener, Map.of());
    }

    /**
     * As {@link #checkout(String, GitSource, ScanProcessListener)}, with
     * labels on the git container, so it can be found again, for example
     * to stop it.
     */
    public Path checkout(
        final String name,
        final GitSource source,
        final ScanProcessListener listener,
        final Map<String, String> labels
    ) {
        final var directory = directory(name);

        final var exitCode = run(
            listener,
            labels,
            sshFiles(source),
            "sh", "-c", SCRIPT.formatted(SSH_DIRECTORY), "checkout",
            directory.toString(),
            source.url(),
            orEmpty(source.branch()),
            orEmpty(source.revision())
        );

        if (exitCode != 0) {
            throw new IllegalStateException(
                "Checkout of " + source.url() + " failed with exit code " + exitCode
            );
        }

        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(
                "The checkout is not visible to this process at " + directory
                + "; is the workspace mounted here?"
            );
        }

        return directory;
    }

    /**
     * Removes the workspace directory {@code name}. The files belong to the
     * user of the git container, so they are removed from a container too.
     */
    public void remove(
        final String name,
        final ScanProcessListener listener
    ) {
        final var exitCode = run(
            listener,
            Map.of(),
            Map.of(),
            "rm", "-rf", directory(name).toString()
        );

        if (exitCode != 0) {
            throw new IllegalStateException(
                "Removing checkout " + name + " failed with exit code " + exitCode
            );
        }
    }

    private int run(
        final ScanProcessListener listener,
        final Map<String, String> labels,
        final Map<String, String> sshFiles,
        final String... command
    ) {
        String containerId = null;

        try {

            Containers.pullIfMissing(docker, image);

            containerId = docker
                .createContainerCmd(image)
                .withEntrypoint(command)
                .withLabels(labels)
                .withEnv("GIT_TERMINAL_PROMPT=0")
                .withHostConfig(
                    HostConfig.newHostConfig()
                        .withMounts(List.of(mount))
                )
                .exec()
                .getId();

            if (!sshFiles.isEmpty()) {
                docker.copyArchiveToContainerCmd(containerId)
                    .withTarInputStream(new ByteArrayInputStream(
                        ContainerArchive.privateFiles(SSH_DIRECTORY, sshFiles)
                    ))
                    .withRemotePath("/")
                    .exec();
            }

            return Containers.runAndForwardOutput(docker, containerId, listener);

        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to copy the SSH key to the git container",
                e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "Checkout was interrupted",
                e
            );
        } finally {
            if (containerId != null) {
                Containers.remove(docker, containerId);
            }
        }
    }

    private Path directory(final String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid checkout name: " + name);
        }

        return root.resolve(name);
    }

    private static String orEmpty(final String value) {
        return value == null ? "" : value;
    }

    /**
     * The files of {@link #SSH_DIRECTORY}, or none for a source without a
     * key.
     */
    private static Map<String, String> sshFiles(final GitSource source) {
        if (source.sshKey() == null) {
            return Map.of();
        }

        return Map.of(
            "id_key", withTrailingNewline(source.sshKey()),
            "known_hosts", withTrailingNewline(source.knownHosts())
        );
    }

    /**
     * OpenSSH refuses a key whose last line is not terminated.
     */
    private static String withTrailingNewline(final String key) {
        return key.endsWith("\n") ? key : key + "\n";
    }

    /**
     * @param url      the repository to clone
     * @param branch   the branch to check out, or {@code null} for the
     *                 default branch
     * @param revision the commit to check out, or {@code null} for the tip
     *                 of the branch
     * @param sshKey     the private key to fetch an SSH url with, or
     *                   {@code null} for a public repository; ignored for
     *                   other urls, such as https
     * @param knownHosts the servers' host keys, in the format of OpenSSH's
     *                   {@code known_hosts}; required with an SSH key and
     *                   an SSH url, as no other server is trusted
     */
    public record GitSource(
        String url,
        String branch,
        String revision,
        String sshKey,
        String knownHosts
    ) {

        public GitSource(
            final String url,
            final String branch,
            final String revision
        ) {
            this(url, branch, revision, null, null);
        }

        public GitSource {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("Git url is required");
            }

            branch = blankToNull(branch);
            revision = blankToNull(revision);
            sshKey = blankToNull(sshKey);
            knownHosts = blankToNull(knownHosts);

            // Only SSH uses them; a key configured for private repositories
            // leaves public ones over https working without known hosts
            if (!isSsh(url)) {
                sshKey = null;
                knownHosts = null;
            }

            if (sshKey != null && knownHosts == null) {
                throw new IllegalArgumentException(
                    "Known hosts are required with an SSH key, to verify the git server"
                );
            }

            // Keep values from being read as git options
            for (final var value : new String[] { url, branch, revision }) {
                if (value != null && value.startsWith("-")) {
                    throw new IllegalArgumentException("Invalid git source: " + value);
                }
            }
        }

        private static String blankToNull(final String value) {
            return value == null || value.isBlank() ? null : value;
        }

        /**
         * Whether git fetches the url over SSH: an {@code ssh://} url, or
         * the scp-like {@code [user@]host:path}, which has a colon before
         * any slash and no scheme.
         */
        static boolean isSsh(final String url) {
            final var scheme = url.indexOf("://");

            if (scheme >= 0) {
                final var name = url.substring(0, scheme);

                return name.equals("ssh") || name.equals("git+ssh") || name.equals("ssh+git");
            }

            final var colon = url.indexOf(':');
            final var slash = url.indexOf('/');

            return colon > 0 && (slash < 0 || colon < slash);
        }

        /**
         * Leaves the key out, so it does not end up in logs.
         */
        @Override
        public String toString() {
            return "GitSource[url=%s, branch=%s, revision=%s, sshKey=%s, knownHosts=%s]".formatted(
                url,
                branch,
                revision,
                sshKey == null ? null : "****",
                knownHosts
            );
        }
    }
}
