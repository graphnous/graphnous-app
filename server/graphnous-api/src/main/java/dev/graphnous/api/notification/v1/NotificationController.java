package dev.graphnous.api.notification.v1;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.graphnous.api.sse.SseHeartbeat;
import dev.graphnous.api.v1.generated.notification.Notification;
import dev.graphnous.api.v1.generated.notification.NotificationCount;
import dev.graphnous.api.v1.generated.notification.NotificationPage;
import dev.graphnous.api.v1.generated.notification.UpdateNotificationRequest;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.notification.NotificationEvent;
import dev.graphnous.application.notification.NotificationListener;
import dev.graphnous.application.notification.NotificationService;
import dev.graphnous.application.notification.NotificationSubscriber;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

/**
 * The notifications of systems, projects and scans, newest first: to list,
 * count, mark read and delete, and to follow as they happen.
 */
@RestController
@RequestMapping("/api/v1")
public class NotificationController {

    /**
     * Newest first, as notifications are read.
     */
    static final Sort NEWEST_FIRST = new Sort("createdAt", Sort.Direction.DESC);

    private final NotificationService notificationService;
    private final NotificationSubscriber notificationSubscriber;
    private final SseHeartbeat heartbeat;
    private final RequestContextProvider contextProvider;

    public NotificationController(
        final NotificationService notificationService,
        final NotificationSubscriber notificationSubscriber,
        final SseHeartbeat heartbeat,
        final RequestContextProvider contextProvider
    ) {
        this.notificationService = notificationService;
        this.notificationSubscriber = notificationSubscriber;
        this.heartbeat = heartbeat;
        this.contextProvider = contextProvider;
    }

    /**
     * What a notification stream sends of an event, as the OpenAPI spec's
     * NotificationEvent: without the notification or its id where the event
     * has none.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationEventResponse(
        String type,
        UUID systemId,
        UUID notificationId,
        Notification notification
    ) {
    }

    @GetMapping("/systems/{systemId}/notifications")
    public ResponseEntity<NotificationPage> listSystemNotifications(
        @PathVariable("systemId") final UUID systemId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "unread", defaultValue = "false") final boolean unread
    ) {
        return ResponseEntity.ok(toPage(notificationService.getNotifications(
            contextProvider.get(), query(page, size), new System.SystemId(systemId), unread
        )));
    }

    @GetMapping("/systems/{systemId}/notifications/unread")
    public ResponseEntity<NotificationCount> countUnreadNotifications(@PathVariable("systemId") final UUID systemId) {
        return ResponseEntity.ok(count(notificationService.countUnread(contextProvider.get(), new System.SystemId(systemId))));
    }

    @PostMapping("/systems/{systemId}/notifications/read")
    public ResponseEntity<NotificationCount> markAllNotificationsRead(@PathVariable("systemId") final UUID systemId) {
        return ResponseEntity.ok(count(notificationService.markAllRead(contextProvider.get(), new System.SystemId(systemId))));
    }

    /**
     * What happens to the system's notifications from now on, as
     * server-sent events named after their type, such as created: those of
     * everyone, and of whether the caller read them, not others. It opens
     * with a comment, so a proxy in between passes the stream on at once.
     */
    @GetMapping(value = "/systems/{systemId}/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@PathVariable("systemId") final UUID systemId) {
        final var system = new System.SystemId(systemId);

        final var context = contextProvider.get();

        // Checks the caller may read the system's notifications, and that it exists
        notificationService.requireReadable(context, system);

        final var emitter = new SseEmitter(0L);

        final NotificationListener listener = event -> {
            if (!event.isFor(context.user())) {
                return;
            }

            try {
                emitter.send(SseEmitter.event()
                    .name(event.type().name().toLowerCase(Locale.ROOT))
                    .data(toResponse(event), MediaType.APPLICATION_JSON));
            } catch (final IOException e) {
                // The client went away: nothing to tell it an error on
                emitter.complete();
            }
        };

        notificationSubscriber.subscribe(system, listener);

        emitter.onCompletion(() -> notificationSubscriber.unsubscribe(system, listener));
        emitter.onTimeout(() -> notificationSubscriber.unsubscribe(system, listener));
        emitter.onError(error -> notificationSubscriber.unsubscribe(system, listener));
        heartbeat.register(emitter);

        try {
            emitter.send(SseEmitter.event().comment("notifications of system " + systemId));
        } catch (final IOException e) {
            emitter.complete();
        }

        return emitter;
    }

