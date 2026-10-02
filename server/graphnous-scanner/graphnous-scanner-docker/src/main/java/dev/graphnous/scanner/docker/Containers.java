package dev.graphnous.scanner.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import dev.graphnous.scanner.listener.ScanProcessListener;

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
        try {
            docker.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            docker.pullImageCmd(image)
                .exec(new PullImageResultCallback())
                .awaitCompletion();
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
