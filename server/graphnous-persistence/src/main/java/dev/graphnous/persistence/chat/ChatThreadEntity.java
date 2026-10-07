package dev.graphnous.persistence.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_threads")
public class ChatThreadEntity {

    @Id
    private UUID id;

    @Column(name = "system_id", nullable = false, updatable = false)
    private UUID systemId;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "title")
    private String title;

    /**
     * The AG-UI messages, as a JSON array.
     */
    @Column(name = "messages", nullable = false, columnDefinition = "TEXT")
    private String messages;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getSystemId() {
        return systemId;
    }

    public void setSystemId(final UUID systemId) {
        this.systemId = systemId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(final UUID userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getMessages() {
        return messages;
    }

    public void setMessages(final String messages) {
        this.messages = messages;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
