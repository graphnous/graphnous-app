package dev.graphnous.application.notification;

import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanNotifierTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ProjectRepository projectRepository;

    private final Project project = new Project(
        Project.ProjectId.generate(),
        "backend",
        null,
        "https://example.com/backend.git",
        null,
        System.SystemId.generate(),
        NOW,
        NOW
    );

    private final Scan scan = new Scan(
        Scan.ScanId.generate(),
        project.id(),
        Scan.ScanStatus.RUNNING,
        new Scan.SourceRevision("abc123", "main"),
        NOW,
        NOW,
        null
    );

    @BeforeEach
    void setUp() {
        lenient().when(projectRepository.findById(project.id())).thenReturn(project);
    }

    @Test
    void notifiesOfAStartedScan() {
        notifier().scanStarted(scan);

        final var notification = saved();

        assertThat(notification.systemId()).isEqualTo(project.systemId());
        assertThat(notification.projectId()).isEqualTo(project.id());
        assertThat(notification.scanId()).isEqualTo(scan.id());
        assertThat(notification.title()).isEqualTo("Scan started");
        assertThat(notification.content()).isEqualTo("The scan of backend (branch main, revision abc123) started.");
        assertThat(notification.read()).isFalse();
        assertThat(notification.createdAt()).isEqualTo(NOW);
    }

    @Test
    void notifiesOfACompletedScan() {
        notifier().scanCompleted(scan);

        assertThat(saved().title()).isEqualTo("Scan completed");
    }

    @Test
    void notifiesOfAFailedScanWithWhy() {
        notifier().scanFailed(scan, "Repository not found");

        final var notification = saved();

        assertThat(notification.title()).isEqualTo("Scan failed");
        assertThat(notification.content()).isEqualTo(
            "The scan of backend (branch main, revision abc123) failed: Repository not found"
        );
    }

    @Test
    void notifiesTheProjectOfDeletedScans() {
        notifier().scansDeleted(project.id(), 3, "they were older than 30 days");

        final var notification = saved();

        assertThat(notification.projectId()).isEqualTo(project.id());
        // The scans are gone, so it is not about one of them
        assertThat(notification.scanId()).isNull();
        assertThat(notification.title()).isEqualTo("Scans deleted");
        assertThat(notification.content()).isEqualTo("Deleted 3 scans of backend, as they were older than 30 days.");
    }

    @Test
    void doesNotNotifyWhenNoScansWereDeleted() {
        notifier().scansDeleted(project.id(), 0, "they were older than 30 days");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void leavesTheScanAloneWhenTheNotificationCannotBeStored() {
        when(notificationRepository.save(any())).thenThrow(new IllegalStateException("Database unavailable"));

        assertThatCode(() -> notifier().scanFailed(scan, "Scanner crashed")).doesNotThrowAnyException();
    }

    @Test
    void leavesOutTheNotificationOfAProjectThatIsGone() {
        when(projectRepository.findById(project.id())).thenThrow(new NotFoundException("Not found"));

        assertThatCode(() -> notifier().scanCompleted(scan)).doesNotThrowAnyException();

        verify(notificationRepository, never()).save(any());
    }

    private Notification saved() {
        final var notification = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(notification.capture());

        return notification.getValue();
    }

    private ScanNotifier notifier() {
        return new ScanNotifier(notificationRepository, projectRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
