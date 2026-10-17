package dev.graphnous.application.notification;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private SystemService systemService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ScanService scanService;

    @Mock
    private NotificationPublisher publisher;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final System.SystemId systemId = System.SystemId.generate();
    private final Project.ProjectId projectId = Project.ProjectId.generate();
    private final Scan.ScanId scanId = Scan.ScanId.generate();

    @Test
    void createsAnUnreadNotificationOfASystem() {
        when(notificationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        final var notification = service().create(
            context,
            new CreateNotificationCommand(systemId, null, null, "Scan failed", "The checkout failed")
        );

        assertThat(notification.systemId()).isEqualTo(systemId);
        assertThat(notification.title()).isEqualTo("Scan failed");
        assertThat(notification.content()).isEqualTo("The checkout failed");
        assertThat(notification.read()).isFalse();
        assertThat(notification.createdAt()).isEqualTo(NOW);

        verify(authorizationService).authorize(context, Permission.NOTIFICATION_CREATE);
        verify(systemService).getSystem(context, systemId);
    }

    @Test
    void createsANotificationOfAScanOfItsProject() {
        when(projectService.getProject(context, projectId)).thenReturn(project(systemId));
        when(scanService.getScan(context, scanId)).thenReturn(scan(projectId));
        when(notificationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        final var notification = service().create(
            context,
            new CreateNotificationCommand(systemId, projectId, scanId, null, "Done")
        );

        assertThat(notification.projectId()).isEqualTo(projectId);
        assertThat(notification.scanId()).isEqualTo(scanId);
        assertThat(notification.title()).isNull();
    }

    @Test
    void refusesAScanWithoutItsProject() {
        assertThatThrownBy(() -> service().create(
            context,
            new CreateNotificationCommand(systemId, null, scanId, null, "Done")
        ))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("project");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void refusesAProjectOfAnotherSystem() {
        when(projectService.getProject(context, projectId)).thenReturn(project(System.SystemId.generate()));

        assertThatThrownBy(() -> service().create(
            context,
            new CreateNotificationCommand(systemId, projectId, null, null, "Done")
        ))
            .isInstanceOf(ValidationException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void refusesAScanOfAnotherProject() {
        when(projectService.getProject(context, projectId)).thenReturn(project(systemId));
        when(scanService.getScan(context, scanId)).thenReturn(scan(Project.ProjectId.generate()));

        assertThatThrownBy(() -> service().create(
            context,
            new CreateNotificationCommand(systemId, projectId, scanId, null, "Done")
        ))
            .isInstanceOf(ValidationException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void refusesANotificationWithoutContent() {
        assertThatThrownBy(() -> service().create(
            context,
            new CreateNotificationCommand(systemId, null, null, "Title", "")
        ))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("content");
    }

    @Test
    void refusesToCreateWithoutPermission() {
        doThrow(new AuthorizationException("Not allowed"))
            .when(authorizationService).authorize(context, Permission.NOTIFICATION_CREATE);

        assertThatThrownBy(() -> service().create(
            context,
            new CreateNotificationCommand(systemId, null, null, null, "Content")
        ))
            .isInstanceOf(AuthorizationException.class);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void marksANotificationRead() {
        final var notification = notification();

        when(notificationRepository.findById(notification.id(), context.user())).thenReturn(notification);

        final var read = service().markRead(context, notification.id(), true);

        assertThat(read.read()).isTrue();
        // For the caller alone
        verify(notificationRepository).markRead(notification.id(), context.user(), true);
        verify(notificationRepository, never()).save(any());
        verify(publisher).publish(NotificationEvent.updated(read, context.user()));

        verify(authorizationService).authorize(context, Permission.NOTIFICATION_UPDATE);
        // Checks the notification's system is accessible to the caller
        verify(systemService).getSystem(context, systemId);
    }

    @Test
    void doesNotSaveANotificationThatIsAlreadyUnread() {
        final var notification = notification();

        when(notificationRepository.findById(notification.id(), context.user())).thenReturn(notification);

        assertThat(service().markRead(context, notification.id(), false)).isEqualTo(notification);

        verify(notificationRepository, never()).markRead(any(), any(), anyBoolean());
        verifyNoInteractions(publisher);
    }

    @Test
    void deletesANotificationOfAnAccessibleSystemOnly() {
        final var notification = notification();

        when(notificationRepository.findById(notification.id(), context.user())).thenReturn(notification);
        when(systemService.getSystem(context, systemId)).thenThrow(new NotFoundException("Not found"));

        assertThatThrownBy(() -> service().delete(context, notification.id()))
            .isInstanceOf(NotFoundException.class);

        verify(notificationRepository, never()).delete(any());
    }

    @Test
    void deletesANotification() {
        final var notification = notification();

        when(notificationRepository.findById(notification.id(), context.user())).thenReturn(notification);

        service().delete(context, notification.id());

        verify(authorizationService).authorize(context, Permission.NOTIFICATION_DELETE);
        verify(notificationRepository).delete(notification.id());
        verify(publisher).publish(NotificationEvent.deleted(notification));
    }

    @Test
    void tellsOfANotificationCreated() {
        when(notificationRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        final var created = service().create(context, new CreateNotificationCommand(systemId, null, null, "Title", "Content"));

        verify(publisher).publish(NotificationEvent.created(created));
    }

    @Test
    void listsAndCountsTheNotificationsTheCallerHasNotRead() {
        final var page = new Page<>(List.of(notification()), 0, 20, 1, 1);
        final var query = new PageQuery(0, 20, new Sort("createdAt", Sort.Direction.DESC));

        when(notificationRepository.findUnread(systemId, context.user(), query)).thenReturn(page);
        when(notificationRepository.findAll(systemId, context.user(), query)).thenReturn(page);
        when(notificationRepository.countUnread(systemId, context.user())).thenReturn(3L);

        assertThat(service().getNotifications(context, query, systemId, true)).isSameAs(page);
        assertThat(service().getNotifications(context, query, systemId, false)).isSameAs(page);
        assertThat(service().countUnread(context, systemId)).isEqualTo(3L);

        verify(systemService, org.mockito.Mockito.times(3)).getSystem(context, systemId);
    }

    @Test
    void marksEveryNotificationReadForTheCaller() {
        when(notificationRepository.markAllRead(systemId, context.user())).thenReturn(2, 0);

        assertThat(service().markAllRead(context, systemId)).isEqualTo(2);
        // Nothing to tell when none was unread
        assertThat(service().markAllRead(context, systemId)).isZero();

        verify(authorizationService, org.mockito.Mockito.times(2)).authorize(context, Permission.NOTIFICATION_UPDATE);
        verify(publisher, org.mockito.Mockito.times(1)).publish(NotificationEvent.allRead(systemId, context.user()));
    }

    @Test
    void tellsOnlyTheReaderOfTheirReading() {
        final var other = new UserContext(UUID.randomUUID());

        assertThat(NotificationEvent.allRead(systemId, context.user()).isFor(context.user())).isTrue();
        assertThat(NotificationEvent.allRead(systemId, context.user()).isFor(other)).isFalse();
        assertThat(NotificationEvent.created(notification()).isFor(other)).isTrue();
        // Without a user, as without security, for no user only
        assertThat(NotificationEvent.allRead(systemId, new UserContext(null)).isFor(new UserContext(null))).isTrue();
        assertThat(NotificationEvent.allRead(systemId, new UserContext(null)).isFor(other)).isFalse();
    }

    private Notification notification() {
        return new Notification(
            Notification.NotificationId.generate(),
            systemId,
            null,
            null,
            "Title",
            "Content",
            false,
            NOW
        );
    }

    private Project project(final System.SystemId systemId) {
        return new Project(projectId, "backend", null, "https://example.com/repo.git", null, systemId, NOW, NOW);
    }

    private Scan scan(final Project.ProjectId projectId) {
        return new Scan(scanId, projectId, Scan.ScanStatus.COMPLETED, new Scan.SourceRevision(null, "main"), NOW, NOW, null);
    }

    private NotificationService service() {
        return new NotificationService(
            notificationRepository,
            authorizationService,
            systemService,
            projectService,
            scanService,
            publisher,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
