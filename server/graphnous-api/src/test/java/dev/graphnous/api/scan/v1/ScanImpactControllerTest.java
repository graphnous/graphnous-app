package dev.graphnous.api.scan.v1;

import dev.graphnous.api.ai.ImpactAgent;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.impact.ImpactComment;
import dev.graphnous.application.scan.impact.ImpactReport;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScanImpactControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestContextProvider contextProvider;

    @MockitoBean
    private ScanService scanService;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private ImpactAnalysisService impactAnalysisService;

    @MockitoBean
    private ImpactAgent impactAgent;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Project project = new Project(
        Project.ProjectId.generate(), "orders-api", null, null, null, System.SystemId.generate(), NOW, NOW
    );
    private final Scan base = scan("main", "1111111aaaa");
    private final Scan head = scan("orders-v2", "2222222bbbb");

    private final ImpactReport report = new ImpactReport(
        base, head, List.of(), List.of(),
        new ImpactReport.Endpoints(List.of(new ScanGraph.Snapshot("e", "Endpoint", "GET /api/orders", Map.of())), List.of()),
        List.of(),
        List.of(new ImpactReport.AffectedProject(project.id(), 0, 0)), false
    );

    @BeforeEach
    void setUp() {
        when(contextProvider.get()).thenReturn(context);
        when(scanService.getScan(context, head.id())).thenReturn(head);
        when(projectService.getProject(context, project.id())).thenReturn(project);
    }

    @Test
    void comparesWithTheNewestCompletedScanOfMain() throws Exception {
        when(scanService.getLatestCompletedScan(context, project.id(), "main")).thenReturn(Optional.of(base));
        when(impactAnalysisService.analyze(context, base.id(), head.id(), null)).thenReturn(report);

        mockMvc.perform(get("/api/v1/scans/{head}/pull-request-comment", head.id().id()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/markdown"))
            .andExpect(content().string(startsWith(ImpactComment.MARKER)))
            .andExpect(content().string(containsString("`orders-v2` at `2222222` compared with `main` at `1111111`")))
            .andExpect(content().string(not(containsString("#### Assessment"))));

        verify(impactAgent, never()).assess(any(), any(ImpactReport.class));
    }

    @Test
    void comparesWithTheGivenBaseOrBranch() throws Exception {
        final var develop = scan("develop", "3333333cccc");

        when(scanService.getLatestCompletedScan(context, project.id(), "develop")).thenReturn(Optional.of(develop));
        when(impactAnalysisService.analyze(context, develop.id(), head.id(), null)).thenReturn(report);
        when(impactAnalysisService.analyze(context, base.id(), head.id(), null)).thenReturn(report);

        mockMvc.perform(get("/api/v1/scans/{head}/pull-request-comment", head.id().id()).queryParam("baseBranch", "develop"))
            .andExpect(status().isOk());
        verify(impactAnalysisService).analyze(context, develop.id(), head.id(), null);

        mockMvc.perform(get("/api/v1/scans/{head}/pull-request-comment", head.id().id()).queryParam("base", base.id().id().toString()))
            .andExpect(status().isOk());
        verify(impactAnalysisService).analyze(context, base.id(), head.id(), null);
        verify(scanService, never()).getLatestCompletedScan(context, project.id(), "main");
    }

    @Test
    void needsACompletedScanOfTheBaseBranch() throws Exception {
        when(scanService.getLatestCompletedScan(context, project.id(), "main")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/scans/{head}/pull-request-comment", head.id().id()))
            .andExpect(status().isNotFound())
            .andExpect(content().string(containsString("No completed scan of branch main")));
    }

    @Test
    void headsTheCommentWithTheAssessmentWhenAsked() throws Exception {
        when(scanService.getLatestCompletedScan(context, project.id(), "main")).thenReturn(Optional.of(base));
        when(impactAnalysisService.analyze(context, base.id(), head.id(), null)).thenReturn(report);
        when(impactAgent.assess(context, report)).thenReturn("**Risk: low**, nothing uses what changed.");

        mockMvc.perform(get("/api/v1/scans/{head}/pull-request-comment", head.id().id()).queryParam("assess", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("#### Assessment\n\n**Risk: low**, nothing uses what changed.")));
    }

    @Test
    void keepsTheReportWhenTheAssessmentFails() throws Exception {
        when(scanService.getLatestCompletedScan(context, project.id(), "main")).thenReturn(Optional.of(base));
        when(impactAnalysisService.analyze(context, base.id(), head.id(), null)).thenReturn(report);
        when(impactAgent.assess(context, report)).thenThrow(new IllegalStateException("The assistant is not configured"));

        mockMvc.perform(get("/api/v1/scans/{head}/pull-request-comment", head.id().id()).queryParam("assess", "true"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("_The assessment could not be written: The assistant is not configured_")))
            .andExpect(content().string(containsString("#### Projects affected")));
    }

    private Scan scan(final String branch, final String revision) {
        return new Scan(Scan.ScanId.generate(), project.id(), Scan.ScanStatus.COMPLETED, new Scan.SourceRevision(revision, branch), NOW, NOW, NOW);
    }
}
