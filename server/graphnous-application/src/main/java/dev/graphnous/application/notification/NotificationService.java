package dev.graphnous.application.notification;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * Notifications of systems, projects and scans. A notification is only
 * accessible to whoever may read its system.
 */
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthorizationService authorizationService;

    private final SystemService systemService;
    private final ProjectService projectService;
    private final ScanService scanService;

    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public NotificationService(
        final NotificationRepository notificationRepository,
        final AuthorizationService authorizationService,
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.authorizationService = authorizationService;

        this.systemService = systemService;
        this.projectService = projectService;
        this.scanService = scanService;

        this.clock = clock;
    }

    /**
     * @throws ValidationException when the notification has no content, is
     *                             about a scan without its project, or its
     *                             project or scan is not of its system
     */
    public Notification create(
        final RequestContext context,
        final CreateNotificationCommand command
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_CREATE);

        log.info(
            "Creating notification organizationId={} systemId={} projectId={} scanId={}",
            context.organization().id(),
            command.systemId(),
            command.projectId(),
            command.scanId()
        );

        if (command.systemId() == null) {
            throw new ValidationException("A notification needs a system");
        }

        // Only for a system of the caller's organization
        this.systemService.getSystem(context, command.systemId());

        requireOfSystem(context, command);

        final Notification notification;

        try {
            notification = new Notification(
                Notification.NotificationId.generate(),
                command.systemId(),
                command.projectId(),
                command.scanId(),
                command.title(),
                command.content(),
                false,
                this.clock.instant()
            );
        } catch (final IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }

        final var saved = this.notificationRepository.save(notification);

        log.info(
            "Created notification organizationId={} notificationId={}",
            context.organization().id(),
            saved.id().id()
        );

        return saved;
    }

    public Notification getNotification(
        final RequestContext context,
        final Notification.NotificationId id
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);

        log.debug(
            "Getting notification organizationId={} id={}",
            context.organization().id(),
            id.id()
        );

        final var notification = this.notificationRepository.findById(id);

        // Only a notification of a system of the caller's organization
        this.systemService.getSystem(context, notification.systemId());

        return notification;
    }

    /**
     * The notifications of the system, its projects and their scans.
     */
    public Page<Notification> getNotifications(
        final RequestContext context,
        final PageQuery pageQuery,
        final System.SystemId systemId
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);

        this.systemService.getSystem(context, systemId);

        return this.notificationRepository.findAll(systemId, pageQuery);
    }

    /**
     * The notifications of the project and its scans.
     */
    public Page<Notification> getNotifications(
        final RequestContext context,
        final PageQuery pageQuery,
        final Project.ProjectId projectId
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);

        this.projectService.getProject(context, projectId);

        return this.notificationRepository.findAll(projectId, pageQuery);
    }

    public Page<Notification> getNotifications(
        final RequestContext context,
        final PageQuery pageQuery,
        final Scan.ScanId scanId
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);

        this.scanService.getScan(context, scanId);

        return this.notificationRepository.findAll(scanId, pageQuery);
    }

    /**
     * Marks the notification read or unread.
     */
    public Notification markRead(
        final RequestContext context,
        final Notification.NotificationId id,
        final boolean read
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_UPDATE);

        final var notification = this.getNotification(context, id);

        if (notification.read() == read) {
            return notification;
        }

        log.debug(
            "Marking notification organizationId={} id={} read={}",
            context.organization().id(),
            id.id(),
            read
        );

        return this.notificationRepository.save(notification.withRead(read));
    }

    public void delete(
        final RequestContext context,
        final Notification.NotificationId id
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_DELETE);

        log.info(
            "Deleting notification organizationId={} id={}",
            context.organization().id(),
            id.id()
        );

        this.getNotification(context, id);

        this.notificationRepository.delete(id);
    }

    /**
     * The project must be of the system, and the scan of the project.
     */
    private void requireOfSystem(
        final RequestContext context,
        final CreateNotificationCommand command
    ) {
        if (command.scanId() != null && command.projectId() == null) {
            throw new ValidationException("A notification about a scan needs the scan's project");
        }

        if (command.projectId() != null) {
            final var project = this.projectService.getProject(context, command.projectId());

            if (!project.systemId().equals(command.systemId())) {
                throw new ValidationException(
                    "Project %s is not of system %s".formatted(command.projectId().id(), command.systemId().id())
                );
            }
        }

        if (command.scanId() != null) {
            final var scan = this.scanService.getScan(context, command.scanId());

            if (!scan.projectId().equals(command.projectId())) {
                throw new ValidationException(
                    "Scan %s is not of project %s".formatted(command.scanId().id(), command.projectId().id())
                );
            }
        }
    }
}
