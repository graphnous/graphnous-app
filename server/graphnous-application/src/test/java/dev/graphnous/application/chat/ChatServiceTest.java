package dev.graphnous.application.chat;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.chat.ChatThread;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private ChatThreadRepository chatThreadRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private SystemService systemService;

    private final RequestContext context = context(UUID.randomUUID());

    private final System.SystemId systemId = System.SystemId.generate();

    @Test
    void startsAnEmptyThreadOfTheCaller() {
        when(chatThreadRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        final var thread = service().create(context, systemId);

        assertThat(thread.systemId()).isEqualTo(systemId);
        assertThat(thread.userId()).isEqualTo(context.user().id());
        assertThat(thread.messages()).isEqualTo("[]");
        assertThat(thread.title()).isNull();
        assertThat(thread.createdAt()).isEqualTo(NOW);

        verify(authorizationService).authorize(context, Permission.CHAT_WRITE);
        // Only about a system of the caller's organization
        verify(systemService).getSystem(context, systemId);
    }

    @Test
    void listsTheCallersThreadsOfTheSystem() {
        final var query = PageQuery.of(0, 20);

        service().getThreads(context, query, systemId);

        verify(systemService).getSystem(context, systemId);
        verify(chatThreadRepository).findAll(systemId, context.user().id(), query);
    }

    @Test
    void keepsTheConversationAndItsFirstTitle() {
        final var thread = thread(context.user().id()).withMessages("[]", "First question", NOW);

        when(chatThreadRepository.findById(thread.id())).thenReturn(Optional.of(thread));
        when(chatThreadRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        final var saved = service().saveMessages(context, thread.id(), "[{\"id\":\"1\"}]", "Second question");

        assertThat(saved.messages()).isEqualTo("[{\"id\":\"1\"}]");
        assertThat(saved.title()).isEqualTo("First question");
        assertThat(saved.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void hidesTheThreadOfAnotherUser() {
        final var thread = thread(UUID.randomUUID());

        when(chatThreadRepository.findById(thread.id())).thenReturn(Optional.of(thread));

        assertThatThrownBy(() -> service().getThread(context, thread.id()))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service().saveMessages(context, thread.id(), "[]", null))
            .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service().delete(context, thread.id()))
            .isInstanceOf(NotFoundException.class);

        verify(chatThreadRepository, never()).save(any());
        verify(chatThreadRepository, never()).delete(any());
    }

    @Test
    void findsTheThreadOfTheSingleUserWithoutSecurity() {
        final var anonymous = context(null);
        final var thread = thread(null);

        when(chatThreadRepository.findById(thread.id())).thenReturn(Optional.of(thread));

        assertThat(service().getThread(anonymous, thread.id())).isEqualTo(thread);
    }

    @Test
    void deletesTheCallersThread() {
        final var thread = thread(context.user().id());

        when(chatThreadRepository.findById(thread.id())).thenReturn(Optional.of(thread));

        service().delete(context, thread.id());

        verify(authorizationService).authorize(context, Permission.CHAT_DELETE);
        final var id = ArgumentCaptor.forClass(ChatThread.ChatThreadId.class);
        verify(chatThreadRepository).delete(id.capture());
        assertThat(id.getValue()).isEqualTo(thread.id());
    }

    private ChatThread thread(final UUID userId) {
        return ChatThread.start(systemId, userId, NOW.minusSeconds(60));
    }

    private static RequestContext context(final UUID userId) {
        return new RequestContext(
            new UserContext(userId),
            new OrganizationContext(new OrganizationId(UUID.randomUUID()))
        );
    }

    private ChatService service() {
        return new ChatService(chatThreadRepository, authorizationService, systemService, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
