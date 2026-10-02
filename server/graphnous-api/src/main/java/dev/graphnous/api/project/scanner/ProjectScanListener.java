package dev.graphnous.api.project.scanner;


import dev.graphnous.application.event.EventHandler;
import dev.graphnous.application.project.scanner.ProjectScanner;
import dev.graphnous.application.project.scanner.StartScanCommand;
import dev.graphnous.application.scan.StartScanEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ProjectScanListener implements EventHandler<StartScanEvent> {

    private final ProjectScanner projectScanner;

    public ProjectScanListener(
        final ProjectScanner projectScanner
    ) {
        this.projectScanner = projectScanner;
    }

    @EventListener(StartScanEvent.class)
    @Override
    @Async()
    public void handle(StartScanEvent event) {
        final var command = new StartScanCommand(
            event.getScanId()
        );

        this.projectScanner.startScan(command, event.getContext());
    }

}
