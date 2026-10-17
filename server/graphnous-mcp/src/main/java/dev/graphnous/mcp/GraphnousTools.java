package dev.graphnous.mcp;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
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
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What an AI agent may look up in Graphnous: the assistant's tools, and
 * those of the MCP server. Read-only.
 * <p>
 * Each call is for the user the {@link RequestContextProvider} gives at
 * that moment: the services check that user's permissions and
 * organization, as they do for the API. The MCP server calls the tools on
 * the request's thread, so that is the caller of the request; the
 * assistant runs on after its request returns, so it fixes the user first,
 * with {@link #forContext(RequestContext)}.
 */
public class GraphnousTools {

    /**
     * The most an agent gets of a list at once.
     */
    static final int LIMIT = 50;

    /**
     * The most usages an agent gets at once: enough to see what a change
     * affects, without filling its context.
     */
    static final int USAGE_LIMIT = 200;

    /**
     * The most endpoints an agent gets at once, each with its calls.
     */
    static final int ENDPOINT_LIMIT = 100;

    /**
     * The most nodes an agent gets at once of each of added, removed and
     * changed.
     */
    static final int COMPARISON_LIMIT = 100;

    /**
     * The most calls an agent gets at once.
     */
    static final int CALL_LIMIT = 200;

    /**
     * The types compareScans lists when not asked for others: what a change
     * of code is about, and of the API it serves. Files and fields change by
     * the hundred with them.
     */
    static final Set<String> COMPARED_TYPES = Set.of("Class", "Method", "Endpoint", "Dependency");

    /**
     * How many pages of LIMIT scans listScans reads at most to find those of
     * a branch.
     */
    static final int BRANCH_PAGES = 10;

    private final RequestContextProvider contextProvider;

    private final SystemService systemService;
    private final ProjectService projectService;
    private final ScanService scanService;
    private final ScanGraphService scanGraphService;
    private final ScanMethodService scanMethodService;
    private final ScanUsageService scanUsageService;
    private final ScanSourceService scanSourceService;
    private final HttpLinkService httpLinkService;
    private final ImpactAnalysisService impactAnalysisService;

    public GraphnousTools(
        final RequestContextProvider contextProvider,
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final ScanGraphService scanGraphService,
        final ScanMethodService scanMethodService,
        final ScanUsageService scanUsageService,
        final ScanSourceService scanSourceService,
        final HttpLinkService httpLinkService,
        final ImpactAnalysisService impactAnalysisService
    ) {
        this.contextProvider = contextProvider;

        this.systemService = systemService;
        this.projectService = projectService;
        this.scanService = scanService;
        this.scanGraphService = scanGraphService;
        this.scanMethodService = scanMethodService;
        this.scanUsageService = scanUsageService;
        this.scanSourceService = scanSourceService;
        this.httpLinkService = httpLinkService;
        this.impactAnalysisService = impactAnalysisService;
    }

    /**
     * The same tools, always for the user of this context.
     */
    public GraphnousTools forContext(final RequestContext context) {
        return new GraphnousTools(
            () -> context,
            systemService,
            projectService,
            scanService,
            scanGraphService,
            scanMethodService,
            scanUsageService,
            scanSourceService,
            httpLinkService,
            impactAnalysisService
        );
    }

    public record SystemSummary(UUID id, String name, String description) {
    }

    public record ProjectSummary(UUID id, String name, String description, String gitUrl, String path) {
    }

    /**
     * @param revision          the commit the scan checked out; null until it has
     * @param requestedRevision what the scan was asked for, such as a tag; null for the tip of its branch
     */
    public record ScanSummary(
        UUID id,
        String status,
        String branch,
        String revision,
        String requestedRevision,
        Instant createdAt,
        Instant startedAt
    ) {
    }

    /**
     * A file of a repository, as the newest completed scan of its project
     * read it.
     *
     * @param path         the path of the file in the repository
     * @param target       the path of the scan target the file is in, within
     *                     the project
     * @param declarations what the file declares, those of the symbol when
     *                     one was asked for, with their ids
     */
    public record FileSummary(
        ProjectSummary project,
        ScanSummary scan,
        String path,
        String fileId,
        String target,
        String module,
        String language,
        String sourceSet,
        List<ScanSource.Declaration> declarations
    ) {
    }

    /**
     * A call of an endpoint, from the project it is in.
     *
     * @param caller the method or function that makes it, with its file
     *               and line; null when the scan does not say
     * @param match  PATH when the call's whole path is the endpoint's;
     *               SUFFIX when the call starts with a base only known when
     *               it runs, such as {API_URL}/orders, which is less sure;
     *               null for a call of no endpoint
     */
    public record CallSummary(
        String httpMethod,
        String url,
        String client,
        UUID projectId,
        String project,
        HttpLinks.Source caller,
        String match
    ) {
    }

    /**
     * An endpoint of a project, with what serves it and the calls of the
     * system that request it.
     *
     * @param handler the method that serves it, with its id for findUsages;
     *                null when the scan does not say
     */
    public record EndpointSummary(
        String httpMethod,
        String url,
        UUID projectId,
        String project,
        HttpLinks.Source handler,
        List<CallSummary> calls
    ) {
    }

    /**
     * @param total     how many calls pass the filter
     * @param truncated whether there are more of those than it lists
     */
    public record CallsSummary(List<CallSummary> calls, int total, boolean truncated) {
    }

    /**
     * @param total     how many endpoints pass the filter
     * @param truncated whether there are more of those than it lists
     */
    public record EndpointsSummary(List<EndpointSummary> endpoints, int total, boolean truncated) {
    }

    /**
     * A node only one of two scans has.
     *
     * @param id its id in the scan that has it, for findUsages; null for a
     *           dependency, which is not one of a scan's nodes
     */
    public record NodeSummary(String id, String type, String name, Map<String, Object> properties) {
    }

    /**
     * A node both scans have, with the properties that differ.
     *
     * @param id its id in the head scan, for findUsages
     */
    public record ChangeSummary(String id, String type, String name, List<ScanGraph.PropertyChange> properties) {
    }

    /**
     * How the head scan differs from the base scan.
     *
     * @param summary   how many of each type were added, removed and
     *                  changed, of every type
     * @param truncated whether a list holds fewer than the summary counts of
     *                  the types asked for
     */
    public record ComparisonSummary(
        ScanSummary base,
        ScanSummary head,
        List<ScanGraph.TypeChanges> summary,
        List<NodeSummary> added,
        List<NodeSummary> removed,
        List<ChangeSummary> changed,
        boolean truncated
    ) {
    }

    /**
     * A removed or changed class, method or field, with what uses it.
     *
     * @param kind           REMOVED, CHANGED, RENAMED or SIGNATURE_CHANGED
     * @param id             its id: in the base scan for a removed, renamed
     *                       or re-signed one, in the head scan for a
     *                       changed one, as are its usages
     * @param replacement    for a renamed one, or a method whose signature
     *                       changed, the key of what the head has instead
     * @param usageCount     how many places use it, of which usages lists
     *                       the first, those outside tests first
     * @param testUsageCount how many of those places are in tests
     */
    public record ImpactChange(
        String kind,
        String id,
        String type,
        String name,
        List<ScanGraph.PropertyChange> properties,
        String replacement,
        int usageCount,
        int testUsageCount,
        boolean truncated,
        List<UsageSummary> usages
    ) {
    }

    /**
     * A call that reached an endpoint of the project in the base scan, and
     * reaches none in the head.
     *
     * @param endpoints the endpoints it reached, such as GET /api/orders
     */
    public record BrokenCallSummary(
        String httpMethod,
        String url,
        String client,
        UUID projectId,
        String project,
        HttpLinks.Source caller,
        List<String> endpoints
    ) {
    }

    public record AffectedProjectSummary(UUID projectId, String project, int usages, int brokenCalls) {
    }

    /**
     * What a change of a project affects, from the base scan to the head.
     *
     * @param removedEndpoints the endpoints the head no longer serves
     * @param addedEndpoints   the endpoints only the head serves
     * @param movedEndpoints   the endpoints whose path or method the head
     *                         changed, as {@code GET /a -> GET /b}
     * @param unusedEndpoints  the endpoints of the head no call of the
     *                         system reaches
     * @param truncated        whether there are more changes than it
     *                         follows
     */
    public record ImpactSummary(
        ScanSummary base,
        ScanSummary head,
        List<ScanGraph.TypeChanges> summary,
        List<ImpactChange> changes,
        List<String> removedEndpoints,
        List<String> addedEndpoints,
        List<String> movedEndpoints,
        UnusedEndpointsSummary unusedEndpoints,
        List<BrokenCallSummary> brokenCalls,
        List<AffectedProjectSummary> projects,
        boolean truncated
    ) {
    }

    /**
     * Endpoints no call Graphnous found reaches: possibly unused, as
     * clients outside the system may still call them.
     *
     * @param noLongerCalled those calls reached in the base, and none in
     *                       the head
     * @param notCalledYet   those the head adds or moves
     * @param others         the rest, the first of them
     * @param otherCount     how many the rest are
     */
    public record UnusedEndpointsSummary(
        List<String> noLongerCalled,
        List<String> notCalledYet,
        List<String> others,
        int otherCount
    ) {
    }

    public record ScanGraphSummary(String status, List<ScanGraph.Target> targets) {
    }

    /**
     * One place that uses what was asked about, as {@link ScanUsages.Usage}
     * with the scan and project it is in by id.
     */
    public record UsageSummary(
        String relationship,
        ScanUsages.Node user,
        String owner,
        String file,
        Integer line,
        ScanUsages.Node target,
        String targetOwner,
        int depth,
        List<ScanUsages.Argument> arguments,
        UUID scanId,
        UUID projectId,
        ScanUsages.Request request
    ) {
    }

    /**
     * @param truncated whether there are more usages than it lists
     */
    public record UsagesSummary(ScanUsages.Node subject, List<UsageSummary> usages, boolean truncated) {
    }

    @Tool(
        name = "listSystems",
        description = "Lists the systems of the user's organization, by name. A system groups the projects that make up one application."
    )
    public List<SystemSummary> listSystems() {
        return systemService.getSystems(context(), new PageQuery(0, LIMIT, new Sort("name", Sort.Direction.ASC)))
            .content()
            .stream()
            .map(system -> new SystemSummary(system.id().id(), system.name(), system.description()))
            .toList();
    }

    @Tool(
        name = "listProjects",
        description = "Lists the projects of a system, by name. A project is a git repository, or a directory in one, that Graphnous scans."
    )
    public List<ProjectSummary> listProjects(
        @ToolParam(description = "The id of the system, from listSystems") final String systemId
    ) {
        return projectService.getProjects(
                context(),
                new PageQuery(0, LIMIT, new Sort("name", Sort.Direction.ASC)),
                new System.SystemId(UUID.fromString(systemId))
            )
            .content()
            .stream()
            .map(GraphnousTools::summary)
            .toList();
    }

    @Tool(
        name = "listScans",
        description = "Lists the most recent scans of a project, newest first, with their branch, revision and status: "
            + "PENDING, QUEUED, RUNNING, COMPLETED or FAILED; only those of one branch when it is given, such as to "
            + "compare the newest COMPLETED scan of a branch with that of main."
    )
    public List<ScanSummary> listScans(
        @ToolParam(description = "The id of the project, from listProjects") final String projectId,
        @ToolParam(description = "Only the scans of this branch, e.g. main", required = false) final String branch
    ) {
        final var context = context();
        final var project = new Project.ProjectId(UUID.fromString(projectId));
        final var wanted = branch == null || branch.isBlank() ? null : branch.trim();
        final var scans = new ArrayList<ScanSummary>();

        // Newest first, through as many as it takes to list LIMIT of the branch
        for (var page = 0; page < BRANCH_PAGES && scans.size() < LIMIT; page++) {
            final var found = scanService.getScans(context, new PageQuery(page, LIMIT, Sort.defaultSort()), project);

            found.content()
                .stream()
                .filter(scan -> wanted == null || (scan.revision() != null && wanted.equals(scan.revision().branch())))
                .limit(LIMIT - scans.size())
                .map(GraphnousTools::summary)
                .forEach(scans::add);

            if (wanted == null || page + 1 >= found.totalPages()) {
                break;
            }
        }

        return scans;
    }

    @Tool(
        name = "analyzeImpact",
        description = "Analyzes what a change of a project affects, from one of its scans (base, e.g. the newest "
            + "COMPLETED scan of main) to another (head, e.g. of a branch), from listScans: the classes, methods "
            + "and fields the head removes or changes, most used first, each with how many places use it and the "
            + "first of them, in this project and the others of the system; the endpoints it removes and adds; "
            + "the HTTP calls of the system that reached an endpoint of the project and reach none in the head, "
            + "with where each is; and the projects affected. A changed Java method signature is a removed method. "
            + "Start here before changing or merging a branch; follow an id further with findUsages."
    )
    public ImpactSummary analyzeImpact(
        @ToolParam(description = "The id of the scan to compare from, e.g. of main, from listScans") final String baseScanId,
        @ToolParam(description = "The id of the scan to compare to, e.g. of the branch, from listScans") final String headScanId,
        @ToolParam(description = "How far out to follow the usages of each change, 1 to " + ScanUsageService.MAX_DEPTH
            + "; 1, only what uses it itself, when left out", required = false) final Integer depth
    ) {
        return impactSummary(impactAnalysisService.analyze(context(), scanId(baseScanId), scanId(headScanId), depth));
    }

    /**
     * The report as analyzeImpact gives it, with the names of the projects.
     */
    public ImpactSummary impactSummary(final ImpactReport report) {
        final var context = context();
        final var projects = projectNames(context, projectService.getProject(context, report.head().projectId()).systemId());

        return new ImpactSummary(
            summary(report.base()),
            summary(report.head()),
            report.summary(),
            report.changes()
                .stream()
                .map(change -> new ImpactChange(
                    change.kind().name(),
                    change.id(),
                    change.type(),
                    change.name(),
                    change.properties(),
                    change.replacement(),
                    change.usageCount(),
                    change.testUsageCount(),
                    change.truncated(),
                    change.usages().stream().map(usage -> summary(usage, change.projects())).toList()
                ))
                .toList(),
            report.endpoints().removed().stream().map(ScanGraph.Snapshot::name).toList(),
            report.endpoints().added().stream().map(ScanGraph.Snapshot::name).toList(),
            report.endpoints().moved().stream().map(move -> move.from().name() + " -> " + move.to().name()).toList(),
            new UnusedEndpointsSummary(
                endpointNames(report.unused().noLongerCalled()),
                endpointNames(report.unused().notCalledYet()),
                endpointNames(report.unused().others()),
                report.unused().otherCount()
            ),
            report.brokenCalls()
                .stream()
                .map(broken -> new BrokenCallSummary(
                    broken.call().httpMethod(),
                    broken.call().url(),
                    broken.call().client(),
                    broken.call().projectId().id(),
                    projects.get(broken.call().projectId().id()),
                    broken.caller(),
                    broken.endpoints()
                        .stream()
                        .map(endpoint -> (endpoint.httpMethod() == null ? "" : endpoint.httpMethod() + " ") + endpoint.url())
                        .toList()
                ))
                .toList(),
            report.projects()
                .stream()
                .map(project -> new AffectedProjectSummary(
                    project.projectId().id(),
                    projects.get(project.projectId().id()),
                    project.usages(),
                    project.brokenCalls()
                ))
                .toList(),
            report.truncated()
        );
    }

    @Tool(
        name = "compareScans",
        description = "Compares two scans of a project, such as the newest COMPLETED scan of a branch (head) with that "
            + "of main (base), from listScans: what the head adds, removes and changes, node by node, as files, "
            + "classes, methods, fields, modules, packages, HTTP endpoints and dependencies: an endpoint whose path "
            + "or method changed is removed and added. Gives how many of each type changed, "
            + "and lists the nodes with the properties that differ, such as a method's parameter types or a file's "
            + "checksum, each with its id for findUsages: in the head scan for added and changed nodes, in the base "
            + "scan for removed ones. Lists classes, methods, endpoints and dependencies unless asked for other types; the "
            + "counts are of every type."
    )
    public ComparisonSummary compareScans(
        @ToolParam(description = "The id of the scan to compare from, e.g. of main, from listScans") final String baseScanId,
        @ToolParam(description = "The id of the scan to compare to, e.g. of the branch, from listScans") final String headScanId,
        @ToolParam(description = "Only nodes of these types: ScanTarget, Module, Package, File, Class, Method, Field, "
            + "Endpoint or Dependency; Class, Method, Endpoint and Dependency when left out", required = false) final List<String> types
    ) {
        final var context = context();
        final var base = scanService.getScan(context, scanId(baseScanId));
        final var head = scanService.getScan(context, scanId(headScanId));

        final var asked = types == null
            ? Set.<String>of()
            : types.stream()
                .filter(type -> type != null && !type.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        final var listed = asked.isEmpty() ? COMPARED_TYPES : asked;

        final var comparison = scanGraphService.compare(context, base.id(), head.id(), listed, COMPARISON_LIMIT);

        return new ComparisonSummary(
            summary(base),
            summary(head),
            comparison.summary(),
            comparison.added()
                .stream()
                .map(node -> new NodeSummary(id(head, node.key(), node.type()), node.type(), node.name(), node.properties()))
                .toList(),
            comparison.removed()
                .stream()
                .map(node -> new NodeSummary(id(base, node.key(), node.type()), node.type(), node.name(), node.properties()))
                .toList(),
            comparison.changed()
                .stream()
                .map(node -> new ChangeSummary(id(head, node.key(), node.type()), node.type(), node.name(), node.properties()))
                .toList(),
            comparison.truncated()
        );
    }

    @Tool(
        name = "getScanGraph",
        description = "Gets the outline of a scan's graph: its targets, each a language and build system, and their modules, "
            + "with how many files, packages, classes, methods and dependencies each module has; methods include "
            + "the functions declared outside any class. "
            + "Only a COMPLETED scan has a graph; to answer about a project, use its newest COMPLETED scan from listScans."
    )
    public ScanGraphSummary getScanGraph(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId
    ) {
        final var context = context();
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
    public List<ScanGraph.ClassSummary> findClasses(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId,
        @ToolParam(description = "Part of the name, e.g. Order or com.example.orders", required = false) final String query
    ) {
        return scanGraphService.findClasses(context(), scanId(scanId), query, LIMIT);
    }

    @Tool(
        name = "getClass",
        description = "Gets a class of a scan: its kind, modifiers, file, package, supertypes, the classes of the scan "
            + "that extend or implement it, its annotations with their arguments, and its methods and fields."
    )
    public ScanGraph.ClassDetails getClass(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId,
        @ToolParam(description = "The qualified name of the class, from findClasses") final String qualifiedName
    ) {
        return scanGraphService.getClass(context(), scanId(scanId), qualifiedName);
    }

    @Tool(
        name = "findFile",
        description = "Finds a file of a git repository in the newest completed scan of its project, as someone "
            + "working on the file knows it: by the repository's url, such as `git remote get-url origin` gives, "
            + "and the file's path in the repository. Gives the project, the scan and its revision, and what the "
            + "file declares, its classes, methods, functions and fields, with their ids for findUsages; only "
            + "what is named so when a symbol is given. The quickest way to what the user is working on; the "
            + "scan may be older than their checkout, so compare its revision."
    )
    public FileSummary findFile(
        @ToolParam(description = "The url of the repository, e.g. git@github.com:acme/shop.git or https://github.com/acme/shop") final String gitUrl,
        @ToolParam(description = "The path of the file in the repository, from its root, e.g. server/src/main/java/com/acme/OrderService.java") final String path,
        @ToolParam(description = "The name of a class, method, function or field the file declares, e.g. OrderService, "
            + "create or OrderService.create, ignoring case; everything it declares when left out", required = false) final String symbol
    ) {
        final var found = scanSourceService.find(context(), gitUrl, path, symbol);
        final var file = found.file();

        return new FileSummary(
            summary(found.project()),
            summary(found.scan()),
            found.path(),
            file.id(),
            file.target(),
            file.module(),
            file.language(),
            file.sourceSet(),
            found.declarations()
        );
    }

    @Tool(
        name = "listEndpoints",
        description = "Lists the HTTP endpoints the projects of a system serve, such as the mappings of Spring "
            + "controllers and the routes of Next.js, in the newest completed scan of each project, with the method "
            + "that serves each and every call in the system that requests it: which project, method, file and line. "
            + "Before changing an endpoint's path, method or parameters, see what calls it; with uncalledOnly, find "
            + "the endpoints nothing in the system calls. Calls are matched by url and method, as written."
    )
    public EndpointsSummary listEndpoints(
        @ToolParam(description = "The id of the system, from listSystems") final String systemId,
        @ToolParam(description = "Part of the url, ignoring case, e.g. /orders", required = false) final String query,
        @ToolParam(description = "Only the endpoints of this project, its id from listProjects", required = false) final String projectId,
        @ToolParam(description = "Only the endpoints no call of the system requests", required = false) final Boolean uncalledOnly
    ) {
        final var context = context();
        final var system = new System.SystemId(UUID.fromString(systemId));

        final var found = httpLinkService.getEndpoints(
            context,
            system,
            new HttpLinks.EndpointFilter(
                query,
                projectId(projectId),
                Boolean.TRUE.equals(uncalledOnly)
            ),
            ENDPOINT_LIMIT
        );

        final var projects = projectNames(context, system);

        final var endpoints = found.endpoints()
            .stream()
            .map(endpoint -> new EndpointSummary(
                endpoint.endpoint().httpMethod(),
                endpoint.endpoint().url(),
                endpoint.endpoint().projectId().id(),
                projects.get(endpoint.endpoint().projectId().id()),
                endpoint.handler(),
                endpoint.calls().stream().map(site -> summary(site, projects)).toList()
            ))
            .toList();

        return new EndpointsSummary(endpoints, found.total(), found.truncated());
    }

    @Tool(
        name = "listUnmatchedCalls",
        description = "Lists the HTTP calls the projects of a system make that request none of its endpoints, in the "
            + "newest completed scan of each project, with the method, file and line of each: calls of an API "
            + "outside the system, or of an endpoint that was renamed or removed, which no longer reach it. "
            + "Calls whose url the scan does not know are left out. Calls are matched by url and method, as written."
    )
    public CallsSummary listUnmatchedCalls(
        @ToolParam(description = "The id of the system, from listSystems") final String systemId,
        @ToolParam(description = "Part of the url, ignoring case, e.g. /orders", required = false) final String query,
        @ToolParam(description = "Only the calls of this project, its id from listProjects", required = false) final String projectId
    ) {
        final var context = context();
        final var system = new System.SystemId(UUID.fromString(systemId));

        final var found = httpLinkService.getUnmatchedCalls(
            context,
            system,
            new HttpLinks.CallFilter(query, projectId(projectId)),
            CALL_LIMIT
        );
        final var projects = projectNames(context, system);

        return new CallsSummary(
            found.calls().stream().map(site -> summary(site, projects)).toList(),
            found.total(),
            found.truncated()
        );
    }

    @Tool(
        name = "findMethods",
        description = "Finds the methods, constructors and functions of a scan whose name or qualified name contains "
            + "the query, ignoring case, optionally only those of one class, with their id in the scan's graph, "
            + "which findUsages takes. To find those of a file the user is working on, findFile is quicker."
    )
    public List<ScanMethod> findMethods(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId,
        @ToolParam(description = "Part of the name, e.g. create or OrderService.create", required = false) final String query,
        @ToolParam(description = "The qualified name of the class they belong to, from findClasses", required = false) final String className
    ) {
        return scanMethodService.getMethods(
                context(),
                scanId(scanId),
                new ScanMethodFilter(query, null, null, className),
                new PageQuery(0, LIMIT, new Sort("qualifiedName", Sort.Direction.ASC))
            )
            .content();
    }

    @Tool(
        name = "findUsages",
        description = "Finds what uses a class, method, function or field of a scan: what changing it affects. "
            + "Lists each place that calls, instantiates, extends, implements, overrides, reads or writes it, "
            + "with its file and line and what it passes, and over HTTP the calls in other projects of the system "
            + "that request the endpoint a method serves. "
            + "Depth 1 is only what uses it itself; 2 also what calls those, up to " + ScanUsageService.MAX_DEPTH + ". "
            + "Only scans stored with their usages have any."
    )
    public UsagesSummary findUsages(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId,
        @ToolParam(description = "The id of the class, method, function or field, from findFile or findMethods") final String nodeId,
        @ToolParam(description = "How far out to follow, 1 to " + ScanUsageService.MAX_DEPTH + "; 1 when left out", required = false) final Integer depth
    ) {
        final var found = scanUsageService.getUsages(context(), scanId(scanId), nodeId, depth);

        final var usages = found.usages()
            .stream()
            .limit(USAGE_LIMIT)
            .map(usage -> summary(usage, found.projects()))
            .toList();

        return new UsagesSummary(found.subject(), usages, found.truncated() || found.usages().size() > USAGE_LIMIT);
    }

    @Tool(
        name = "findAnnotated",
        description = "Finds the classes, methods and fields of a scan with an annotation, with its arguments as JSON, "
            + "and the functions and variables declared outside any class with a decorator or attribute, "
            + "e.g. RestController or GetMapping for the endpoints of a Spring application, or Entity for its "
            + "persisted types."
    )
    public List<ScanGraph.AnnotatedElement> findAnnotated(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId,
        @ToolParam(description = "The simple or qualified name of the annotation, e.g. GetMapping or "
            + "org.springframework.web.bind.annotation.GetMapping") final String annotation
    ) {
        return scanGraphService.findAnnotated(context(), scanId(scanId), annotation, LIMIT);
    }

    @Tool(
        name = "listDependencies",
        description = "Lists the libraries each module of a scan depends on, with their version and scope."
    )
    public List<ScanGraph.Dependency> listDependencies(
        @ToolParam(description = "The id of the scan, from listScans") final String scanId
    ) {
        return scanGraphService.getDependencies(context(), scanId(scanId));
    }

    /**
     * The names of the projects of the system, by id: those calls and
     * endpoints are of, read once.
     */
    private Map<UUID, String> projectNames(final RequestContext context, final System.SystemId system) {
        final var names = new HashMap<UUID, String>();

        projectService.getProjects(context, new PageQuery(0, 100, new Sort("name", Sort.Direction.ASC)), system)
            .content()
            .forEach(project -> names.put(project.id().id(), project.name()));

        return names;
    }

    private static List<String> endpointNames(final List<ImpactReport.UnusedEndpoint> endpoints) {
        return endpoints.stream()
            .map(unused -> (unused.endpoint().httpMethod() == null ? "" : unused.endpoint().httpMethod() + " ") + unused.endpoint().url())
            .toList();
    }

    private static UsageSummary summary(final ScanUsages.Usage usage, final Map<Scan.ScanId, Project.ProjectId> projects) {
        return new UsageSummary(
            usage.relationship(),
            usage.user(),
            usage.owner(),
            usage.file(),
            usage.line(),
            usage.target(),
            usage.targetOwner(),
            usage.depth(),
            usage.arguments(),
            usage.scanId() == null ? null : usage.scanId().id(),
            usage.scanId() == null || !projects.containsKey(usage.scanId()) ? null : projects.get(usage.scanId()).id(),
            usage.request()
        );
    }

    private static CallSummary summary(final HttpLinks.CallSite site, final Map<UUID, String> projects) {
        return new CallSummary(
            site.call().httpMethod(),
            site.call().url(),
            site.call().client(),
            site.call().projectId().id(),
            projects.get(site.call().projectId().id()),
            site.caller(),
            site.match() == null ? null : site.match().name()
        );
    }

    private static Project.ProjectId projectId(final String projectId) {
        return projectId == null || projectId.isBlank() ? null : new Project.ProjectId(UUID.fromString(projectId));
    }

    private static ProjectSummary summary(final Project project) {
        return new ProjectSummary(
            project.id().id(),
            project.name(),
            project.description(),
            project.gitUrl(),
            project.path()
        );
    }

    private static ScanSummary summary(final Scan scan) {
        return new ScanSummary(
            scan.id().id(),
            scan.status().name(),
            scan.revision() == null ? null : scan.revision().branch(),
            scan.revision() == null ? null : scan.revision().revision(),
            scan.revision() == null ? null : scan.revision().requestedRevision(),
            scan.createdAt(),
            scan.startedAt()
        );
    }

    private RequestContext context() {
        return contextProvider.get();
    }

    /**
     * The id of a node of the scan: its key after the scan's id. A
     * dependency has none.
     */
    private static String id(final Scan scan, final String key, final String type) {
        return "Dependency".equals(type) ? null : scan.id().id() + "|" + key;
    }

    private static Scan.ScanId scanId(final String scanId) {
        return new Scan.ScanId(UUID.fromString(scanId));
    }
}
