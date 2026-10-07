package dev.graphnous.application.chat;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.chat.ChatThread;
import dev.graphnous.domain.system.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.Objects;

/**
 * The conversations with the assistant. A thread is private to the user who
 * started it, and only while they may read its system: anyone else is told
 * it does not exist.
 */
public class ChatService {

    private final ChatThreadRepository chatThreadRepository;
    private final AuthorizationService authorizationService;
    private final SystemService systemService;
    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    public ChatService(
        final ChatThreadRepository chatThreadRepository,
        final AuthorizationService authorizationService,
        final SystemService systemService,
        final Clock clock
    ) {
        this.chatThreadRepository = chatThreadRepository;
        this.authorizationService = authorizationService;
        this.systemService = systemService;
        this.clock = clock;
    }

    public ChatThread create(
        final RequestContext context,
        final System.SystemId systemId
    ) {
        this.authorizationService.authorize(context, Permission.CHAT_WRITE);

        // Only about a system of the caller's organization
        this.systemService.getSystem(context, systemId);

        final var thread = this.chatThreadRepository.save(
            ChatThread.start(systemId, context.user().id(), this.clock.instant())
        );

        log.info(
            "Created chat thread organizationId={} systemId={} threadId={}",
            context.organization().id(),
            systemId.id(),
            thread.id().id()
        );

        return thread;
    }

    /**
     * The caller's threads about the system.
     */
    public Page<ChatThread> getThreads(
        final RequestContext context,
        final PageQuery pageQuery,
        final System.SystemId systemId
    ) {
        this.authorizationService.authorize(context, Permission.CHAT_READ);

        this.systemService.getSystem(context, systemId);

        return this.chatThreadRepository.findAll(systemId, context.user().id(), pageQuery);
    }

    public ChatThread getThread(
        final RequestContext context,
        final ChatThread.ChatThreadId id
    ) {
        this.authorizationService.authorize(context, Permission.CHAT_READ);

        return own(context, id);
    }

    /**
     * Stores the conversation of the thread as it is after a run of the
     * assistant; the first question becomes its title.
     */
    public ChatThread saveMessages(
        final RequestContext context,
        final ChatThread.ChatThreadId id,
        final String messages,
        final String title
    ) {
        this.authorizationService.authorize(context, Permission.CHAT_WRITE);

        final var thread = own(context, id);

        return this.chatThreadRepository.save(thread.withMessages(messages, title, this.clock.instant()));
    }

    public void delete(
        final RequestContext context,
        final ChatThread.ChatThreadId id
    ) {
        this.authorizationService.authorize(context, Permission.CHAT_DELETE);

        own(context, id);

        log.info(
            "Deleting chat thread organizationId={} threadId={}",
            context.organization().id(),
            id.id()
        );

        this.chatThreadRepository.delete(id);
    }

    /**
     * The thread, when it is the caller's and they may still read its
     * system.
     */
    private ChatThread own(
        final RequestContext context,
        final ChatThread.ChatThreadId id
    ) {
        final var thread = this.chatThreadRepository.findById(id)
            .filter(candidate -> Objects.equals(candidate.userId(), context.user().id()))
            .orElseThrow(() -> new NotFoundException("Chat thread %s not found".formatted(id.id())));

        this.systemService.getSystem(context, thread.systemId());

        return thread;
    }
}
