package dev.graphnous.api.ai;

import com.agui.community.core.agent.Context;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.spring.server.core.JacksonSerializer;
import dev.graphnous.application.ai.AiApiTokenRetriever;
import dev.graphnous.application.ai.ApiToken;
import dev.graphnous.application.chat.ChatService;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.system.SystemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphnousAgentTest {

    @Mock
    private AiApiTokenRetriever tokenRetriever;

    @Mock
    private RequestContextProvider contextProvider;

    @Mock
    private SystemService systemService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ScanService scanService;

    @Mock
    private ScanGraphService scanGraphService;

    @Mock
    private ChatService chatService;

    @Test
    void failsTheRunWithoutAnApiKey() throws Exception {
        when(tokenRetriever.retreive()).thenReturn(new ApiToken("", "openai"));

        assertThat(error(agent().run(input())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OpenAI API key");
    }

    @Test
    void failsTheRunForAnotherProvider() throws Exception {
        when(tokenRetriever.retreive()).thenReturn(new ApiToken("key", "mistral"));

        assertThat(error(agent().run(input())))
            .hasMessageContaining("'mistral'");
    }

    @Test
    void tellsTheModelWhatTheUserIsLookingAt() {
        final var input = new RunAgentInput(
            "thread",
            "run",
            null,
            List.of(),
            List.of(),
            List.of(new Context("The selected system", "{\"id\":\"a1\",\"name\":\"Shop\"}")),
            null,
            List.of()
        );

        assertThat(GraphnousAgent.systemPrompt(input))
            .startsWith(GraphnousAgent.SYSTEM_PROMPT)
            .endsWith("What the user is looking at:\n- The selected system: {\"id\":\"a1\",\"name\":\"Shop\"}\n");
        assertThat(GraphnousAgent.systemPrompt(input())).isEqualTo(GraphnousAgent.SYSTEM_PROMPT);
    }

    private static RunAgentInput input() {
        return new RunAgentInput("thread", "run", null, List.of(), List.of(), List.of(), null, List.of());
    }

    /**
     * What the run failed with.
     */
    private static Throwable error(final Flow.Publisher<Event> run) throws Exception {
        final var error = new CompletableFuture<Throwable>();

        run.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(final Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(final Event event) {
            }

            @Override
            public void onError(final Throwable throwable) {
                error.complete(throwable);
            }

            @Override
            public void onComplete() {
                error.completeExceptionally(new AssertionError("The run completed"));
            }
        });

        return error.get(5, TimeUnit.SECONDS);
    }

    private GraphnousAgent agent() {
        return new GraphnousAgent(
            tokenRetriever,
            contextProvider,
            systemService,
            projectService,
            scanService,
            scanGraphService,
            chatService,
            new JacksonSerializer(),
            "gpt-5"
        );
    }
}
