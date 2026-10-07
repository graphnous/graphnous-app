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
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.system.SystemService;
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
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final ScanGraphService scanGraphService,
        final ChatService chatService,
        final Serializer serializer,
        @Value("${graphnous.ai.openai.model}") final String model
    ) {
        return new GraphnousAgent(
            tokenRetriever,
            contextProvider,
            systemService,
            projectService,
            scanService,
            scanGraphService,
            chatService,
            serializer,
            model
        );
    }

    @Bean
    ChatService chatService(
        final ChatThreadRepository chatThreadRepository,
        final AuthorizationService authorizationService,
        final SystemService systemService
    ) {
        return new ChatService(chatThreadRepository, authorizationService, systemService, Clock.systemUTC());
    }
}
