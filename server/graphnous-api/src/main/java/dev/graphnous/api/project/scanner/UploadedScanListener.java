package dev.graphnous.api.project.scanner;

import dev.graphnous.application.event.EventHandler;
import dev.graphnous.application.project.scanner.ProjectScanner;
import dev.graphnous.application.scan.UploadedScanEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class UploadedScanListener implements EventHandler<UploadedScanEvent> {

    private final ProjectScanner projectScanner;

    public UploadedScanListener(final ProjectScanner projectScanner) {
        this.projectScanner = projectScanner;
    }

    @EventListener(UploadedScanEvent.class)
    @Override
    @Async
    public void handle(final UploadedScanEvent event) {
        this.projectScanner.storeUploaded(event.getScanId(), event.getResults(), event.getContext());
    }
}
