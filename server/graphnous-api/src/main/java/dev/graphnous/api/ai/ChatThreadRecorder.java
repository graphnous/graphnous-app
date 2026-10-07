package dev.graphnous.api.ai;

import com.agui.community.core.event.Event;
import com.agui.community.core.event.MessagesSnapshotEvent;
import com.agui.community.core.serialization.Serializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.chat.ChatService;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.domain.chat.ChatThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Flow;

/**
 * Keeps the conversation of a run in its chat thread: the agent ends each
 * run with a snapshot of all its messages, which is stored instead of sent
 * on, as the client has them already. Runs on a thread that was not
 * created through the API are not kept. A conversation that cannot be
 * stored is logged; the run goes on.
 */
class ChatThreadRecorder {

    /**
     * The most of the first question that becomes the title.
     */
    private static final int TITLE_LENGTH = 80;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ChatService chatService;
    private final Serializer serializer;

    private static final Logger log = LoggerFactory.getLogger(ChatThreadRecorder.class);

    ChatThreadRecorder(
        final ChatService chatService,
        final Serializer serializer
    ) {
        this.chatService = chatService;
        this.serializer = serializer;
    }

    /**
     * The run's events, with its snapshot stored in the thread rather than
     * passed on.
     *
     * @param context the user the run is for, who must own the thread
     */
    Flow.Publisher<Event> recording(
        final Flow.Publisher<Event> run,
        final RequestContext context,
        final String threadId
    ) {
        return subscriber -> run.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(final Event event) {
                if (event instanceof MessagesSnapshotEvent snapshot) {
                    save(snapshot, context, threadId);
                    return;
                }

                subscriber.onNext(event);
            }

            @Override
            public void onError(final Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

    private void save(
        final MessagesSnapshotEvent snapshot,
        final RequestContext context,
        final String threadId
    ) {
        final ChatThread.ChatThreadId id;

        try {
            id = new ChatThread.ChatThreadId(UUID.fromString(threadId));
        } catch (final IllegalArgumentException e) {
            log.debug("Not keeping the conversation of thread {}: not a chat thread id", threadId);
            return;
        }

        try {
            final var messages = JSON.readTree(serializer.serialize(snapshot)).get("messages");

            chatService.saveMessages(context, id, messages.toString(), title(messages));
        } catch (final NotFoundException e) {
            log.debug("Not keeping the conversation of thread {}: no chat thread of the user", threadId);
        } catch (final Exception e) {
            log.warn("Keeping the conversation of chat thread threadId={} failed", threadId, e);
        }
    }

    /**
     * The first question, shortened; null when there is none in text.
     */
    static String title(final JsonNode messages) {
        for (final var message : messages) {
            final var content = message.get("content");

            if ("user".equals(message.path("role").asText()) && content != null && content.isTextual()) {
                final var text = content.asText().strip().replaceAll("\\s+", " ");

                if (text.isEmpty()) {
                    continue;
                }

                return text.length() <= TITLE_LENGTH ? text : text.substring(0, TITLE_LENGTH - 1) + "…";
            }
        }

        return null;
    }
}
