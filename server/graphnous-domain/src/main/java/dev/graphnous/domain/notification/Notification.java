package dev.graphnous.domain.notification;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;

import java.time.Instant;
import java.util.UUID;

/**
 * A message about a system, or about one of its projects, or about a scan
 * of one of those projects.
 *
 * @param projectId the project it is about, or {@code null} for the system
 *                  as a whole; required with a scan
 * @param scanId    the scan it is about, or {@code null}
 * @param title     a short summary, or {@code null}
 * @param read      whether it has been read
 */
public record Notification(
    NotificationId id,
    System.SystemId systemId,
    Project.ProjectId projectId,
    Scan.ScanId scanId,
    String title,
    String content,
    boolean read,
    Instant createdAt
) {

    public Notification {
        if (id == null) {
            throw new IllegalArgumentException("A notification needs an id");
        }

        if (systemId == null) {
            throw new IllegalArgumentException("A notification needs a system");
        }

        if (scanId != null && projectId == null) {
            throw new IllegalArgumentException("A notification about a scan needs the scan's project");
        }

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("A notification needs content");
        }

        if (createdAt == null) {
            throw new IllegalArgumentException("A notification needs the time it was created");
        }

        title = title == null || title.isBlank() ? null : title;
    }

    /**
     * The notification, marked read or unread.
     */
    public Notification withRead(final boolean read) {
        return new Notification(id, systemId, projectId, scanId, title, content, read, createdAt);
    }

    public record NotificationId(UUID id) {

        public static NotificationId generate() {
            return new NotificationId(UUID.randomUUID());
        }
    }
}
