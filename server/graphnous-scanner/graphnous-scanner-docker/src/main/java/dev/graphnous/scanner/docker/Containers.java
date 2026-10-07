package dev.graphnous.scanner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import dev.graphnous.scanner.listener.ScanProcessListener;

import java.util.Objects;

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
     * Starts the container, streams its output to the listener line by
     * line and returns the exit code once it has stopped.
     */
    static int runAndForwardOutput(
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

        final var exitCode = docker
            .waitContainerCmd(containerId)
            .exec(new WaitContainerResultCallback())
            .awaitStatusCode();

        logs.awaitCompletion();

        stdout.flush();
        stderr.flush();

        return exitCode;
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
