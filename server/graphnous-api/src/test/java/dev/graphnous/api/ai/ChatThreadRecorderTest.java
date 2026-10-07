package dev.graphnous.api.ai;

import com.agui.community.core.event.Event;
import com.agui.community.core.event.MessagesSnapshotEvent;
import com.agui.community.core.event.RunFinishedEvent;
import com.agui.community.core.event.RunStartedEvent;
import com.agui.community.core.message.AssistantMessage;
import com.agui.community.core.message.UserMessage;
import com.agui.community.spring.server.core.JacksonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.chat.ChatService;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.domain.chat.ChatThread;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatThreadRecorderTest {

    @Mock
    private ChatService chatService;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final UUID threadId = UUID.randomUUID();

    @Test
    void keepsTheConversationInsteadOfSendingItOn() throws Exception {
        final var events = record(threadId.toString(), snapshot("Which systems   do I have?"));

        assertThat(events).extracting(event -> event.getClass().getSimpleName())
            .containsExactly("RunStartedEvent", "RunFinishedEvent");

        final var messages = ArgumentCaptor.forClass(String.class);
        verify(chatService).saveMessages(
            eq(context),
            eq(new ChatThread.ChatThreadId(threadId)),
            messages.capture(),
            eq("Which systems do I have?")
        );

        final var saved = new ObjectMapper().readTree(messages.getValue());
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).get("role").asText()).isEqualTo("user");
        assertThat(saved.get(1).get("content").asText()).isEqualTo("You have one system.");
    }

    @Test
    void doesNotKeepARunOutsideAChatThread() throws Exception {
        final var events = record("thread-of-another-client", snapshot("Hi"));

        assertThat(events).hasSize(2);
        verifyNoInteractions(chatService);
    }

    @Test
    void goesOnWhenTheThreadIsNotTheUsers() throws Exception {
        when(chatService.saveMessages(any(), any(), anyString(), any())).thenThrow(new NotFoundException("Not found"));

        assertThat(record(threadId.toString(), snapshot("Hi"))).hasSize(2);
    }

    @Test
    void shortensALongFirstQuestionForTheTitle() throws Exception {
        final var messages = new ObjectMapper().readTree("""
            [{"id": "1", "role": "user", "content": "%s"}]
            """.formatted("a".repeat(200)));

        assertThat(ChatThreadRecorder.title(messages)).hasSize(80).endsWith("…");
        assertThat(ChatThreadRecorder.title(new ObjectMapper().readTree("[]"))).isNull();
    }

    private static MessagesSnapshotEvent snapshot(final String question) {
        return new MessagesSnapshotEvent(List.of(
            new UserMessage("1", question),
            new AssistantMessage("2", "You have one system.")
        ));
    }

    /**
     * What the client is sent of a run with the snapshot.
     */
    private List<Event> record(final String threadId, final MessagesSnapshotEvent snapshot) throws Exception {
        final var publisher = new SubmissionPublisher<Event>(Runnable::run, 16);
        final var received = new ArrayList<Event>();

        new ChatThreadRecorder(chatService, new JacksonSerializer())
            .recording(publisher, context, threadId)
            .subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(final Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(final Event event) {
                    received.add(event);
                }

                @Override
                public void onError(final Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });

        publisher.submit(new RunStartedEvent(threadId, "run"));
        publisher.submit(snapshot);
        publisher.submit(new RunFinishedEvent(threadId, "run"));
        publisher.close();

        return received;
    }
}
