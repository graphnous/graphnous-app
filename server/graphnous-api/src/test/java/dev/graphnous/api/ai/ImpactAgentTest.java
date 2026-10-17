package dev.graphnous.api.ai;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.impact.ImpactReport;
import dev.graphnous.application.scan.links.HttpLinkService;
import dev.graphnous.application.scan.methods.ScanMethodService;
import dev.graphnous.application.scan.source.ScanSourceService;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import dev.graphnous.mcp.GraphnousTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpactAgentTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @Mock
    private ChatModels chatModels;

    @Mock
    private ImpactAnalysisService impactAnalysisService;

    @Mock
    private ProjectService projectService;

    @Mock
    private SystemService systemService;

    @Mock
    private ScanService scanService;

    @Mock
    private ScanGraphService scanGraphService;

    @Mock
    private ScanMethodService scanMethodService;

    @Mock
    private ScanUsageService scanUsageService;

    @Mock
    private ScanSourceService scanSourceService;

    @Mock
    private HttpLinkService httpLinkService;

    // The user the assistant runs for
    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final System.SystemId systemId = System.SystemId.generate();
    private final Project api = new Project(Project.ProjectId.generate(), "api", null, null, null, systemId, NOW, NOW);
    private final Scan base = scan("main");
    private final Scan head = scan("feature");

    /**
     * A model that answers at once, and keeps the prompts it was given.
     */
    private final List<Prompt> prompts = new ArrayList<>();

    private final ChatModel model = new ChatModel() {
        @Override
        public ChatResponse call(final Prompt prompt) {
            prompts.add(prompt);

            return new ChatResponse(List.of(new Generation(new AssistantMessage("**Risk: high**, a call breaks."))));
        }

        // As a model that calls tools: the client gives it the tools in these
        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return getOptions();
        }
    };

    @Test
    void assessesTheReportOfTheChangeWithAModelOfItsOwn() {
        final var usages = IntStream.range(0, ImpactAgent.REPORT_USAGES + 3)
            .mapToObj(i -> new ScanUsages.Usage(
                "CALLS", new ScanUsages.Node("u" + i, "Method", "caller" + i, null), null, "Caller.java", i + 1,
                new ScanUsages.Node("m1", "Method", "create", null), null, 1, List.of(), base.id(), null
            ))
            .toList();

        when(impactAnalysisService.analyze(context, base.id(), head.id(), null)).thenReturn(new ImpactReport(
            base,
            head,
            List.of(new ScanGraph.TypeChanges("Method", 0, 1, 0)),
            List.of(new ImpactReport.Change(
                ImpactReport.Kind.REMOVED, base.id().id() + "|m1", base.id(), "Method", "create", List.of(),
                usages, usages.size(), false, Map.of(base.id(), api.id())
            )),
            new ImpactReport.Endpoints(List.of(), List.of()),
            List.of(),
            List.of(new ImpactReport.AffectedProject(api.id(), usages.size(), 0)),
            false
        ));
        when(projectService.getProject(context, api.id())).thenReturn(api);
        when(projectService.getProjects(eq(context), any(), eq(systemId))).thenReturn(new Page<>(List.of(api), 0, 100, 1, 1));
        when(chatModels.get("gpt-5")).thenReturn(model);

        final var assessment = agent().assess(context, base.id().id().toString(), head.id().id().toString(), null);

        assertThat(assessment).isEqualTo("**Risk: high**, a call breaks.");
        assertThat(prompts).singleElement().satisfies(prompt -> {
            assertThat(prompt.getInstructions())
                .filteredOn(SystemMessage.class::isInstance)
                .singleElement()
                .satisfies(system -> assertThat(system.getText()).isEqualTo(ImpactAgent.SYSTEM_PROMPT));

            // The report, with the first few usages of each change
            assertThat(prompt.getInstructions())
                .filteredOn(UserMessage.class::isInstance)
                .singleElement()
                .satisfies(user -> assertThat(user.getText())
                    .contains("\"kind\":\"REMOVED\"", "\"usageCount\":8", "caller4")
                    .doesNotContain("caller5"));

            // Only the lookups, not the report again
            assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrderElementsOf(ImpactAgent.TOOLS);
        });
    }

    @Test
    void assessesAReportAlreadyMade() {
        final var report = new ImpactReport(
            base, head, List.of(), List.of(), new ImpactReport.Endpoints(List.of(), List.of()), List.of(), List.of(), false
        );

        when(projectService.getProject(context, api.id())).thenReturn(api);
        when(projectService.getProjects(eq(context), any(), eq(systemId))).thenReturn(new Page<>(List.of(api), 0, 100, 1, 1));
        when(chatModels.get("gpt-5")).thenReturn(model);

        assertThat(agent().assess(context, report)).isEqualTo("**Risk: high**, a call breaks.");
        assertThat(prompts).singleElement().satisfies(prompt -> assertThat(prompt.getInstructions())
            .filteredOn(UserMessage.class::isInstance)
            .singleElement()
            .satisfies(user -> assertThat(user.getText()).contains(head.id().id().toString())));

        verifyNoInteractions(impactAnalysisService);
    }

    @Test
    void cutsTheUsagesOfEachChangeToTheFirstFew() {
        final var usage = new GraphnousTools.UsageSummary(
            "CALLS", null, null, "Caller.java", 1, null, null, 1, List.of(), null, null, null
        );
        final var change = new GraphnousTools.ImpactChange(
            "CHANGED", "m1", "Method", "find", List.of(), 9, false,
            IntStream.range(0, 9).mapToObj(i -> usage).toList()
        );

        final var compact = ImpactAgent.compact(new GraphnousTools.ImpactSummary(
            null, null, List.of(), List.of(change), List.of(), List.of(), List.of(), List.of(), false
        ));

        assertThat(compact.changes()).singleElement().satisfies(cut -> {
            assertThat(cut.usages()).hasSize(ImpactAgent.REPORT_USAGES);
            assertThat(cut.usageCount()).isEqualTo(9);
            assertThat(cut.truncated()).isTrue();
        });
    }

    @Test
    void isAToolOfTheAssistantForItsUser() {
        final var agent = mock(ImpactAgent.class);

        when(agent.assess(context, "base", "head", null)).thenReturn("Low risk.");

        assertThat(new ImpactTools(agent, context).assessImpact("base", "head")).isEqualTo("Low risk.");
    }

    private ImpactAgent agent() {
        final var tools = new GraphnousTools(
            () -> context, systemService, projectService, scanService, scanGraphService, scanMethodService,
            scanUsageService, scanSourceService, httpLinkService, impactAnalysisService
        );

        return new ImpactAgent(chatModels, tools, JsonMapper.builder().build(), "gpt-5");
    }

    private Scan scan(final String branch) {
        return new Scan(Scan.ScanId.generate(), api.id(), Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("abc", branch), NOW, NOW, NOW);
    }
}
