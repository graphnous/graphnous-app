package dev.graphnous.github;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.CreateScanCommand;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.impact.ImpactAssessor;
import dev.graphnous.application.scan.impact.ImpactComment;
import dev.graphnous.application.scan.impact.ImpactReport;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.source.ScanSourceService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitHubAppTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final long INSTALLATION = 42;

    @Mock
    private GitHubClient github;

    @Mock
    private PullRequestScans pullRequestScans;

    @Mock
    private ScanSourceService scanSourceService;

    @Mock
    private ScanService scanService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ImpactAnalysisService impactAnalysisService;

    private final OrganizationId organization = new OrganizationId(UUID.randomUUID());
    private final RequestContext context = new RequestContext(new UserContext(null), new OrganizationContext(organization));

    private final Project shop = new Project(
        Project.ProjectId.generate(), "shop", null, "git@github.com:acme/shop.git", null, System.SystemId.generate(), NOW, NOW
    );
    private final Scan head = scan("orders-v2", "abc123");
    private final Scan main = scan("main", "def456");

    private final PullRequestScan pullRequestScan = new PullRequestScan(
        head.id(), shop.id(), INSTALLATION, "acme/shop", 3, "abc123", "main", 7, NOW
    );

    @BeforeEach
    void setUp() {
        when(scanSourceService.getRepositoryProjects(context, "https://github.com/acme/shop.git")).thenReturn(List.of(shop));
        when(projectService.getProject(context, shop.id())).thenReturn(shop);
        when(github.startCheckRun(INSTALLATION, "acme/shop", "Graphnous impact: shop", "abc123")).thenReturn(7L);
        when(scanService.create(eq(context), any())).thenReturn(head);
        when(pullRequestScans.claim(any())).thenReturn(true);
    }

    @Test
    void scansAPullRequestsHeadWithACheckRun() {
        app().handle("pull_request", pullRequest("opened"));

        verify(scanService).create(context, new CreateScanCommand(shop.id(), "abc123", "orders-v2", null, null, null));
        verify(github).startCheckRun(INSTALLATION, "acme/shop", "Graphnous impact: shop", "abc123");
        verify(pullRequestScans).save(pullRequestScan);
    }

    @Test
    void ignoresWhatElseHappensToAPullRequest() {
        app().handle("pull_request", pullRequest("labeled"));
        app().handle("issues", pullRequest("opened"));

        verifyNoInteractions(scanService, github, pullRequestScans);
    }

    @Test
    void failsTheCheckRunWhenTheScanCannotStart() {
        when(scanService.create(eq(context), any())).thenThrow(new IllegalStateException("No scans left this month"));

        app().handle("pull_request", pullRequest("synchronize"));

        verify(github).completeCheckRun(INSTALLATION, "acme/shop", 7, "failure",
            new GitHubClient.Output("The scan could not start", "No scans left this month"));
        verify(pullRequestScans, never()).save(any());
    }

    @Test
    void scansPushesToTheDefaultBranch() {
        app().handle("push", push("refs/heads/main", false));

        verify(scanService).create(context, new CreateScanCommand(shop.id(), "def456", "main", null, null, null));
    }

    @Test
    void leavesPushesToOtherBranchesAndDeletedBranches() {
        app().handle("push", push("refs/heads/orders-v2", false));
        app().handle("push", push("refs/heads/main", true));
        app(false).handle("push", push("refs/heads/main", false));

        verify(scanService, never()).create(any(), any());
    }

    @Test
    void reportsACompletedScanOnThePullRequest() {
        final var report = report(List.of());

        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));
        when(scanService.getLatestCompletedScan(context, shop.id(), "main")).thenReturn(Optional.of(main));
        when(impactAnalysisService.analyze(context, main.id(), head.id(), null)).thenReturn(report);
        when(github.findComment(INSTALLATION, "acme/shop", 3, GitHubApp.marker(shop.id()))).thenReturn(Optional.empty());

        app().completed(head.id());

        final var body = ArgumentCaptor.forClass(String.class);
        verify(github).comment(eq(INSTALLATION), eq("acme/shop"), eq(3), body.capture());
        assertThat(body.getValue())
            .startsWith(GitHubApp.marker(shop.id()) + "\n" + ImpactComment.MARKER)
            .contains("`orders-v2` at `abc123` compared with `main` at `def456`");

        final var output = ArgumentCaptor.forClass(GitHubClient.Output.class);
        verify(github).completeCheckRun(eq(INSTALLATION), eq("acme/shop"), eq(7L), eq("success"), output.capture());
        assertThat(output.getValue().title()).isEqualTo("Nothing else uses what changed");
        assertThat(output.getValue().summary()).startsWith(ImpactComment.MARKER);

        verify(pullRequestScans).claim(head.id());
    }

    @Test
    void editsTheCommentOfAnEarlierPushAndFlagsBrokenCalls() {
        final var report = report(List.of(new ImpactReport.BrokenCall(
            new HttpLinks.Call("c", head.id(), shop.id(), "GET", "/api/orders", "fetch"),
            null,
            List.of(new HttpLinks.Endpoint("e", main.id(), shop.id(), "GET", "/api/orders"))
        )));

        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));
        when(scanService.getLatestCompletedScan(context, shop.id(), "main")).thenReturn(Optional.of(main));
        when(impactAnalysisService.analyze(context, main.id(), head.id(), null)).thenReturn(report);
        when(github.findComment(INSTALLATION, "acme/shop", 3, GitHubApp.marker(shop.id()))).thenReturn(Optional.of(501L));

        app().completed(head.id());

        verify(github).editComment(eq(INSTALLATION), eq("acme/shop"), eq(501L), startsWith(GitHubApp.marker(shop.id())));
        verify(github, never()).comment(anyLong(), anyString(), anyInt(), anyString());
        verify(github).completeCheckRun(eq(INSTALLATION), eq("acme/shop"), eq(7L), eq("neutral"),
            eq(new GitHubClient.Output("1 broken call, 0 changes in use", ImpactComment.render(report, java.util.Map.of(shop.id(), "shop"), null))));
    }

    @Test
    void headsTheReportWithTheAssessment() {
        final var report = report(List.of());

        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));
        when(scanService.getLatestCompletedScan(context, shop.id(), "main")).thenReturn(Optional.of(main));
        when(impactAnalysisService.analyze(context, main.id(), head.id(), null)).thenReturn(report);
        when(github.findComment(anyLong(), anyString(), anyInt(), anyString())).thenReturn(Optional.empty());

        app((assessed, given) -> {
            assertThat(assessed).isEqualTo(context);
            assertThat(given).isSameAs(report);
            return "**Risk: low**, nothing else uses it.";
        }, true).completed(head.id());

        verify(github).comment(eq(INSTALLATION), eq("acme/shop"), eq(3), contains("#### Assessment\n\n**Risk: low**, nothing else uses it."));
    }

    @Test
    void reportsWithoutTheAssessmentWhenItFails() {
        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));
        when(scanService.getLatestCompletedScan(context, shop.id(), "main")).thenReturn(Optional.of(main));
        when(impactAnalysisService.analyze(context, main.id(), head.id(), null)).thenReturn(report(List.of()));
        when(github.findComment(anyLong(), anyString(), anyInt(), anyString())).thenReturn(Optional.empty());

        app((assessed, given) -> {
            throw new IllegalStateException("The assistant is not configured");
        }, true).completed(head.id());

        verify(github).comment(eq(INSTALLATION), eq("acme/shop"), eq(3), contains("_The assessment could not be written: The assistant is not configured_"));
        verify(github).completeCheckRun(eq(INSTALLATION), eq("acme/shop"), eq(7L), eq("success"), any());
    }

    @Test
    void saysWhenThereIsNoScanToCompareWith() {
        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));
        when(scanService.getLatestCompletedScan(context, shop.id(), "main")).thenReturn(Optional.empty());

        app().completed(head.id());

        verify(github).completeCheckRun(eq(INSTALLATION), eq("acme/shop"), eq(7L), eq("neutral"), any());
        verify(github, never()).comment(anyLong(), anyString(), anyInt(), anyString());
        verify(pullRequestScans).claim(head.id());
    }

    @Test
    void leavesTheCommentToTheScanOfANewerPush() {
        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));
        when(pullRequestScans.isSuperseded(pullRequestScan)).thenReturn(true);

        app().completed(head.id());

        verify(github).completeCheckRun(INSTALLATION, "acme/shop", 7, "neutral",
            new GitHubClient.Output("A newer push replaced this scan", "The pull request's newer commit has its own report."));
        verifyNoInteractions(impactAnalysisService);
        verify(pullRequestScans).claim(head.id());
    }

    @Test
    void failsTheCheckRunWhenTheReportFails() {
        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));
        when(scanService.getLatestCompletedScan(context, shop.id(), "main")).thenReturn(Optional.of(main));
        when(impactAnalysisService.analyze(context, main.id(), head.id(), null)).thenThrow(new IllegalStateException("Neo4j unavailable"));

        app().completed(head.id());

        verify(github).completeCheckRun(INSTALLATION, "acme/shop", 7, "failure",
            new GitHubClient.Output("The impact report failed", "Neo4j unavailable"));
        verify(pullRequestScans).claim(head.id());
    }

    @Test
    void failsTheCheckRunOfAScanThatFailed() {
        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));

        app().failed(head.id(), "Checking out abc123 failed");

        verify(github).completeCheckRun(INSTALLATION, "acme/shop", 7, "failure",
            new GitHubClient.Output("The scan failed", "Checking out abc123 failed"));
        verify(pullRequestScans).claim(head.id());
    }

    @Test
    void leavesScansOfNoPullRequest() {
        when(pullRequestScans.find(head.id())).thenReturn(Optional.empty());

        app().completed(head.id());
        app().failed(head.id(), "Failed");

        verifyNoInteractions(github, impactAnalysisService);
    }

    @Test
    void leavesAScanAnotherCallerReportsOn() {
        when(pullRequestScans.find(head.id())).thenReturn(Optional.of(pullRequestScan));
        when(pullRequestScans.claim(head.id())).thenReturn(false);

        app().completed(head.id());
        app().failed(head.id(), "Failed");

        verifyNoInteractions(github, impactAnalysisService);
    }

    @Test
    void sweepsUpTheScansWhoseEventsWereMissed() {
        final var deleted = pullRequestScan(Scan.ScanId.generate(), 8);
        final var failed = pullRequestScan(Scan.ScanId.generate(), 9);
        final var running = pullRequestScan(Scan.ScanId.generate(), 10);

        when(pullRequestScans.findAll()).thenReturn(List.of(pullRequestScan, deleted, failed, running));
        when(scanService.getScan(context, head.id())).thenReturn(head);
        when(scanService.getScan(context, deleted.scanId())).thenThrow(new NotFoundException("No such scan"));
        when(scanService.getScan(context, failed.scanId())).thenReturn(withStatus(failed.scanId(), Scan.ScanStatus.FAILED));
        when(scanService.getScan(context, running.scanId())).thenReturn(withStatus(running.scanId(), Scan.ScanStatus.RUNNING));
        when(scanService.getLatestCompletedScan(context, shop.id(), "main")).thenReturn(Optional.of(main));
        when(impactAnalysisService.analyze(context, main.id(), head.id(), null)).thenReturn(report(List.of()));
        when(github.findComment(anyLong(), anyString(), anyInt(), anyString())).thenReturn(Optional.empty());

        app().sweep();

        verify(github).comment(eq(INSTALLATION), eq("acme/shop"), eq(3), anyString());
        verify(github).completeCheckRun(eq(INSTALLATION), eq("acme/shop"), eq(7L), eq("success"), any());
        verify(github).completeCheckRun(INSTALLATION, "acme/shop", 8, "neutral",
            new GitHubClient.Output("The scan was deleted", "The scan was deleted before it was done, so there is no report."));
        verify(github).completeCheckRun(INSTALLATION, "acme/shop", 9, "failure",
            new GitHubClient.Output("The scan failed", "The scan failed; its steps in Graphnous say why."));
        verify(github, never()).completeCheckRun(eq(INSTALLATION), eq("acme/shop"), eq(10L), anyString(), any());
        verify(pullRequestScans, never()).claim(running.scanId());
    }

    @Test
    void keepsSweepingPastAScanItCannotCheck() {
        final var other = pullRequestScan(Scan.ScanId.generate(), 8);

        when(pullRequestScans.findAll()).thenReturn(List.of(pullRequestScan, other));
        when(scanService.getScan(context, head.id())).thenThrow(new IllegalStateException("Database unavailable"));
        when(scanService.getScan(context, other.scanId())).thenThrow(new NotFoundException("No such scan"));

        app().sweep();

        verify(github).completeCheckRun(eq(INSTALLATION), eq("acme/shop"), eq(8L), eq("neutral"), any());
    }

    private PullRequestScan pullRequestScan(final Scan.ScanId scanId, final long checkRun) {
        return new PullRequestScan(scanId, shop.id(), INSTALLATION, "acme/shop", 3, "abc123", "main", checkRun, NOW);
    }

    private Scan withStatus(final Scan.ScanId scanId, final Scan.ScanStatus status) {
        return new Scan(scanId, shop.id(), status, new Scan.SourceRevision("abc123", "orders-v2"), NOW, NOW, NOW);
    }

    private ImpactReport report(final List<ImpactReport.BrokenCall> brokenCalls) {
        return new ImpactReport(
            main, head, List.of(), List.of(),
            // Something changed, so the comment is a whole report
            new ImpactReport.Endpoints(List.of(new ScanGraph.Snapshot("e", "Endpoint", "GET /api/legacy", java.util.Map.of())), List.of()),
            brokenCalls,
            brokenCalls.isEmpty() ? List.of() : List.of(new ImpactReport.AffectedProject(shop.id(), 0, brokenCalls.size())),
            false
        );
    }

    private static JsonNode pullRequest(final String action) {
        return JSON.readTree("""
            {
              "action": "%s",
              "installation": {"id": 42},
              "repository": {"full_name": "acme/shop", "clone_url": "https://github.com/acme/shop.git", "default_branch": "main"},
              "pull_request": {
                "number": 3,
                "head": {"ref": "orders-v2", "sha": "abc123"},
                "base": {"ref": "main", "sha": "def456"}
              }
            }
            """.formatted(action));
    }

    private static JsonNode push(final String ref, final boolean deleted) {
        return JSON.readTree("""
            {
              "ref": "%s",
              "after": "def456",
              "deleted": %s,
              "installation": {"id": 42},
              "repository": {"full_name": "acme/shop", "clone_url": "https://github.com/acme/shop.git", "default_branch": "main"}
            }
            """.formatted(ref, deleted));
    }

    private GitHubApp app() {
        return app(true);
    }

    private GitHubApp app(final boolean scanDefaultBranch) {
        return app(null, scanDefaultBranch);
    }

    private GitHubApp app(final ImpactAssessor assessor, final boolean scanDefaultBranch) {
        return new GitHubApp(
            github, pullRequestScans, installation -> organization, scanSourceService, scanService, projectService,
            impactAnalysisService, assessor, scanDefaultBranch, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private Scan scan(final String branch, final String revision) {
        return new Scan(Scan.ScanId.generate(), shop.id(), Scan.ScanStatus.COMPLETED, new Scan.SourceRevision(revision, branch), NOW, NOW, NOW);
    }
}
