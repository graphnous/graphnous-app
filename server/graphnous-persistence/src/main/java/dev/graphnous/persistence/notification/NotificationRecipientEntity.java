package dev.graphnous.persistence.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Whether a user read a notification: one row for each user who marked it,
 * without a user where there is none.
 */
@Entity
@Table(name = "notification_recipients")
public class NotificationRecipientEntity {

    @Id
    private UUID id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    /**
     * Null for no user, as without security or for an API key.
     */
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    /**
     * When it was last marked read; null while it is unread.
     */
    @Column(name = "read_at")
    private Instant readAt;

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(final UUID notificationId) {
        this.notificationId = notificationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(final UUID userId) {
        this.userId = userId;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(final boolean read) {
        this.read = read;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(final Instant readAt) {
        this.readAt = readAt;
    }
}
