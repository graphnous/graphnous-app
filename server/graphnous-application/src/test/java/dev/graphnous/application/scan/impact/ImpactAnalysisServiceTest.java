package dev.graphnous.application.scan.impact;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.scan.links.HttpLinkRepository;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @Mock
    private ScanService scanService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ScanGraphService scanGraphService;

    @Mock
    private ScanUsageService scanUsageService;

    @Mock
    private HttpLinkRepository httpLinkRepository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final System.SystemId systemId = System.SystemId.generate();
    private final Project api = project("api");
    private final Project web = project("web");

    // The api's main, and a branch of it
    private final Scan base = scan(api, "main");
    private final Scan head = scan(api, "feature");
    // The latest of the web app, which calls the api
    private final Scan webScan = scan(web, "main");

    private static final String CREATE = "api|orders|class:com.acme.Orders|method:com.acme.Orders.create(Order)";
    private static final String FIND = "api|orders|class:com.acme.Orders|method:com.acme.Orders.find(UUID)";

    @Test
    void followsWhatUsesTheRemovedAndChangedNodesMostUsedFirst() {
        scans();
        comparison(
            List.of(snapshot(CREATE, "Method", "create")),
            List.of(new ScanGraph.Change(FIND, "Method", "find",
                List.of(new ScanGraph.PropertyChange("returnType", "Order", "Optional<Order>"))))
        );
        noCalls();

        // create was used once, by the api itself; find three times, twice from the web app
        when(scanUsageService.getUsages(context, base.id(), id(base, CREATE), 1))
            .thenReturn(found(List.of(usage("checkout", base.id())), Map.of(base.id(), api.id())));
        when(scanUsageService.getUsages(context, head.id(), id(head, FIND), 1))
            .thenReturn(found(
                List.of(usage("show", head.id()), usage("load", webScan.id()), usage("reload", webScan.id())),
                Map.of(head.id(), api.id(), webScan.id(), web.id())
            ));

        final var report = service().analyze(context, base.id(), head.id(), null);

        assertThat(report.changes())
            .extracting(ImpactReport.Change::kind, ImpactReport.Change::id, ImpactReport.Change::scanId, ImpactReport.Change::usageCount)
            .containsExactly(
                tuple(ImpactReport.Kind.CHANGED, id(head, FIND), head.id(), 3),
                tuple(ImpactReport.Kind.REMOVED, id(base, CREATE), base.id(), 1)
            );
        assertThat(report.changes().getFirst().properties())
            .containsExactly(new ScanGraph.PropertyChange("returnType", "Order", "Optional<Order>"));
        // As many in each: in either order
        assertThat(report.projects()).containsExactlyInAnyOrder(
            new ImpactReport.AffectedProject(api.id(), 2, 0),
            new ImpactReport.AffectedProject(web.id(), 2, 0)
        );
        assertThat(report.truncated()).isFalse();
    }

    @Test
    void listsTheCallsThatNoLongerReachAnEndpoint() {
        scans();
        comparison(List.of(), List.of());

        final var orders = new HttpLinks.Call("orders", webScan.id(), web.id(), "GET", "/api/orders", "fetch");
        final var customers = new HttpLinks.Call("customers", webScan.id(), web.id(), "GET", "/api/customers", "fetch");
        final var caller = new HttpLinks.Source("f1", "Method", "loadOrders", "loadOrders", null, "src/orders.ts", 12);
        final var listOrders = new HttpLinks.Endpoint("e1", base.id(), api.id(), "GET", "/api/orders");

        when(httpLinkRepository.findCalls(List.of(head, webScan))).thenReturn(List.of(orders, customers));
        // The branch moved the orders to /api/v2
        when(httpLinkRepository.findEndpoints(List.of(base))).thenReturn(List.of(
            listOrders,
            new HttpLinks.Endpoint("e2", base.id(), api.id(), "GET", "/api/customers")
        ));
        when(httpLinkRepository.findEndpoints(List.of(head))).thenReturn(List.of(
            new HttpLinks.Endpoint("e3", head.id(), api.id(), "GET", "/api/v2/orders"),
            new HttpLinks.Endpoint("e4", head.id(), api.id(), "GET", "/api/customers")
        ));
        when(httpLinkRepository.findSources(List.of(head, webScan), Set.of("orders"))).thenReturn(Map.of("orders", caller));

        final var report = service().analyze(context, base.id(), head.id(), 2);

        assertThat(report.brokenCalls()).containsExactly(new ImpactReport.BrokenCall(orders, caller, List.of(listOrders)));
        assertThat(report.projects()).containsExactly(new ImpactReport.AffectedProject(web.id(), 0, 1));
    }

    @Test
    void listsTheEndpointsTheHeadRemovesAndAdds() {
        scans();
        final var removed = snapshot("api|orders|class:com.acme.Orders|method:list()|endpoint:GET /api/orders", "Endpoint", "GET /api/orders");
        final var added = snapshot("api|orders|class:com.acme.Orders|method:list()|endpoint:GET /api/v2/orders", "Endpoint", "GET /api/v2/orders");

        when(scanGraphService.compare(eq(context), eq(base.id()), eq(head.id()), eq(Set.of("Class", "Method", "Field", "Endpoint")), anyInt()))
            .thenReturn(new ScanGraph.Comparison(List.of(), List.of(added), List.of(removed), List.of(), false));
        noCalls();

        final var report = service().analyze(context, base.id(), head.id(), null);

        assertThat(report.endpoints()).isEqualTo(new ImpactReport.Endpoints(List.of(removed), List.of(added)));
        // Endpoints are followed through the calls, not their usages
        assertThat(report.changes()).isEmpty();
    }

    @Test
    void followsAtMostTheLimitOfChanges() {
        scans();

        final var removed = new ArrayList<ScanGraph.Snapshot>();
        IntStream.rangeClosed(0, ImpactAnalysisService.MAX_CHANGES)
            .forEach(i -> removed.add(snapshot("api|orders|class:C" + i, "Class", "C" + i)));
        comparison(removed, List.of());
        noCalls();

        when(scanUsageService.getUsages(eq(context), eq(base.id()), any(), eq(1)))
            .thenReturn(found(List.of(), Map.of(base.id(), api.id())));

        final var report = service().analyze(context, base.id(), head.id(), null);

        assertThat(report.changes()).hasSize(ImpactAnalysisService.MAX_CHANGES);
        assertThat(report.truncated()).isTrue();
    }

    @Test
    void listsAtMostTheLimitOfUsagesButCountsThemAll() {
        scans();
        comparison(List.of(snapshot(CREATE, "Method", "create")), List.of());
        noCalls();

        final var usages = IntStream.range(0, ImpactAnalysisService.MAX_USAGES + 5)
            .mapToObj(i -> usage("caller" + i, base.id()))
            .toList();
        when(scanUsageService.getUsages(context, base.id(), id(base, CREATE), 1))
            .thenReturn(found(usages, Map.of(base.id(), api.id())));

        final var change = service().analyze(context, base.id(), head.id(), null).changes().getFirst();

        assertThat(change.usages()).hasSize(ImpactAnalysisService.MAX_USAGES);
        assertThat(change.usageCount()).isEqualTo(ImpactAnalysisService.MAX_USAGES + 5);
    }

    @Test
    void comparesOnlyScansOfOneProject() {
        when(scanService.getScan(context, base.id())).thenReturn(base);
        when(scanService.getScan(context, webScan.id())).thenReturn(webScan);

        assertThatThrownBy(() -> service().analyze(context, base.id(), webScan.id(), null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("same project");

        verifyNoInteractions(scanGraphService, scanUsageService, httpLinkRepository);
    }

    @Test
    void refusesADepthOutOfRange() {
        when(scanService.getScan(context, base.id())).thenReturn(base);
        when(scanService.getScan(context, head.id())).thenReturn(head);

        assertThatThrownBy(() -> service().analyze(context, base.id(), head.id(), ScanUsageService.MAX_DEPTH + 1))
            .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service().analyze(context, base.id(), head.id(), 0))
            .isInstanceOf(ValidationException.class);

        verifyNoInteractions(scanGraphService);
    }

    private void scans() {
        when(scanService.getScan(context, base.id())).thenReturn(base);
        when(scanService.getScan(context, head.id())).thenReturn(head);
        when(projectService.getProject(context, api.id())).thenReturn(api);
        // The api's latest is main, which the head stands in for
        when(scanService.getNewestCompletedScans(context, systemId)).thenReturn(List.of(base, webScan));
    }

    private void comparison(final List<ScanGraph.Snapshot> removed, final List<ScanGraph.Change> changed) {
        when(scanGraphService.compare(eq(context), eq(base.id()), eq(head.id()), any(), anyInt()))
            .thenReturn(new ScanGraph.Comparison(List.of(), List.of(), removed, changed, false));
    }

    private void noCalls() {
        when(httpLinkRepository.findCalls(any())).thenReturn(List.of());
        when(httpLinkRepository.findEndpoints(any())).thenReturn(List.of());
    }

    private Project project(final String name) {
        return new Project(Project.ProjectId.generate(), name, null, null, null, systemId, NOW, NOW);
    }

    private static Scan scan(final Project project, final String branch) {
        return new Scan(Scan.ScanId.generate(), project.id(), Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("abc", branch), NOW, NOW, NOW);
    }

    private static String id(final Scan scan, final String key) {
        return scan.id().id() + "|" + key;
    }

    private static ScanGraph.Snapshot snapshot(final String key, final String type, final String name) {
        return new ScanGraph.Snapshot(key, type, name, Map.of());
    }

    private static ScanUsages.Usage usage(final String user, final Scan.ScanId scanId) {
        return new ScanUsages.Usage(
            "CALLS", new ScanUsages.Node(user, "Method", user, null), null, "Caller.java", 1,
            new ScanUsages.Node("target", "Method", "target", null), null, 1, List.of(), scanId, null
        );
    }

    private static ScanUsages.Found found(final List<ScanUsages.Usage> usages, final Map<Scan.ScanId, Project.ProjectId> projects) {
        return new ScanUsages.Found(new ScanUsages.Node("subject", "Method", "subject", null), usages, false, projects);
    }

    private ImpactAnalysisService service() {
        return new ImpactAnalysisService(scanService, projectService, scanGraphService, scanUsageService, httpLinkRepository);
    }
}
