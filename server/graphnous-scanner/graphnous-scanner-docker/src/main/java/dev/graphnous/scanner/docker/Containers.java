package dev.graphnous.scanner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import dev.graphnous.scanner.listener.ScanProcessListener;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Container steps shared by the scanner sandbox and the git checkout.
 */
final class Containers {

    private Containers() {
    }

    static void pullIfMissing(
        final DockerClient docker,
        final String image
    ) throws InterruptedException {
        Containers.pullIfMissing(docker, image, null);
    }

    /**
     * Pulls the image for the platform, such as {@code linux/amd64}, unless
     * it is there for that platform already; without a platform, unless it
     * is there at all. An image that is there for another platform, such as
     * linux/arm64 on Apple silicon, is pulled again, as Docker refuses to
     * create a container of it for the requested one.
     */
    static void pullIfMissing(
        final DockerClient docker,
        final String image,
        final String platform
    ) throws InterruptedException {
        try {
            final var local = docker.inspectImageCmd(image).exec();

            if (Objects.isNull(platform) || isFor(local, platform)) {
                return;
            }
        } catch (NotFoundException e) {
            // Pulled below
        }

        final var cmd = docker.pullImageCmd(image);

        if (Objects.nonNull(platform)) {
            cmd.withPlatform(platform);
        }

        cmd
            .exec(new PullImageResultCallback())
            .awaitCompletion();
    }

    /**
     * Whether the image is for the platform's OS and architecture; a
     * variant, such as the v8 of linux/arm64/v8, is not compared.
     */
    static boolean isFor(
        final InspectImageResponse image,
        final String platform
    ) {
        final var parts = platform.split("/");

        return parts.length >= 2
            && parts[0].equalsIgnoreCase(image.getOs())
            && parts[1].equalsIgnoreCase(image.getArch());
    }

    /**
     * How often a running container is looked at.
     */
    private static final Duration POLL = Duration.ofSeconds(1);

    /**
     * How long the Docker daemon may not answer before the container is
     * given up on.
     */
    static final Duration UNREACHABLE = Duration.ofSeconds(30);

    /**
     * How long the rest of a stopped container's output may take to arrive.
     */
    private static final Duration DRAIN = Duration.ofSeconds(10);

    /**
     * How a container stopped.
     *
     * @param code      its exit code
     * @param oomKilled whether Docker killed it for using more memory than
     *                  it may have
     */
    record Exit(int code, boolean oomKilled) {
    }

    /**
     * The container was removed while it ran, such as by a cancelled scan
     * or from outside.
     */
    static final class RemovedException extends IllegalStateException {

        RemovedException(final Throwable cause) {
            super("The container was removed while it ran", cause);
        }
    }

    /**
     * Starts the container, streams its output to the listener line by
     * line and returns the exit code once it has stopped.
     */
    static int runAndForwardOutput(
        final DockerClient docker,
        final String containerId,
        final ScanProcessListener listener
    ) throws InterruptedException {
        return run(docker, containerId, listener).code();
    }

    /**
     * Starts the container, streams its output to the listener line by
     * line and returns how it stopped.
     * <p>
     * The container is looked at every second rather than waited for, so
     * nothing waits forever on it: one that is removed while it runs, such
     * as by a cancelled scan or from outside, ends the run at once, and so
     * does a Docker daemon that does not answer for {@link #UNREACHABLE}.
     *
     * @throws RemovedException     when the container was removed while
     *                               it ran
     * @throws IllegalStateException when the daemon stopped answering
     */
    static Exit run(
        final DockerClient docker,
        final String containerId,
        final ScanProcessListener listener
    ) throws InterruptedException {
        docker.startContainerCmd(containerId).exec();

        final var stdout = new LineSplitter(listener::stdout);
        final var stderr = new LineSplitter(listener::stderr);

        final var logs = docker
            .logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withFollowStream(true)
            .exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(final Frame frame) {
                    if (frame.getStreamType() == StreamType.STDERR) {
                        stderr.accept(frame.getPayload());
                    } else {
                        stdout.accept(frame.getPayload());
                    }
                }
            });

        try {
            final var state = awaitStopped(docker, containerId);

            // Its output ends with it, unless the stream was cut off
            logs.awaitCompletion(DRAIN.toMillis(), TimeUnit.MILLISECONDS);

            return new Exit(
                state.getExitCodeLong() == null ? -1 : state.getExitCodeLong().intValue(),
                Boolean.TRUE.equals(state.getOOMKilled())
            );
        } finally {
            closeQuietly(logs);

            stdout.flush();
            stderr.flush();
        }
    }

    /**
     * The state of the container once it has stopped.
     */
    private static InspectContainerResponse.ContainerState awaitStopped(
        final DockerClient docker,
        final String containerId
    ) throws InterruptedException {
        Instant unreachableSince = null;

        while (true) {
            try {
                final var state = docker.inspectContainerCmd(containerId).exec().getState();
                unreachableSince = null;

                if (state != null && isStopped(state)) {
                    return state;
                }
            } catch (final NotFoundException e) {
                throw new RemovedException(e);
            } catch (final RuntimeException e) {
                if (unreachableSince == null) {
                    unreachableSince = Instant.now();
                } else if (Duration.between(unreachableSince, Instant.now()).compareTo(UNREACHABLE) > 0) {
                    throw new IllegalStateException(
                        "Docker did not answer for " + UNREACHABLE.toSeconds() + " seconds while the container ran: "
                        + e.getMessage(),
                        e
                    );
                }
            }

            Thread.sleep(POLL.toMillis());
        }
    }

    private static boolean isStopped(final InspectContainerResponse.ContainerState state) {
        return "exited".equals(state.getStatus()) || "dead".equals(state.getStatus());
    }

    private static void closeQuietly(final Closeable closeable) {
        try {
            closeable.close();
        } catch (final IOException | RuntimeException ignored) {
            // Only frees the stream
        }
    }

    static void remove(
        final DockerClient docker,
        final String containerId
    ) {
        try {
            docker.removeContainerCmd(containerId)
                .withForce(true)
                .exec();
        } catch (NotFoundException ignored) {
            // already gone
        }
    }
}
