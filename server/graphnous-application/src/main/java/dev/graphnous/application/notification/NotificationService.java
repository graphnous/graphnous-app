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
 * accessible to whoever may read its system, and is read or unread for the
 * user of the request context: each user marks it for themselves, and
 * without a user, as without security or for an API key, it is marked for
 * no user.
 */
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthorizationService authorizationService;

    private final SystemService systemService;
    private final ProjectService projectService;
    private final ScanService scanService;

    private final NotificationPublisher publisher;
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
        this(notificationRepository, authorizationService, systemService, projectService, scanService, NotificationPublisher.NONE, clock);
    }

    /**
     * @param publisher who to tell of each notification created, marked or
     *                  deleted
     */
    public NotificationService(
        final NotificationRepository notificationRepository,
        final AuthorizationService authorizationService,
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final NotificationPublisher publisher,
        final Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.authorizationService = authorizationService;

        this.systemService = systemService;
        this.projectService = projectService;
        this.scanService = scanService;

        this.publisher = publisher;
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
        this.publisher.publish(NotificationEvent.created(saved));

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

        final var notification = this.notificationRepository.findById(id, context.user());

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

        return this.notificationRepository.findAll(systemId, context.user(), pageQuery);
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

        return this.notificationRepository.findAll(projectId, context.user(), pageQuery);
    }

    public Page<Notification> getNotifications(
        final RequestContext context,
        final PageQuery pageQuery,
        final Scan.ScanId scanId
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);

        this.scanService.getScan(context, scanId);

        return this.notificationRepository.findAll(scanId, context.user(), pageQuery);
    }

    /**
     * Marks the notification read or unread for the user of the context.
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

        this.notificationRepository.markRead(id, context.user(), read);

        final var marked = notification.withRead(read);
        this.publisher.publish(NotificationEvent.updated(marked, context.user()));

        return marked;
    }

    /**
     * The notifications of the system, its projects and their scans; only
     * the unread ones when asked.
     */
    public Page<Notification> getNotifications(
        final RequestContext context,
        final PageQuery pageQuery,
        final System.SystemId systemId,
        final boolean unreadOnly
    ) {
        if (!unreadOnly) {
            return getNotifications(context, pageQuery, systemId);
        }

        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);
        this.systemService.getSystem(context, systemId);

        return this.notificationRepository.findUnread(systemId, context.user(), pageQuery);
    }

    /**
     * The notifications of the project and its scans; only the unread ones
     * when asked.
     */
    public Page<Notification> getNotifications(
        final RequestContext context,
        final PageQuery pageQuery,
        final Project.ProjectId projectId,
        final boolean unreadOnly
    ) {
        if (!unreadOnly) {
            return getNotifications(context, pageQuery, projectId);
        }

        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);
        this.projectService.getProject(context, projectId);

        return this.notificationRepository.findUnread(projectId, context.user(), pageQuery);
    }

    /**
     * The notifications of the scan; only the unread ones when asked.
     */
    public Page<Notification> getNotifications(
        final RequestContext context,
        final PageQuery pageQuery,
        final Scan.ScanId scanId,
        final boolean unreadOnly
    ) {
        if (!unreadOnly) {
            return getNotifications(context, pageQuery, scanId);
        }

        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);
        this.scanService.getScan(context, scanId);

        return this.notificationRepository.findUnread(scanId, context.user(), pageQuery);
    }

    /**
     * How many notifications of the system, its projects and their scans
     * are unread.
     */
    public long countUnread(
        final RequestContext context,
        final System.SystemId systemId
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);
        this.systemService.getSystem(context, systemId);

        return this.notificationRepository.countUnread(systemId, context.user());
    }

    /**
     * Marks every notification of the system, its projects and their scans
     * read.
     *
     * @return how many were unread
     */
    public int markAllRead(
        final RequestContext context,
        final System.SystemId systemId
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_UPDATE);
        this.systemService.getSystem(context, systemId);

        final var marked = this.notificationRepository.markAllRead(systemId, context.user());

        log.debug(
            "Marked notifications read organizationId={} systemId={} count={}",
            context.organization().id(),
            systemId.id(),
            marked
        );

        if (marked > 0) {
            this.publisher.publish(NotificationEvent.allRead(systemId, context.user()));
        }

        return marked;
    }

    /**
     * Checks the caller may read the system's notifications, as before
     * listening to them.
     */
    public void requireReadable(
        final RequestContext context,
        final System.SystemId systemId
    ) {
        this.authorizationService.authorize(context, Permission.NOTIFICATION_READ);
        this.systemService.getSystem(context, systemId);
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

        final var notification = this.getNotification(context, id);

        this.notificationRepository.delete(id);
        this.publisher.publish(NotificationEvent.deleted(notification));
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
