package dev.graphnous.api.ai;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.serialization.Serializer;
import com.agui.community.spring.ai.SpringAiAgent;
import dev.graphnous.application.chat.ChatService;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.mcp.GraphnousTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.stream.Collectors;

/**
 * The Graphnous assistant, served over AG-UI: a Spring AI agent that can
 * look up the systems, projects and scans of the user it runs for, and
 * what the scans found.
 * <p>
 * Each run is set up when it starts, on the request's thread: the user is
 * only known there, as the agent runs on after the request returns, and the
 * model comes from {@link ChatModels} each time, so a changed key is used
 * without a restart. Without a key the run fails with an AG-UI error rather
 * than the server failing to start. What a change risks breaking it leaves
 * to the {@link ImpactAgent}, a tool of its own.
 * <p>
 * The conversation is not kept in the run's chat thread: ag-ui 2.0.0 ends a
 * run without the snapshot of its messages that {@link ChatThreadRecorder}
 * stores.
 */
public class GraphnousAgent implements Agent {

    static final String SYSTEM_PROMPT = """
        You are the Graphnous assistant. Graphnous scans the source code of
        software systems into a graph, so people can see how their code fits
        together: its modules, classes, methods and dependencies.

        A system is an application made up of projects; a project is a git
        repository, or a directory in one; a scan reads a project's code at a
        branch or revision into the graph.

        Use the tools to look up the user's systems, projects and scans rather
        than guessing, and refer to them by name. To answer about a project's
        code, read the graph of its newest completed scan: start from its
        outline, then find the classes, annotations or dependencies the
        question is about. Answer briefly. When a tool fails, say what you
        could not look up.

        Your tools only look things up. To scan a project, as when a branch
        has no completed scan to look at yet, ask the user with startScan:
        it starts nothing until they agree. When the user asks what a branch
        or change affects or risks breaking, compare the newest completed
        scans of main and of the branch with assessImpact, and give them its
        assessment.
        """;

    private final ChatModels chatModels;
    private final RequestContextProvider contextProvider;

    private final GraphnousTools tools;
    private final ImpactAgent impactAgent;

    private final ChatThreadRecorder recorder;

    private final String model;

    public GraphnousAgent(
        final ChatModels chatModels,
        final RequestContextProvider contextProvider,
        final GraphnousTools tools,
        final ImpactAgent impactAgent,
        final ChatService chatService,
        final Serializer serializer,
        final String model
    ) {
        this.chatModels = chatModels;
        this.contextProvider = contextProvider;

        this.tools = tools;
        this.impactAgent = impactAgent;

        this.recorder = new ChatThreadRecorder(chatService, serializer);

        this.model = model;
    }

    @Override
    public Flow.Publisher<Event> run(final RunAgentInput input) {
        final SpringAiAgent agent;
        final RequestContext context;

        try {
            context = contextProvider.get();
            agent = agent(input, context);
        } catch (final RuntimeException e) {
            return failed(e);
        }

        return recorder.recording(agent.run(input), context, input.threadId());
    }

    /**
     * The agent for this run, with tools that act as the current user.
     */
    private SpringAiAgent agent(
        final RunAgentInput input,
        final RequestContext context
    ) {
        final var chatClient = ChatClient.builder(chatModels.get(model))
            .defaultSystem(systemPrompt(input))
            .build();

        final var callbacks = MethodToolCallbackProvider.builder()
            .toolObjects(tools.forContext(context), new ImpactTools(impactAgent, context))
            .build()
            .getToolCallbacks();

        return SpringAiAgent.builder(chatClient)
            .tools(List.of(callbacks))
            .build();
    }

    /**
     * The system prompt, with what the client says about where the user is,
     * such as the system they selected: the AG-UI run's context, which the
     * Spring AI agent does not pass on itself.
     */
    static String systemPrompt(final RunAgentInput input) {
        if (input.context().isEmpty()) {
            return SYSTEM_PROMPT;
        }

        final var context = input.context()
            .stream()
            .map(item -> "- " + item.description() + ": " + item.value())
            .collect(Collectors.joining("\n"));

        return SYSTEM_PROMPT + "\nWhat the user is looking at:\n" + context + "\n";
    }

    /**
     * A run that fails straight away, which the AG-UI endpoint reports to
     * the client as a RUN_ERROR.
     */
    private static Flow.Publisher<Event> failed(final RuntimeException error) {
        return subscriber -> {
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(final long n) {
                }

                @Override
                public void cancel() {
                }
            });
            subscriber.onError(error);
        };
    }
}
