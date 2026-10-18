package dev.graphnous.mcp;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.links.HttpLinkService;
import dev.graphnous.application.scan.methods.ScanMethodService;
import dev.graphnous.application.scan.source.ScanSourceService;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Coding agents use Graphnous's tools over MCP, at /mcp, as the caller of
 * the request.
 */
@SpringBootTest(classes = McpEndpointTest.Application.class)
@AutoConfigureMockMvc
class McpEndpointTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(McpConfiguration.class)
    static class Application {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestContextProvider contextProvider;

    @MockitoBean
    private SystemService systemService;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private ScanGraphService scanGraphService;

    @MockitoBean
    private ScanMethodService scanMethodService;

    @MockitoBean
    private ScanUsageService scanUsageService;

    @MockitoBean
    private ScanSourceService scanSourceService;

    @MockitoBean
    private HttpLinkService httpLinkService;

    @MockitoBean
    private ImpactAnalysisService impactAnalysisService;

    @Test
    void introducesItself() throws Exception {
        mockMvc.perform(mcp("""
                {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
                  "protocolVersion": "2025-06-18",
                  "capabilities": {},
                  "clientInfo": {"name": "test", "version": "1.0"}
                }}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.serverInfo.name").value("graphnous"))
            .andExpect(jsonPath("$.result.capabilities.tools").exists())
            .andExpect(jsonPath("$.result.instructions").value(containsString("findUsages")));
    }

    @Test
    void listsTheTools() throws Exception {
        mockMvc.perform(mcp("""
                {"jsonrpc": "2.0", "id": 2, "method": "tools/list"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.tools[*].name").value(hasItems(
                "listSystems", "listProjects", "listScans", "analyzeImpact", "compareScans", "findFile", "listEndpoints", "listUnmatchedCalls", "findMethods", "findUsages"
            )));
    }

    /**
     * After the parameters of the tool's method, which the build keeps,
     * rather than arg0 and on.
     */
    @Test
    void namesTheArgumentsOfATool() throws Exception {
        mockMvc.perform(mcp("""
                {"jsonrpc": "2.0", "id": 4, "method": "tools/list"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.tools[?(@.name == 'listProjects')].inputSchema.properties.systemId").exists())
            .andExpect(jsonPath("$.result.tools[?(@.name == 'listProjects')].inputSchema.properties.arg0").doesNotExist())
            .andExpect(jsonPath("$.result.tools[?(@.name == 'compareScans')].inputSchema.properties.baseScanId").exists());
    }

    /**
     * On the request's thread, where the security context, which is the
     * thread's, knows the caller.
     */
    @Test
    void callsAToolAsTheCaller() throws Exception {
        final var requestThread = Thread.currentThread();
        final var caller = new RequestContext(
            new UserContext(UUID.randomUUID()),
            new OrganizationContext(new OrganizationId(UUID.randomUUID()))
        );
        final var system = new System(System.SystemId.generate(), "Shop", "The web shop", NOW, NOW);

        when(contextProvider.get()).thenAnswer(invocation -> {
            if (Thread.currentThread() != requestThread) {
                throw new IllegalStateException("Called on " + Thread.currentThread().getName());
            }
            return caller;
        });
        when(systemService.getSystems(eq(caller), any())).thenReturn(new Page<>(List.of(system), 0, 50, 1, 1));

        mockMvc.perform(mcp("""
                {"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {
                  "name": "listSystems", "arguments": {}
                }}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.isError").value(false))
            .andExpect(jsonPath("$.result.content[0].text").value(containsString("Shop")));
    }

    private static MockHttpServletRequestBuilder mcp(final String body) {
        return post("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .content(body);
    }
}
