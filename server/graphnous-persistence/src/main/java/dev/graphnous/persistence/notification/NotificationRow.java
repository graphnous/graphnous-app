package dev.graphnous.persistence.notification;

/**
 * A notification, with whether its recipient read it: null when they never
 * marked it, which is unread.
 */
public record NotificationRow(NotificationEntity notification, Boolean read) {
}
