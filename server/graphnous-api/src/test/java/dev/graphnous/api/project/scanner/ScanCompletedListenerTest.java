package dev.graphnous.api.project.scanner;

import dev.graphnous.application.scan.ScanCompletedEvent;
import dev.graphnous.application.scan.stats.ScanStatService;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScanCompletedListenerTest {

    @Mock
    private ScanStatService scanStatService;

    @Test
    void countsTheStatsOfTheCompletedScan() {
        final var scanId = Scan.ScanId.generate();
        final var projectId = Project.ProjectId.generate();
        final var results = List.of(new ScanResult());

        new ScanCompletedListener(scanStatService).handle(new ScanCompletedEvent(scanId, projectId, results));

        verify(scanStatService).createOrUpdate(scanId, projectId, results);
    }
}
