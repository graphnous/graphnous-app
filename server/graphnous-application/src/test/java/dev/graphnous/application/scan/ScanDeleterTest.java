package dev.graphnous.application.scan;

import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.application.scan.stats.ScanStatRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ScanDeleterTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanLogRepository scanLogRepository;

    @Mock
    private ScanResultRepository scanResultRepository;

    @Mock
    private ScanStepRepository scanStepRepository;

    @Mock
    private ScanStatRepository scanStatRepository;

    private final Project.ProjectId projectId = Project.ProjectId.generate();

    @Test
    void deletesEachScanOfTheProjectWithItsResultsAndLogs() {
        final var first = Scan.ScanId.generate();
        final var second = Scan.ScanId.generate();

        when(scanRepository.findIdsByProjectId(projectId)).thenReturn(List.of(first, second));

        deleter().deleteScans(projectId);

        final InOrder order = inOrder(scanResultRepository, scanLogRepository, scanStepRepository, scanStatRepository, scanRepository);

        for (final var scanId : List.of(first, second)) {
            order.verify(scanResultRepository).delete(scanId);
            order.verify(scanLogRepository).deleteByScanId(scanId);
            order.verify(scanStepRepository).deleteByScanId(scanId);
            order.verify(scanStatRepository).deleteByScanId(scanId);

            // Last, so a delete that fails halfway still finds the scan
            order.verify(scanRepository).delete(scanId);
        }
    }

    @Test
    void deletesASingleScanWithItsResultsAndLogs() {
        final var scanId = Scan.ScanId.generate();

        deleter().deleteScan(scanId);

        final InOrder order = inOrder(scanResultRepository, scanLogRepository, scanStepRepository, scanStatRepository, scanRepository);

        order.verify(scanResultRepository).delete(scanId);
        order.verify(scanLogRepository).deleteByScanId(scanId);
        order.verify(scanStepRepository).deleteByScanId(scanId);
        order.verify(scanStatRepository).deleteByScanId(scanId);

        order.verify(scanRepository).delete(scanId);
    }

    @Test
    void refusesAProjectWithActiveScans() {
        when(scanRepository.hasActiveScans(projectId)).thenReturn(true);

        assertThrows(ConflictException.class, () -> deleter().requireNoActiveScans(projectId));
    }

    @Test
    void allowsAProjectWithoutActiveScans() {
        when(scanRepository.hasActiveScans(projectId)).thenReturn(false);

        assertDoesNotThrow(() -> deleter().requireNoActiveScans(projectId));
    }

    @Test
    void doesNothingForAProjectWithoutScans() {
        when(scanRepository.findIdsByProjectId(projectId)).thenReturn(List.of());

        deleter().deleteScans(projectId);

        verify(scanRepository, never()).delete(any());
        verify(scanResultRepository, never()).delete(any());
    }

    private ScanDeleter deleter() {
        return new ScanDeleter(scanRepository, scanLogRepository, scanResultRepository, scanStepRepository, scanStatRepository);
    }
}
