package dev.graphnous.mcp;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.scan.links.HttpLinkService;
import dev.graphnous.application.scan.methods.ScanMethodService;
import dev.graphnous.application.scan.source.ScanSourceService;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.system.SystemService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * The MCP server: Graphnous's tools for coding agents such as Claude Code,
 * served by Spring AI at spring.ai.mcp.server.streamable-http.mcp-endpoint.
 * Spring AI serves every {@link ToolCallbackProvider} bean, so this is the
 * only one.
 * <p>
 * It runs stateless, so a call needs no session and is answered on its
 * request's thread: there the {@link RequestContextProvider} knows the
 * caller, by the same access token or API key as the API. Its settings
 * default to those of graphnous-mcp.properties.
 */
@Configuration
@PropertySource("classpath:graphnous-mcp.properties")
public class McpConfiguration {

    @Bean
    GraphnousTools graphnousTools(
        final RequestContextProvider contextProvider,
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final ScanGraphService scanGraphService,
        final ScanMethodService scanMethodService,
        final ScanUsageService scanUsageService,
        final ScanSourceService scanSourceService,
        final HttpLinkService httpLinkService
    ) {
        return new GraphnousTools(
            contextProvider,
            systemService,
            projectService,
            scanService,
            scanGraphService,
            scanMethodService,
            scanUsageService,
            scanSourceService,
            httpLinkService
        );
    }

    @Bean
    ToolCallbackProvider graphnousMcpTools(final GraphnousTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