    @GetMapping("/projects/{projectId}/notifications")
    public ResponseEntity<NotificationPage> listProjectNotifications(
        @PathVariable("projectId") final UUID projectId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "unread", defaultValue = "false") final boolean unread
    ) {
        return ResponseEntity.ok(toPage(notificationService.getNotifications(
            contextProvider.get(), query(page, size), new Project.ProjectId(projectId), unread
        )));
    }

    @GetMapping("/scans/{scanId}/notifications")
    public ResponseEntity<NotificationPage> listScanNotifications(
        @PathVariable("scanId") final UUID scanId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "unread", defaultValue = "false") final boolean unread
    ) {
        return ResponseEntity.ok(toPage(notificationService.getNotifications(
            contextProvider.get(), query(page, size), new Scan.ScanId(scanId), unread
        )));
    }

    @GetMapping("/notifications/{notificationId}")
    public ResponseEntity<Notification> getNotification(@PathVariable("notificationId") final UUID notificationId) {
        return ResponseEntity.ok(fromDomain(notificationService.getNotification(contextProvider.get(), id(notificationId))));
    }

    /**
     * Marks the notification read or unread; 400 without read.
     */
    @PatchMapping("/notifications/{notificationId}")
    public ResponseEntity<Notification> updateNotification(
        @PathVariable("notificationId") final UUID notificationId,
        @RequestBody final UpdateNotificationRequest request
    ) {
        if (request == null || request.getRead() == null) {
            throw new ValidationException("read is required");
        }

        return ResponseEntity.ok(fromDomain(notificationService.markRead(contextProvider.get(), id(notificationId), request.getRead())));
    }

    @DeleteMapping("/notifications/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable("notificationId") final UUID notificationId) {
        notificationService.delete(contextProvider.get(), id(notificationId));

        return ResponseEntity.noContent().build();
    }

    static Notification fromDomain(final dev.graphnous.domain.notification.Notification domain) {
        final var notification = new Notification();

        notification.setId(domain.id().id());
        notification.setSystemId(domain.systemId().id());
        notification.setProjectId(domain.projectId() == null ? null : domain.projectId().id());
        notification.setScanId(domain.scanId() == null ? null : domain.scanId().id());
        notification.setTitle(domain.title());
        notification.setContent(domain.content());
        notification.setRead(domain.read());
        notification.setCreatedAt(domain.createdAt().atOffset(ZoneOffset.UTC));

        return notification;
    }

    static NotificationEventResponse toResponse(final NotificationEvent event) {
        return new NotificationEventResponse(
            event.type().name(),
            event.systemId().id(),
            event.notificationId() == null ? null : event.notificationId().id(),
            event.notification() == null ? null : fromDomain(event.notification())
        );
    }

    private static NotificationPage toPage(final Page<dev.graphnous.domain.notification.Notification> page) {
        final var response = new NotificationPage();

        response.setContent(page.content().stream().map(NotificationController::fromDomain).toList());
        response.setPage(page.page());
        response.setSize(page.size());
        response.setTotalElements(page.totalElements());
        response.setTotalPages(page.totalPages());

        return response;
    }

    private static NotificationCount count(final long count) {
        final var response = new NotificationCount();
        response.setCount(count);

        return response;
    }

    private static PageQuery query(final int page, final int size) {
        return new PageQuery(page, size, NEWEST_FIRST);
    }

    private static dev.graphnous.domain.notification.Notification.NotificationId id(final UUID id) {
        return new dev.graphnous.domain.notification.Notification.NotificationId(id);
    }
}
