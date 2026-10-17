package dev.graphnous.github;

import dev.graphnous.application.scan.ScanCompletedEvent;
import dev.graphnous.application.scan.ScanFailedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reports on the pull requests' scans once they are done, off the thread
 * that ran them, and now and then on those whose event it missed.
 */
@Component
@ConditionalOnBooleanProperty("graphnous.github.enabled")
public class GitHubScanListener {

    private final GitHubApp app;

    public GitHubScanListener(final GitHubApp app) {
        this.app = app;
    }

    @Async
    @EventListener(ScanCompletedEvent.class)
    public void completed(final ScanCompletedEvent event) {
        app.completed(event.getScanId());
    }

    @Async
    @EventListener(ScanFailedEvent.class)
    public void failed(final ScanFailedEvent event) {
        app.failed(event.scanId(), event.reason());
    }

    /**
     * What the events missed, such as while the server was stopped.
     */
    @Scheduled(
        fixedDelayString = "${graphnous.github.sweep-interval:PT1M}",
        initialDelayString = "${graphnous.github.sweep-interval:PT1M}"
    )
    public void sweep() {
        app.sweep();
    }
}
