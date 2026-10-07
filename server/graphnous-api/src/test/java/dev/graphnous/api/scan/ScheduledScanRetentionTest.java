package dev.graphnous.api.scan;

import dev.graphnous.application.notification.ScanNotifier;
import dev.graphnous.application.scan.ScanDeleter;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledScanRetentionTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanDeleter scanDeleter;

    @Mock
    private ScanNotifier scanNotifier;

    private final Project.ProjectId projectId = Project.ProjectId.generate();

    @Test
    void deletesTheScansOlderThanTheRetentionPeriod() {
        final var other = Project.ProjectId.generate();

        final var first = scan(projectId);
        final var second = scan(projectId);
        final var third = scan(other);

        when(scanRepository.findFinishedCreatedBefore(NOW.minus(Duration.ofDays(30))))
            .thenReturn(List.of(first, second, third));

        retention(30, -1).deleteExpired();

        verify(scanDeleter).deleteScan(first.id());
        verify(scanDeleter).deleteScan(second.id());
        verify(scanDeleter).deleteScan(third.id());

        // One notification for each project
        verify(scanNotifier).scansDeleted(projectId, 2, "they were older than 30 days");
        verify(scanNotifier).scansDeleted(other, 1, "they were older than 30 days");
    }

    @Test
    void keepsScansWithoutARetentionPeriod() {
        retention(-1, -1).deleteExpired();

        verifyNoInteractions(scanRepository, scanDeleter);
    }

    @Test
    void keepsDeletingWhenOneScanFails() {
        final var failing = scan(projectId);
        final var next = scan(projectId);

        when(scanRepository.findFinishedCreatedBefore(any())).thenReturn(List.of(failing, next));
        doThrow(new IllegalStateException("Graph unavailable")).when(scanDeleter).deleteScan(failing.id());

        retention(30, -1).deleteExpired();

        verify(scanDeleter).deleteScan(next.id());
        // Only the scan that was deleted
        verify(scanNotifier).scansDeleted(projectId, 1, "they were older than 30 days");
    }

    @Test
    void deletesTheOldestScansToMakeRoomForANewOne() {
        final var oldest = Scan.ScanId.generate();
        final var older = Scan.ScanId.generate();

        when(scanRepository.count(projectId)).thenReturn(6);
        // 6 scans and the new one, for a limit of 5
        when(scanRepository.findOldestFinished(projectId, 2)).thenReturn(List.of(oldest, older));

        retention(-1, 5).makeRoomFor(projectId);

        verify(scanDeleter).deleteScan(oldest);
        verify(scanDeleter).deleteScan(older);
        verify(scanNotifier).scansDeleted(projectId, 2, "a project keeps at most 5 scans");
    }

    @Test
    void deletesNothingWithRoomLeft() {
        when(scanRepository.count(projectId)).thenReturn(4);

        retention(-1, 5).makeRoomFor(projectId);

        verify(scanRepository, never()).findOldestFinished(any(), anyInt());
        verifyNoInteractions(scanDeleter, scanNotifier);
    }

    @Test
    void keepsAnyNumberOfScansWithoutALimit() {
        retention(-1, -1).makeRoomFor(projectId);

        verifyNoInteractions(scanRepository, scanDeleter);
    }

    @Test
    void refusesLimitsThatWouldDeleteEverything() {
        assertThatThrownBy(() -> retention(0, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("graphnous.scans.retention.days");

        assertThatThrownBy(() -> retention(-1, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("graphnous.scans.retention.max-per-project");
    }

    private static Scan scan(final Project.ProjectId projectId) {
        return new Scan(Scan.ScanId.generate(), projectId, Scan.ScanStatus.COMPLETED, null, NOW, NOW, null);
    }

    private ScheduledScanRetention retention(final int days, final int maxPerProject) {
        return new ScheduledScanRetention(
            scanRepository,
            scanDeleter,
            scanNotifier,
            days,
            maxPerProject,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
