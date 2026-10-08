package dev.graphnous.api.ai;

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
import java.util.UUID;

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

    // The user the agent runs for
    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

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
                "getScanGraph",
                "findClasses",
                "getClass",
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

        assertThat(tools().listScans(projectId.id().toString()))
            .containsExactly(new GraphnousTools.ScanSummary(scan.id().id(), "FAILED", "main", "abc123", "abc123", NOW, NOW));
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
        return new GraphnousTools(context, systemService, projectService, scanService, scanGraphService);
    }
}
