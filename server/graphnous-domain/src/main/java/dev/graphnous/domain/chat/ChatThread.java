package dev.graphnous.domain.chat;

import dev.graphnous.domain.system.System;

import java.time.Instant;
import java.util.UUID;

/**
 * A conversation of a user with the assistant about a system.
 *
 * @param userId   who it belongs to; null for the single user of a server
 *                 without security
 * @param title    what it is about, from its first question; null until
 *                 one was asked
 * @param messages the conversation, as the JSON array of its AG-UI
 *                 messages
 */
public record ChatThread(
    ChatThreadId id,
    System.SystemId systemId,
    UUID userId,
    String title,
    String messages,
    Instant createdAt,
    Instant updatedAt
) {

    /**
     * A thread without messages yet.
     */
    public static ChatThread start(
        final System.SystemId systemId,
        final UUID userId,
        final Instant at
    ) {
        return new ChatThread(ChatThreadId.generate(), systemId, userId, null, "[]", at, at);
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
        return new ChatThread(id, systemId, userId, this.title == null ? title : this.title, messages, createdAt, at);
    }

    public record ChatThreadId(UUID id) {

        public static ChatThreadId generate() {
            return new ChatThreadId(UUID.randomUUID());
        }
    }
}
