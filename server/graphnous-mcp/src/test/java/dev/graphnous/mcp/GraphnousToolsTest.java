package dev.graphnous.mcp;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.impact.ImpactReport;
import dev.graphnous.application.scan.links.HttpLinkService;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.methods.ScanMethod;
import dev.graphnous.application.scan.methods.ScanMethodFilter;
import dev.graphnous.application.scan.methods.ScanMethodService;
import dev.graphnous.application.scan.source.ScanSource;
import dev.graphnous.application.scan.source.ScanSourceService;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphnousToolsTest {

    private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");

    @Mock
    private SystemService systemService;

    @Mock
    private ProjectService projectService;

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

    @Mock
    private ImpactAnalysisService impactAnalysisService;

    // The user the agent runs for
    private final RequestContext context = context();

    @Test
    void offersTheModelEachTool() {
        final var callbacks = MethodToolCallbackProvider.builder()
            .toolObjects(tools())
            .build()
            .getToolCallbacks();

        assertThat(callbacks)
            .extracting(callback -> callback.getToolDefinition().name())
            .containsExactlyInAnyOrder(
                "listSystems",
                "listProjects",
                "listScans",
                "analyzeImpact",
                "compareScans",
                "getScanGraph",
                "findClasses",
                "getClass",
                "findFile",
                "listEndpoints",
                "listUnmatchedCalls",
                "findMethods",
                "findUsages",
                "findAnnotated",
                "listDependencies"
            );
    }

    @Test
    void listsTheSystemsOfTheUser() {
        final var system = new System(System.SystemId.generate(), "Shop", "The web shop", NOW, NOW);

        when(systemService.getSystems(eq(context), any())).thenReturn(page(system));

        assertThat(tools().listSystems())
            .containsExactly(new GraphnousTools.SystemSummary(system.id().id(), "Shop", "The web shop"));

        final var query = ArgumentCaptor.forClass(PageQuery.class);
        verify(systemService).getSystems(eq(context), query.capture());
        assertThat(query.getValue().sort().property()).isEqualTo("name");
    }

    @Test
    void listsTheProjectsOfASystem() {
        final var systemId = System.SystemId.generate();
        final var project = new Project(
            Project.ProjectId.generate(), "backend", null, "https://example.com/shop.git", "backend", systemId, NOW, NOW
        );

        when(projectService.getProjects(eq(context), any(), eq(systemId))).thenReturn(page(project));

        assertThat(tools().listProjects(systemId.id().toString()))
            .containsExactly(new GraphnousTools.ProjectSummary(
                project.id().id(), "backend", null, "https://example.com/shop.git", "backend"
            ));
    }

    @Test
    void listsTheScansOfAProject() {
        final var projectId = Project.ProjectId.generate();
        final var scan = new Scan(
            Scan.ScanId.generate(),
            projectId,
            Scan.ScanStatus.FAILED,
            new Scan.SourceRevision("abc123", "main"),
            NOW,
            NOW,
            NOW
        );

        when(scanService.getScans(eq(context), any(), eq(projectId))).thenReturn(page(scan));

        assertThat(tools().listScans(projectId.id().toString(), null))
            .containsExactly(new GraphnousTools.ScanSummary(scan.id().id(), "FAILED", "main", "abc123", "abc123", NOW, NOW));
    }

    @Test
    void listsTheScansOfABranchFromAsManyPagesAsItTakes() {
        final var projectId = Project.ProjectId.generate();
        final var main = new Scan(Scan.ScanId.generate(), projectId, Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("a1", "main"), NOW, NOW, NOW);
        final var feature = new Scan(Scan.ScanId.generate(), projectId, Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("b2", "feature"), NOW, NOW, NOW);

        when(scanService.getScans(eq(context), any(), eq(projectId))).thenAnswer(invocation -> {
            final PageQuery query = invocation.getArgument(1);

            // Only feature scans on the first page, main on the second
            return new Page<>(List.of(query.page() == 0 ? feature : main), query.page(), 50, 2, 2);
        });

        assertThat(tools().listScans(projectId.id().toString(), " main "))
            .extracting(GraphnousTools.ScanSummary::id)
            .containsExactly(main.id().id());
    }

    @Test
    void comparesTwoScansWithTheIdsOfTheirNodes() {
        final var projectId = Project.ProjectId.generate();
        final var base = new Scan(Scan.ScanId.generate(), projectId, Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("a1", "main"), NOW, NOW, NOW);
        final var head = new Scan(Scan.ScanId.generate(), projectId, Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("b2", "feature"), NOW, NOW, NOW);
        final var summary = List.of(new ScanGraph.TypeChanges("Method", 1, 1, 1), new ScanGraph.TypeChanges("Dependency", 1, 0, 0));
        final var changes = List.of(new ScanGraph.PropertyChange("returnType", "Order", "Optional<Order>"));

        when(scanService.getScan(context, base.id())).thenReturn(base);
        when(scanService.getScan(context, head.id())).thenReturn(head);
        when(scanGraphService.compare(context, base.id(), head.id(), Set.of("Method", "Dependency"), GraphnousTools.COMPARISON_LIMIT))
            .thenReturn(new ScanGraph.Comparison(
                summary,
                List.of(
                    new ScanGraph.Snapshot("api|orders|class:A|method:a()", "Method", "a", Map.of()),
                    new ScanGraph.Snapshot("api|orders|dependency:org.slf4j:slf4j-api", "Dependency", "org.slf4j:slf4j-api", Map.of("version", "2.0.9"))
                ),
                List.of(new ScanGraph.Snapshot("api|orders|class:A|method:old()", "Method", "old", Map.of())),
                List.of(new ScanGraph.Change("api|orders|class:A|method:find()", "Method", "find", changes)),
                false
            ));

        final var comparison = tools().compareScans(base.id().id().toString(), head.id().id().toString(), List.of("Method", " Dependency", ""));

        assertThat(comparison.base().revision()).isEqualTo("a1");
        assertThat(comparison.head().branch()).isEqualTo("feature");
        assertThat(comparison.summary()).isEqualTo(summary);
        assertThat(comparison.added()).containsExactly(
            new GraphnousTools.NodeSummary(head.id().id() + "|api|orders|class:A|method:a()", "Method", "a", Map.of()),
            new GraphnousTools.NodeSummary(null, "Dependency", "org.slf4j:slf4j-api", Map.of("version", "2.0.9"))
        );
        assertThat(comparison.removed()).containsExactly(
            new GraphnousTools.NodeSummary(base.id().id() + "|api|orders|class:A|method:old()", "Method", "old", Map.of())
        );
        assertThat(comparison.changed()).containsExactly(
            new GraphnousTools.ChangeSummary(head.id().id() + "|api|orders|class:A|method:find()", "Method", "find", changes)
        );
    }

    @Test
    void listsTheClassesMethodsAndDependenciesByDefault() {
        final var base = scan(Scan.ScanStatus.COMPLETED);
        final var head = scan(Scan.ScanStatus.COMPLETED);

        when(scanService.getScan(context, base.id())).thenReturn(base);
        when(scanService.getScan(context, head.id())).thenReturn(head);
        when(scanGraphService.compare(context, base.id(), head.id(), GraphnousTools.COMPARED_TYPES, GraphnousTools.COMPARISON_LIMIT))
            .thenReturn(new ScanGraph.Comparison(List.of(), List.of(), List.of(), List.of(), false));

        assertThat(tools().compareScans(base.id().id().toString(), head.id().id().toString(), null).added()).isEmpty();
        assertThat(tools().compareScans(base.id().id().toString(), head.id().id().toString(), List.of(" ")).added()).isEmpty();
    }

    @Test
    void analyzesTheImpactOfAChangeWithTheProjectsByName() {
        final var systemId = System.SystemId.generate();
        final var api = new Project(Project.ProjectId.generate(), "api", null, null, null, systemId, NOW, NOW);
        final var web = new Project(Project.ProjectId.generate(), "web", null, null, null, systemId, NOW, NOW);
        final var base = new Scan(Scan.ScanId.generate(), api.id(), Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("a1", "main"), NOW, NOW, NOW);
        final var head = new Scan(Scan.ScanId.generate(), api.id(), Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("b2", "feature"), NOW, NOW, NOW);
        final var webScan = Scan.ScanId.generate();
        final var usage = new ScanUsages.Usage(
            "CALLS", new ScanUsages.Node("u1", "Method", "load", null), null, "load.ts", 3,
            new ScanUsages.Node("m1", "Method", "create", null), null, 1, List.of(), webScan, null
        );
        final var caller = new HttpLinks.Source("f1", "Method", "loadOrders", "loadOrders", null, "src/orders.ts", 12);
        final var call = new HttpLinks.Call("call", webScan, web.id(), "GET", "/api/orders", "fetch");
        final var endpoint = new HttpLinks.Endpoint("e1", base.id(), api.id(), "GET", "/api/orders");

        when(impactAnalysisService.analyze(context, base.id(), head.id(), 2)).thenReturn(new ImpactReport(
            base,
            head,
            List.of(new ScanGraph.TypeChanges("Method", 0, 1, 0)),
            List.of(new ImpactReport.Change(
                ImpactReport.Kind.REMOVED, base.id().id() + "|m1", base.id(), "Method", "create", List.of(),
                List.of(usage), 1, false, Map.of(webScan, web.id())
            )),
            new ImpactReport.Endpoints(
                List.of(new ScanGraph.Snapshot("e1", "Endpoint", "GET /api/orders", Map.of())),
                List.of(new ScanGraph.Snapshot("e2", "Endpoint", "GET /api/v2/orders", Map.of()))
            ),
            List.of(new ImpactReport.BrokenCall(call, caller, List.of(endpoint))),
            List.of(new ImpactReport.AffectedProject(web.id(), 1, 1)),
            false
        ));
        when(projectService.getProject(context, api.id())).thenReturn(api);
        when(projectService.getProjects(eq(context), any(), eq(systemId))).thenReturn(new Page<>(List.of(api, web), 0, 100, 2, 1));

        final var impact = tools().analyzeImpact(base.id().id().toString(), head.id().id().toString(), 2);

        assertThat(impact.base().branch()).isEqualTo("main");
        assertThat(impact.head().branch()).isEqualTo("feature");
        assertThat(impact.changes()).singleElement().satisfies(change -> {
            assertThat(change.kind()).isEqualTo("REMOVED");
            assertThat(change.id()).isEqualTo(base.id().id() + "|m1");
            assertThat(change.usages()).singleElement().satisfies(found -> {
                assertThat(found.file()).isEqualTo("load.ts");
                assertThat(found.projectId()).isEqualTo(web.id().id());
            });
        });
        assertThat(impact.removedEndpoints()).containsExactly("GET /api/orders");
        assertThat(impact.addedEndpoints()).containsExactly("GET /api/v2/orders");
        assertThat(impact.brokenCalls()).containsExactly(new GraphnousTools.BrokenCallSummary(
            "GET", "/api/orders", "fetch", web.id().id(), "web", caller, List.of("GET /api/orders")
        ));
        assertThat(impact.projects()).containsExactly(new GraphnousTools.AffectedProjectSummary(web.id().id(), "web", 1, 1));
    }

    @Test
    void getsTheGraphOfAScanWithItsStatus() {
        final var scan = scan(Scan.ScanStatus.COMPLETED);
        final var target = new ScanGraph.Target(
            "backend", "JAVA", "25", "MAVEN",
            List.of(new ScanGraph.Module("orders", "orders", 2, 1, 3, 2, 2))
        );

        when(scanService.getScan(context, scan.id())).thenReturn(scan);
        when(scanGraphService.getOverview(context, scan.id())).thenReturn(new ScanGraph.Overview(List.of(target)));

        assertThat(tools().getScanGraph(scan.id().id().toString()))
            .isEqualTo(new GraphnousTools.ScanGraphSummary("COMPLETED", List.of(target)));
    }

    @Test
    void findsClassesOfAScan() {
        final var scanId = Scan.ScanId.generate();
        final var order = new ScanGraph.ClassSummary("Order", "com.example.Order", "CLASS", "orders", "Order.java");

        when(scanGraphService.findClasses(context, scanId, "order", 50)).thenReturn(List.of(order));

        assertThat(tools().findClasses(scanId.id().toString(), "order")).containsExactly(order);
    }

    @Test
    void findsAnnotatedElementsOfAScan() {
        final var scanId = Scan.ScanId.generate();
        final var annotated = new ScanGraph.AnnotatedElement(
            "METHOD", "com.example.OrderController", "getOrder",
            new ScanGraph.Annotation("GetMapping", "org.springframework.web.bind.annotation.GetMapping", "{\"value\":\"/{id}\"}", null)
        );

        when(scanGraphService.findAnnotated(context, scanId, "GetMapping", 50)).thenReturn(List.of(annotated));

        assertThat(tools().findAnnotated(scanId.id().toString(), "GetMapping")).containsExactly(annotated);
    }

    @Test
    void asksForTheUserOfEachCall() {
        final var other = context();
        final var contexts = List.of(context, other).iterator();
        final var tools = new GraphnousTools(
            contexts::next, systemService, projectService, scanService, scanGraphService, scanMethodService, scanUsageService, scanSourceService, httpLinkService, impactAnalysisService
        );

        when(systemService.getSystems(any(), any())).thenReturn(new Page<>(List.of(), 0, 50, 0, 0));

        tools.listSystems();
        tools.listSystems();

        verify(systemService).getSystems(eq(context), any());
        verify(systemService).getSystems(eq(other), any());
    }

    @Test
    void keepsTheUserItIsFixedTo() {
        final var fixed = new GraphnousTools(
            () -> {
                throw new AssertionError("Asked for the user of the request");
            },
            systemService, projectService, scanService, scanGraphService, scanMethodService, scanUsageService, scanSourceService, httpLinkService, impactAnalysisService
        ).forContext(context);

        when(systemService.getSystems(eq(context), any())).thenReturn(new Page<>(List.of(), 0, 50, 0, 0));

        assertThat(fixed.listSystems()).isEmpty();
    }

    @Test
    void findsAFileOfARepository() {
        final var project = new Project(
            Project.ProjectId.generate(), "shop", null, "https://github.com/acme/shop", null, System.SystemId.generate(), NOW, NOW
        );
        final var scan = new Scan(
            Scan.ScanId.generate(), project.id(), Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("abc123", "main"), NOW, NOW, NOW
        );
        final var create = new ScanSource.Declaration(
            "m1", "METHOD", "METHOD", "create", "com.acme.OrderService.create()", "com.acme.OrderService"
        );
        final var file = new ScanSource.File(
            "f1", "server", "orders", "orders/src/OrderService.java", "JAVA", "MAIN", List.of(create)
        );

        when(scanSourceService.find(context, "git@github.com:acme/shop.git", "server/orders/src/OrderService.java", "create"))
            .thenReturn(new ScanSource.Found(project, scan, "server/orders/src/OrderService.java", file, List.of(create)));

        assertThat(tools().findFile("git@github.com:acme/shop.git", "server/orders/src/OrderService.java", "create"))
            .isEqualTo(new GraphnousTools.FileSummary(
                new GraphnousTools.ProjectSummary(project.id().id(), "shop", null, "https://github.com/acme/shop", null),
                new GraphnousTools.ScanSummary(scan.id().id(), "COMPLETED", "main", "abc123", "abc123", NOW, NOW),
                "server/orders/src/OrderService.java",
                "f1",
                "server",
                "orders",
                "JAVA",
                "MAIN",
                List.of(create)
            ));
    }

    @Test
    void listsTheEndpointsOfASystemWithTheirCalls() {
        final var systemId = System.SystemId.generate();
        final var backend = new Project(Project.ProjectId.generate(), "backend", null, null, null, systemId, NOW, NOW);
        final var web = new Project(Project.ProjectId.generate(), "web", null, null, null, systemId, NOW, NOW);
        final var handler = new HttpLinks.Source("m1", "Method", "list", "com.acme.Orders.list()", "com.acme.Orders", "Orders.java", null);
        final var caller = new HttpLinks.Source("f1", "Method", "loadOrders", "loadOrders", null, "src/orders.ts", 12);
        final var call = new HttpLinks.Call("call", Scan.ScanId.generate(), web.id(), "GET", "{API}/orders", "fetch");
        final var endpoint = new HttpLinks.Endpoint("endpoint", Scan.ScanId.generate(), backend.id(), "GET", "/api/orders");

        when(httpLinkService.getEndpoints(
            context, systemId, new HttpLinks.EndpointFilter("orders", backend.id(), true), GraphnousTools.ENDPOINT_LIMIT
        )).thenReturn(new HttpLinks.Endpoints(
            List.of(new HttpLinks.EndpointCalls(endpoint, handler, List.of(new HttpLinks.CallSite(call, caller, HttpLinks.Match.SUFFIX)))),
            3,
            false
        ));
        when(projectService.getProjects(eq(context), any(), eq(systemId))).thenReturn(new Page<>(List.of(backend, web), 0, 100, 2, 1));

        assertThat(tools().listEndpoints(systemId.id().toString(), "orders", backend.id().id().toString(), true))
            .isEqualTo(new GraphnousTools.EndpointsSummary(
                List.of(new GraphnousTools.EndpointSummary(
                    "GET", "/api/orders", backend.id().id(), "backend", handler,
                    List.of(new GraphnousTools.CallSummary("GET", "{API}/orders", "fetch", web.id().id(), "web", caller, "SUFFIX"))
                )),
                3,
                false
            ));
    }

    @Test
    void listsTheEndpointsOfEveryProjectByDefault() {
        final var systemId = System.SystemId.generate();

        when(httpLinkService.getEndpoints(context, systemId, HttpLinks.EndpointFilter.none(), GraphnousTools.ENDPOINT_LIMIT))
            .thenReturn(new HttpLinks.Endpoints(List.of(), 0, false));
        when(projectService.getProjects(eq(context), any(), eq(systemId))).thenReturn(new Page<>(List.of(), 0, 100, 0, 0));

        assertThat(tools().listEndpoints(systemId.id().toString(), null, " ", null).endpoints()).isEmpty();
    }

    @Test
    void listsTheCallsOfNoEndpoint() {
        final var systemId = System.SystemId.generate();
        final var web = new Project(Project.ProjectId.generate(), "web", null, null, null, systemId, NOW, NOW);
        final var caller = new HttpLinks.Source("f1", "Method", "charge", "charge", null, "src/pay.ts", 4);
        final var call = new HttpLinks.Call("call", Scan.ScanId.generate(), web.id(), "POST", "/api/payments", "fetch");

        when(httpLinkService.getUnmatchedCalls(
            context, systemId, new HttpLinks.CallFilter("payments", web.id()), GraphnousTools.CALL_LIMIT
        )).thenReturn(new HttpLinks.Calls(List.of(new HttpLinks.CallSite(call, caller, null)), 1, false));
        when(projectService.getProjects(eq(context), any(), eq(systemId))).thenReturn(new Page<>(List.of(web), 0, 100, 1, 1));

        assertThat(tools().listUnmatchedCalls(systemId.id().toString(), "payments", web.id().id().toString()))
            .isEqualTo(new GraphnousTools.CallsSummary(
                List.of(new GraphnousTools.CallSummary("POST", "/api/payments", "fetch", web.id().id(), "web", caller, null)),
                1,
                false
            ));
    }

    @Test
    void findsMethodsOfAClass() {
        final var scanId = Scan.ScanId.generate();
        final var create = new ScanMethod(
            "m1", "create", "com.example.OrderService.create", "METHOD", List.of("public"), "Order",
            List.of("request"), List.of("OrderRequest"), List.of(), "com.example.OrderService", "orders", "OrderService.java"
        );

        when(scanMethodService.getMethods(
            eq(context), eq(scanId), eq(new ScanMethodFilter("create", null, null, "com.example.OrderService")), any()
        )).thenReturn(page(create));

        assertThat(tools().findMethods(scanId.id().toString(), "create", "com.example.OrderService"))
            .containsExactly(create);
    }

    @Test
    void findsUsagesWithTheProjectTheyAreIn() {
        final var scanId = Scan.ScanId.generate();
        final var frontend = Scan.ScanId.generate();
        final var frontendProject = Project.ProjectId.generate();
        final var subject = new ScanUsages.Node("m1", "Method", "create", "com.example.OrderService.create");
        final var request = new ScanUsages.Request("POST", "/api/orders", "POST", "/api/orders");
        final var usage = new ScanUsages.Usage(
            "REQUESTS", new ScanUsages.Node("f1", "Method", "checkout", null), null, "checkout.ts", 12,
            subject, null, 1, null, frontend, request
        );

        when(scanUsageService.getUsages(context, scanId, "m1", 2)).thenReturn(
            new ScanUsages.Found(subject, List.of(usage), false, Map.of(frontend, frontendProject))
        );

        final var found = tools().findUsages(scanId.id().toString(), "m1", 2);

        assertThat(found.subject()).isEqualTo(subject);
        assertThat(found.truncated()).isFalse();
        assertThat(found.usages()).containsExactly(new GraphnousTools.UsageSummary(
            "REQUESTS", usage.user(), null, "checkout.ts", 12, subject, null, 1, null,
            frontend.id(), frontendProject.id(), request
        ));
    }

    @Test
    void listsAtMostTheUsageLimit() {
        final var scanId = Scan.ScanId.generate();
        final var subject = new ScanUsages.Node("m1", "Method", "create", null);
        final var usages = IntStream.range(0, GraphnousTools.USAGE_LIMIT + 1)
            .mapToObj(line -> new ScanUsages.Usage(
                "CALLS", new ScanUsages.Node("c" + line, "Method", "caller", null), null, "Caller.java", line,
                subject, null, 1, List.of(), scanId, null
            ))
            .toList();

        when(scanUsageService.getUsages(context, scanId, "m1", null)).thenReturn(
            new ScanUsages.Found(subject, usages, false, Map.of(scanId, Project.ProjectId.generate()))
        );

        final var found = tools().findUsages(scanId.id().toString(), "m1", null);

        assertThat(found.usages()).hasSize(GraphnousTools.USAGE_LIMIT);
        assertThat(found.truncated()).isTrue();
    }

    private Scan scan(final Scan.ScanStatus status) {
        return new Scan(
            Scan.ScanId.generate(),
            Project.ProjectId.generate(),
            status,
            new Scan.SourceRevision("abc123", "main"),
            NOW,
            NOW,
            NOW
        );
    }

    private static <T> Page<T> page(final T item) {
        return new Page<>(List.of(item), 0, 50, 1, 1);
    }

    private GraphnousTools tools() {
        return new GraphnousTools(
            () -> context, systemService, projectService, scanService, scanGraphService, scanMethodService, scanUsageService, scanSourceService, httpLinkService, impactAnalysisService
        );
    }

    private static RequestContext context() {
        return new RequestContext(
            new UserContext(UUID.randomUUID()),
            new OrganizationContext(new OrganizationId(UUID.randomUUID()))
        );
    }
}
