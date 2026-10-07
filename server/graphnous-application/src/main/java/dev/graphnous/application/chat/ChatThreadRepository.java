package dev.graphnous.application.chat;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.chat.ChatThread;
import dev.graphnous.domain.system.System;

import java.util.Optional;
import java.util.UUID;

public interface ChatThreadRepository {

    ChatThread save(final ChatThread thread);

    Optional<ChatThread> findById(final ChatThread.ChatThreadId id);

    /**
     * The threads of the user about the system.
     *
     * @param userId null for the single user of a server without security
     */
    Page<ChatThread> findAll(
        final System.SystemId systemId,
        final UUID userId,
        final PageQuery pageQuery
    );

    void delete(final ChatThread.ChatThreadId id);

    void deleteBySystemId(final System.SystemId systemId);
}
