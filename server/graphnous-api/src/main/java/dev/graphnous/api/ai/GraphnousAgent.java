package dev.graphnous.api.ai;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.agent.RunAgentInput;
import com.agui.community.core.event.Event;
import com.agui.community.core.serialization.Serializer;
import com.agui.community.spring.ai.SpringAiAgent;
import dev.graphnous.application.ai.AiApiTokenRetriever;
import dev.graphnous.application.chat.ChatService;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.system.SystemService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
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
 * API key comes from the {@link AiApiTokenRetriever} each time, so a changed
 * key is used without a restart. Without a key the run fails with an AG-UI
 * error rather than the server failing to start. The conversation is kept
 * in the run's chat thread, when it is one of the user's.
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
        """;

    private final AiApiTokenRetriever tokenRetriever;
    private final RequestContextProvider contextProvider;

    private final SystemService systemService;
    private final ProjectService projectService;
    private final ScanService scanService;
    private final ScanGraphService scanGraphService;

    private final ChatThreadRecorder recorder;

    private final String model;

    // The chat model of the last key, reused while the key stays the same
    private String chatModelKey;
    private ChatModel chatModel;

    public GraphnousAgent(
        final AiApiTokenRetriever tokenRetriever,
        final RequestContextProvider contextProvider,
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final ScanGraphService scanGraphService,
        final ChatService chatService,
        final Serializer serializer,
        final String model
    ) {
        this.tokenRetriever = tokenRetriever;
        this.contextProvider = contextProvider;

        this.systemService = systemService;
        this.projectService = projectService;
        this.scanService = scanService;
        this.scanGraphService = scanGraphService;

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
        final var tools = new GraphnousTools(
            context,
            systemService,
            projectService,
            scanService,
            scanGraphService
        );

        final var chatClient = ChatClient.builder(chatModel())
            .defaultSystem(systemPrompt(input))
            .build();

        return SpringAiAgent.builder(chatClient)
            .tools(List.of(MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks()))
            // The whole conversation at the end of each run, to keep it
            .emitMessagesSnapshot(true)
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

    private synchronized ChatModel chatModel() {
        final var token = tokenRetriever.retreive();

        if (token == null || token.token() == null || token.token().isBlank()) {
            throw new IllegalStateException(
                "The assistant is not configured: set an OpenAI API key (graphnous.ai.openai.api-key)"
            );
        }

        if (!"openai".equalsIgnoreCase(token.provider())) {
            throw new IllegalStateException("The assistant does not support AI provider '" + token.provider() + "'");
        }

        if (!token.token().equals(chatModelKey)) {
            chatModel = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                    .apiKey(token.token())
                    .model(model)
                    .build())
                .build();
            chatModelKey = token.token();
        }

        return chatModel;
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
