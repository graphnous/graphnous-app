package dev.graphnous.api.project.scanner;

import dev.graphnous.application.event.EventHandler;
import dev.graphnous.application.scan.ScanCompletedEvent;
import dev.graphnous.application.scan.stats.ScanStatService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Counts the stats of every scan that completes.
 */
@Component
public class ScanCompletedListener implements EventHandler<ScanCompletedEvent> {

    private final ScanStatService scanStatService;

    public ScanCompletedListener(final ScanStatService scanStatService) {
        this.scanStatService = scanStatService;
    }

    @EventListener(ScanCompletedEvent.class)
    @Override
    @Async
    public void handle(final ScanCompletedEvent event) {
        this.scanStatService.createOrUpdate(
            event.getScanId(),
            event.getProjectId(),
            event.getResults()
        );
    }
}
