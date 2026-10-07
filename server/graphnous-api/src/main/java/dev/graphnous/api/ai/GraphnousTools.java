package dev.graphnous.api.ai;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the agent may look up in Graphnous, for the user it runs for: the
 * services check that user's permissions and organization, as they do for
 * the API. Read-only.
 */
class GraphnousTools {

    /**
     * The most the agent gets of a list at once.
     */
    private static final int LIMIT = 50;

    private final RequestContext context;

    private final SystemService systemService;
    private final ProjectService projectService;
    private final ScanService scanService;
    private final ScanGraphService scanGraphService;

    GraphnousTools(
        final RequestContext context,
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final ScanGraphService scanGraphService
    ) {
        this.context = context;

        this.systemService = systemService;
        this.projectService = projectService;
        this.scanService = scanService;
        this.scanGraphService = scanGraphService;
    }

    record SystemSummary(UUID id, String name, String description) {
    }

    record ProjectSummary(UUID id, String name, String description, String gitUrl, String path) {
    }

    record ScanSummary(UUID id, String status, String branch, String revision, Instant createdAt, Instant startedAt) {
    }

    record ScanGraphSummary(String status, List<ScanGraph.Target> targets) {
    }

    @Tool(
        name = "listSystems",
        description = "Lists the systems of the user's organization, by name. A system groups the projects that make up one application."
    )
    List<SystemSummary> listSystems() {
        return systemService.getSystems(context, new PageQuery(0, LIMIT, new Sort("name", Sort.Direction.ASC)))
            .content()
            .stream()
            .map(system -> new SystemSummary(system.id().id(), system.name(), system.description()))
            .toList();
    }

    @Tool(
        name = "listProjects",
        description = "Lists the projects of a system, by name. A project is a git repository, or a directory in one, that Graphnous scans."
    )
    List<ProjectSummary> listProjects(
        @ToolParam(description = "The id of the system, from listSystems") final String systemId
    ) {
        return projectService.getProjects(
                context,
                new PageQuery(0, LIMIT, new Sort("name", Sort.Direction.ASC)),
                new System.SystemId(UUID.fromString(systemId))
            )
            .content()
            .stream()
            .map(project -> new ProjectSummary(
                project.id().id(),
                project.name(),
                project.description(),
                project.gitUrl(),
                project.path()
            ))
            .toList();
    }

    @Tool(
        name = "listScans",
        description = "Lists the most recent scans of a project, newest first, with their status: PENDING, QUEUED, RUNNING, COMPLETED or FAILED."
    )
    List<ScanSummary> listScans(
        @ToolParam(description = "The id of the project, from listProjects") final String projectId
    ) {
        return scanService.getScans(
                context,
                new PageQuery(0, LIMIT, Sort.defaultSort()),
                new Project.ProjectId(UUID.fromString(projectId))
            )
            .content()
            .stream()
            .map(scan -> new ScanSummary(
                scan.id().id(),
                scan.status().name(),
                scan.revision() == null ? null : scan.revision().branch(),
                scan.revision() == null ? null : scan.revision().revision(),
                scan.createdAt(),
                scan.startedAt()
            ))
            .toList();
    }

    @Tool(
        name = "getScanGraph",
        description = "Gets the outline of a scan's graph: its targets, each a language and build system, and their modules, "
            + "with how many files, packages, classes, methods and dependencies each module has. "
            + "Only a COMPLETED scan has a graph; to answer about a project, use its newest COMPLETED scan from listScans."
    )
    ScanGraphSummary getScanGraph(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId
    ) {
        final var scan = scanService.getScan(context, scanId(scanId));

        return new ScanGraphSummary(
            scan.status().name(),
            scanGraphService.getOverview(context, scan.id()).targets()
        );
    }

    @Tool(
        name = "findClasses",
        description = "Finds the classes, interfaces, enums and records of a scan whose name or qualified name contains "
            + "the query, ignoring case, with the module and file that declare them. An empty query lists them all, "
            + "up to the limit."
    )
    List<ScanGraph.ClassSummary> findClasses(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId,
        @ToolParam(description = "Part of the name, e.g. Order or com.example.orders", required = false) final String query
    ) {
        return scanGraphService.findClasses(context, scanId(scanId), query, LIMIT);
    }

    @Tool(
        name = "getClass",
        description = "Gets a class of a scan: its kind, modifiers, file, package, supertypes, the classes of the scan "
            + "that extend or implement it, its annotations with their arguments, and its methods and fields."
    )
    ScanGraph.ClassDetails getClass(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId,
        @ToolParam(description = "The qualified name of the class, from findClasses") final String qualifiedName
    ) {
        return scanGraphService.getClass(context, scanId(scanId), qualifiedName);
    }

    @Tool(
        name = "findAnnotated",
        description = "Finds the classes, methods and fields of a scan with an annotation, with its arguments as JSON, "
            + "e.g. RestController or GetMapping for the endpoints of a Spring application, or Entity for its "
            + "persisted types."
    )
    List<ScanGraph.AnnotatedElement> findAnnotated(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId,
        @ToolParam(description = "The simple or qualified name of the annotation, e.g. GetMapping or "
            + "org.springframework.web.bind.annotation.GetMapping") final String annotation
    ) {
        return scanGraphService.findAnnotated(context, scanId(scanId), annotation, LIMIT);
    }

    @Tool(
        name = "listDependencies",
        description = "Lists the libraries each module of a scan depends on, with their version and scope."
    )
    List<ScanGraph.Dependency> listDependencies(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId
    ) {
        return scanGraphService.getDependencies(context, scanId(scanId));
    }

    private static Scan.ScanId scanId(final String scanId) {
        return new Scan.ScanId(UUID.fromString(scanId));
    }
}
