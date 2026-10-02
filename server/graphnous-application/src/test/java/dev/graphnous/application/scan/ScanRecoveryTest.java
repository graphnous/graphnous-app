package dev.graphnous.application.scan;

import dev.graphnous.application.project.scanner.ScanCanceller;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanRecoveryTest {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final Duration TIMEOUT = Duration.ofHours(1);

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanLogService scanLogService;

    @Mock
    private ScanCanceller scanCanceller;

    @Mock
    private ScanSteps scanSteps;

    @Test
    void failsEveryActiveScanLeftByAPreviousRun() {
        final var pending = scan(Scan.ScanStatus.PENDING, NOW);
        final var running = scan(Scan.ScanStatus.RUNNING, NOW);

        when(scanRepository.findActive()).thenReturn(List.of(pending, running));

        recovery().failInterrupted();

        final var saved = savedScans(2);

        assertThat(saved).extracting(Scan::id).containsExactly(pending.id(), running.id());
        assertThat(saved).allSatisfy(scan -> {
            assertThat(scan.status()).isEqualTo(Scan.ScanStatus.FAILED);
            assertThat(scan.updatedAt()).isEqualTo(NOW);
        });
        verify(scanLogService).log(pending.id(), ScanLogLevel.ERROR, "Scan interrupted by a server restart");
        // The step that was running is failed with the same reason
        verify(scanSteps).failRunning(running.id(), "Scan interrupted by a server restart");
        verify(scanLogService).log(running.id(), ScanLogLevel.ERROR, "Scan interrupted by a server restart");
    }

    @Test
    void failsOnlyScansWithoutProgressWithinTheTimeout() {
        final var stuck = scan(Scan.ScanStatus.RUNNING, NOW.minus(TIMEOUT).minusSeconds(1));
        final var recent = scan(Scan.ScanStatus.RUNNING, NOW.minus(TIMEOUT).plusSeconds(1));

        when(scanRepository.findActive()).thenReturn(List.of(stuck, recent));

        recovery().failTimedOut();

        assertThat(savedScans(1)).singleElement().satisfies(scan -> {
            assertThat(scan.id()).isEqualTo(stuck.id());
            assertThat(scan.status()).isEqualTo(Scan.ScanStatus.FAILED);
            // Everything else about the scan is kept
            assertThat(scan.projectId()).isEqualTo(stuck.projectId());
            assertThat(scan.revision()).isEqualTo(stuck.revision());
            assertThat(scan.createdAt()).isEqualTo(stuck.createdAt());
        });
        verify(scanLogService).log(stuck.id(), ScanLogLevel.ERROR, "Scan timed out: no progress in PT1H");
        verify(scanSteps).failRunning(stuck.id(), "Scan timed out: no progress in PT1H");
        verify(scanSteps, never()).failRunning(eq(recent.id()), anyString());
        verify(scanLogService, never()).log(eq(recent.id()), any(), anyString());
    }

    @Test
    void keepsFailingTheOtherScansWhenOneCannotBeSaved() {
        final var broken = scan(Scan.ScanStatus.RUNNING, NOW);
        final var other = scan(Scan.ScanStatus.QUEUED, NOW);

        when(scanRepository.findActive()).thenReturn(List.of(broken, other));
        doThrow(new IllegalStateException("Database unavailable"))
            .when(scanRepository).save(argThat(scan -> scan.id().equals(broken.id())));

        recovery().failInterrupted();

        verify(scanRepository).save(argThat(scan -> scan.id().equals(other.id())));
        verify(scanLogService).log(eq(other.id()), eq(ScanLogLevel.ERROR), anyString());
        // Not logged as failed, as it was not
        verify(scanLogService, never()).log(eq(broken.id()), any(), anyString());
    }

    @Test
    void failsTheScanEvenWhenItsLogCannotBeWritten() {
        final var scan = scan(Scan.ScanStatus.RUNNING, NOW);

        when(scanRepository.findActive()).thenReturn(List.of(scan));
        when(scanLogService.log(any(), any(), anyString())).thenThrow(new IllegalStateException("Log unavailable"));

        recovery().failInterrupted();

        assertThat(savedScans(1)).singleElement()
            .extracting(Scan::status)
            .isEqualTo(Scan.ScanStatus.FAILED);
    }

    @Test
    void stopsWhatAPreviousRunLeftBehindEvenWithoutActiveScans() {
        when(scanRepository.findActive()).thenReturn(List.of());

        recovery().failInterrupted();

        verify(scanCanceller).cancelAll();
    }

    @Test
    void startsUpWhenStoppingWhatAPreviousRunLeftBehindFails() {
        when(scanRepository.findActive()).thenReturn(List.of());
        doThrow(new IllegalStateException("Docker unavailable")).when(scanCanceller).cancelAll();

        recovery().failInterrupted();
    }

    @Test
    void stopsTheWorkOfATimedOutScanAfterFailingIt() {
        final var stuck = scan(Scan.ScanStatus.RUNNING, NOW.minus(TIMEOUT).minusSeconds(1));
        final var recent = scan(Scan.ScanStatus.RUNNING, NOW);

        when(scanRepository.findActive()).thenReturn(List.of(stuck, recent));

        recovery().failTimedOut();

        // Failed first, so the scan's thread sees the status once its work stops
        final InOrder order = inOrder(scanRepository, scanCanceller);
        order.verify(scanRepository).save(argThat(scan -> scan.id().equals(stuck.id())));
        order.verify(scanCanceller).cancel(stuck.id());

        verify(scanCanceller, never()).cancel(recent.id());
    }

    @Test
    void leavesTheWorkOfAScanThatCouldNotBeFailed() {
        final var stuck = scan(Scan.ScanStatus.RUNNING, NOW.minus(TIMEOUT).minusSeconds(1));

        when(scanRepository.findActive()).thenReturn(List.of(stuck));
        doThrow(new IllegalStateException("Database unavailable")).when(scanRepository).save(any());

        recovery().failTimedOut();

        verify(scanCanceller, never()).cancel(any());
    }

    private List<Scan> savedScans(final int count) {
        final var saved = ArgumentCaptor.forClass(Scan.class);

        verify(scanRepository, times(count)).save(saved.capture());

        return saved.getAllValues();
    }

    private static Scan scan(
        final Scan.ScanStatus status,
        final Instant updatedAt
    ) {
        return new Scan(
            Scan.ScanId.generate(),
            Project.ProjectId.generate(),
            status,
            new Scan.SourceRevision("abc123", "main"),
            updatedAt.minus(Duration.ofMinutes(5)),
            updatedAt,
            null
        );
    }

    private ScanRecovery recovery() {
        return new ScanRecovery(
            scanRepository,
            scanLogService,
            scanCanceller,
            scanSteps,
            TIMEOUT,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
