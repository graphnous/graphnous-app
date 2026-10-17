package dev.graphnous.api.configuration;

import com.agui.community.core.agent.Agent;
import com.agui.community.core.serialization.Serializer;
import dev.graphnous.api.ai.GraphnousAgent;
import dev.graphnous.application.ai.AiApiTokenRetriever;
import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.chat.ChatService;
import dev.graphnous.application.chat.ChatThreadRepository;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.mcp.GraphnousTools;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AiConfiguration {

    /**
     * The assistant; the AG-UI starter serves it at ag-ui.server.path.
     */
    @Bean
    Agent assistant(
        final AiApiTokenRetriever tokenRetriever,
        final RequestContextProvider contextProvider,
        final GraphnousTools tools,
        final ChatService chatService,
        final Serializer serializer,
        @Value("${graphnous.ai.openai.model}") final String model
    ) {
        return new GraphnousAgent(
            tokenRetriever,
            contextProvider,
            tools,
            chatService,
            serializer,
            model
        );
    }

    @Bean
    ChatService chatService(
        final ChatThreadRepository chatThreadRepository,
        final AuthorizationService authorizationService,
        final SystemService systemService,
        final ProjectService projectService
    ) {
        return new ChatService(chatThreadRepository, authorizationService, systemService, projectService, Clock.systemUTC());
    }
}
