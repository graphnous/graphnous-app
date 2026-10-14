package dev.graphnous.domain.chat;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;

import java.time.Instant;
import java.util.UUID;

/**
 * A conversation of a user with the assistant about a system, or about
 * one project of it.
 *
 * @param userId   who it belongs to; null for the single user of a server
 *                 without security
 * @param title    what it is about, from its first question; null until
 *                 one was asked
 * @param messages  the conversation, as the JSON array of its AG-UI
 *                  messages
 * @param projectId the project of the system it is about; null when it is
 *                  about the system as a whole
 */
public record ChatThread(
    ChatThreadId id,
    System.SystemId systemId,
    UUID userId,
    String title,
    String messages,
    Instant createdAt,
    Instant updatedAt,
    Project.ProjectId projectId
) {

    /**
     * A thread about the system as a whole.
     */
    public ChatThread(
        final ChatThreadId id,
        final System.SystemId systemId,
        final UUID userId,
        final String title,
        final String messages,
        final Instant createdAt,
        final Instant updatedAt
    ) {
        this(id, systemId, userId, title, messages, createdAt, updatedAt, null);
    }

    /**
     * A thread without messages yet.
     */
    public static ChatThread start(
        final System.SystemId systemId,
        final UUID userId,
        final Instant at
    ) {
        return start(systemId, null, userId, at);
    }

    /**
     * A thread without messages yet, about a project of the system; about
     * the system as a whole without one.
     */
    public static ChatThread start(
        final System.SystemId systemId,
        final Project.ProjectId projectId,
        final UUID userId,
        final Instant at
    ) {
        return new ChatThread(ChatThreadId.generate(), systemId, userId, null, "[]", at, at, projectId);
    }

    /**
     * The thread with the conversation as it is now; it keeps the title it
     * has, or takes the given one.
     */
    public ChatThread withMessages(
        final String messages,
        final String title,
        final Instant at
    ) {
        return new ChatThread(id, systemId, userId, this.title == null ? title : this.title, messages, createdAt, at, projectId);
    }

    public record ChatThreadId(UUID id) {

        public static ChatThreadId generate() {
            return new ChatThreadId(UUID.randomUUID());
        }
    }
}
