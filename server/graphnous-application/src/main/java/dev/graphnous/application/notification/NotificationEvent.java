package dev.graphnous.application.notification;

import dev.graphnous.application.context.UserContext;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.system.System;

import java.util.Objects;

/**
 * What happened to a notification of a system, or to all of them.
 *
 * @param notificationId the notification's id; null for {@link Type#ALL_READ}
 * @param notification   the notification as it is now, for
 *                       {@link Type#CREATED} and {@link Type#UPDATED}; null
 *                       otherwise
 * @param recipient      whose read state it changed, for
 *                       {@link Type#UPDATED} and {@link Type#ALL_READ}, as
 *                       each user reads for themselves; null for an event
 *                       of everyone
 */
public record NotificationEvent(
    Type type,
    System.SystemId systemId,
    Notification.NotificationId notificationId,
    Notification notification,
    UserContext recipient
) {

    public enum Type {
        CREATED,
        /**
         * Marked read or unread.
         */
        UPDATED,
        DELETED,
        /**
         * Every notification of the system, marked read.
         */
        ALL_READ
    }

    public static NotificationEvent created(final Notification notification) {
        return new NotificationEvent(Type.CREATED, notification.systemId(), notification.id(), notification, null);
    }

    /**
     * @param recipient who marked it read or unread
     */
    public static NotificationEvent updated(final Notification notification, final UserContext recipient) {
        return new NotificationEvent(Type.UPDATED, notification.systemId(), notification.id(), notification, recipient);
    }

    public static NotificationEvent deleted(final Notification notification) {
        return new NotificationEvent(Type.DELETED, notification.systemId(), notification.id(), null, null);
    }

    /**
     * @param recipient who marked them read
     */
    public static NotificationEvent allRead(final System.SystemId systemId, final UserContext recipient) {
        return new NotificationEvent(Type.ALL_READ, systemId, null, null, recipient);
    }

    /**
     * Whether the user is to hear of it: of everyone, or of their own read
     * state; no user hears of the read state of no user.
     */
    public boolean isFor(final UserContext user) {
        return recipient == null || Objects.equals(recipient.id(), user == null ? null : user.id());
    }
}
