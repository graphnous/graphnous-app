package dev.graphnous.api.system.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.api.project.v1.ProjectMapper;
import dev.graphnous.api.scan.v1.ScanMapper;
import dev.graphnous.api.v1.generated.project.CreateProjectRequest;
import dev.graphnous.api.v1.generated.project.Project;
import dev.graphnous.api.v1.generated.project.ProjectPage;
import dev.graphnous.api.v1.generated.scan.ScanPage;
import dev.graphnous.api.v1.generated.system.CreateSystemRequest;
import dev.graphnous.api.v1.generated.system.System;
import dev.graphnous.api.v1.generated.system.SystemPage;
import dev.graphnous.api.v1.generated.system.UpdateSystemRequest;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.CreateProjectCommand;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.dependencies.ScanDependencyFilter;
import dev.graphnous.application.scan.dependencies.ScanDependencyService;
import dev.graphnous.application.scan.dependencies.SystemDependency;
import dev.graphnous.application.scan.dependencies.SystemLibrary;
import dev.graphnous.application.scan.links.HttpLinkService;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.stats.ScanStatService;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.application.system.CreateSystemCommand;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.application.system.UpdateSystemCommand;
import dev.graphnous.domain.system.System.SystemId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

@RestController
@RequestMapping("/api/v1/systems")
public class SystemController {

    private final SystemService systemService;
    private final ProjectService projectService;
    private final ScanService scanService;
    private final ScanStatService scanStatService;
    private final ScanDependencyService scanDependencyService;
    private final HttpLinkService httpLinkService;

    private final RequestContextProvider contextProvider;

    private final SystemMapper systemMapper;
    private final ProjectMapper projectMapper;
    private final ScanMapper scanMapper;

    public SystemController(
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final ScanStatService scanStatService,
        final ScanDependencyService scanDependencyService,
        final HttpLinkService httpLinkService,
        final RequestContextProvider contextProvider
    ) {
        this.systemService = systemService;
        this.projectService = projectService;
        this.scanService = scanService;
        this.scanStatService = scanStatService;
        this.scanDependencyService = scanDependencyService;
        this.httpLinkService = httpLinkService;

        this.contextProvider = contextProvider;

        this.systemMapper = new SystemMapper();
        this.projectMapper = new ProjectMapper();
        this.scanMapper = new ScanMapper();
    }

    @GetMapping
    public ResponseEntity<SystemPage> getSystems(
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "name") @Pattern(regexp = "name|createdAt") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var response = this.systemService.getSystems(
            this.contextProvider.get(),
            new PageQuery(
                pageNumber,
                size,
                new Sort(
                    sort,
                    Sort.Direction.fromValue(direction)
                )
            )
        );

        return ResponseEntity.ok(
            this.systemMapper.toPage(response)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<System> getSystem(
        @PathVariable("id") final UUID id
    ) {
        final var system = this.systemService.getSystem(
            this.contextProvider.get(),
            new SystemId(id)
        );

        return ResponseEntity.ok(
            this.systemMapper.fromDomain(system)
        );
    }

    @PostMapping()
    public ResponseEntity<System> createSystem(
        @RequestBody @Valid final CreateSystemRequest systemRequest
    ) {
        final var system = this.systemService.create(
            contextProvider.get(),
            new CreateSystemCommand(systemRequest.getName(), systemRequest.getDescription())
        );

        final var location = fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(system.id().id())
            .toUri();

        return ResponseEntity
            .created(location)
            .body(this.systemMapper.fromDomain(system));
    }

    @PutMapping("/{id}")
    public ResponseEntity<System> updateSystem(
        @PathVariable("id") final UUID id,
        @RequestBody @Valid final UpdateSystemRequest systemRequest
    ) {

        final var system = this.systemService.update(
            contextProvider.get(),
            new UpdateSystemCommand(new SystemId(id), systemRequest.getName(), systemRequest.getDescription())
        );

        return ResponseEntity
            .ok()
            .body(this.systemMapper.fromDomain(system));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSystem(
        @PathVariable("id") final UUID id
    ) {
        this.systemService.delete(
            this.contextProvider.get(),
            new SystemId(id)
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/projects")
    public ResponseEntity<ProjectPage> getProjects(
        @PathVariable(name = "id") final UUID systemId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "name") @Pattern(regexp = "name|createdAt") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var response = this.projectService.getProjects(
            this.contextProvider.get(),
            new PageQuery(
                pageNumber,
                size,
                new Sort(
                    sort,
                    Sort.Direction.fromValue(direction)
                )
            ),
            new SystemId(systemId)
        );

        return ResponseEntity.ok(
            this.projectMapper.toPage(response)
        );
    }

    /**
     * The scans of every project of the system, a page at a time.
     * <p>
     * Not in the published OpenAPI spec yet; its page is the one of a
     * project's scans.
     */
    @GetMapping("/{id}/scans")
    public ResponseEntity<ScanPage> getScans(
        @PathVariable(name = "id") final UUID systemId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "createdAt") @Pattern(regexp = "createdAt|updatedAt") final String sort,
        @RequestParam(name = "direction", defaultValue = "desc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var response = this.scanService.getSystemScans(
            this.contextProvider.get(),
            new PageQuery(
                pageNumber,
                size,
                new Sort(
                    sort,
                    Sort.Direction.fromValue(direction)
                )
            ),
            new SystemId(systemId)
        );

        return ResponseEntity.ok(
            this.scanMapper.toPage(response)
        );
    }

    /**
     * Not in the published OpenAPI spec yet, so its model is its own.
     *
     * @param numberOfProjectsScanned the projects with a scan that has stats
     * @param numberOfModulesScanned  the modules of those projects
     * @param languages               the files of those projects by
     *                                language, such as JAVA; each a path
     *                                from the root of its project's
     *                                repository
     * @param numberOfClassesParsed   the classes of those projects
     * @param numberOfMethodsParsed   the methods of those projects
     * @param numberOfProjects        the projects of the system, scanned
     *                                or not
     * @param numberOfScans           the scans of those projects, whatever
     *                                their status
     */
    public record SystemStatsResponse(
        UUID systemId,
        int numberOfProjectsScanned,
        int numberOfModulesScanned,
        Map<String, List<String>> languages,
        int numberOfClassesParsed,
        int numberOfMethodsParsed,
        int numberOfProjects,
        int numberOfScans
    ) {
    }

    /**
     * The stats of the system: those of the latest scan that has any of
     * each of its projects, added up, with how many projects it has and
     * how many scans they have.
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<SystemStatsResponse> getStats(
        @PathVariable(name = "id") final UUID systemId
    ) {
        final var stats = this.scanStatService.getSystemStats(
            this.contextProvider.get(),
            new SystemId(systemId)
        );

        return ResponseEntity.ok(
            new SystemStatsResponse(
                stats.systemId().id(),
                stats.numberOfProjectsScanned(),
                stats.numberOfModulesScanned(),
                stats.languages(),
                stats.numberOfClassesParsed(),
                stats.numberOfMethodsParsed(),
                stats.numberOfProjects(),
                stats.numberOfScans()
            )
        );
    }

    /**
     * Not in the published OpenAPI spec yet, so its model is its own.
     *
     * @param id        the library's id in the scan's graph, the focus of
     *                  GET /scans/{id}/graph
     * @param projectId the project whose module depends on it
     * @param scanId    the scan of the project it was found in
     * @param module    the path of the module that depends on it
     * @param name      such as org.slf4j:slf4j-api
     * @param scope     such as compile or test, if the build system has scopes
     */
    public record SystemDependencyResponse(
        String id,
        UUID projectId,
        UUID scanId,
        String module,
        String name,
        String version,
        String scope
    ) {
    }

    public record SystemDependencyPage(
        List<SystemDependencyResponse> content,
        int page,
        int size,
        int totalElements,
        int totalPages
    ) {
    }

    /**
     * The libraries the system's projects depend on: those of the latest
     * completed scan of each project, one per project, module and library.
     * Every filter is optional.
     *
     * @param query  part of the library's name, ignoring case
     * @param module the path of the module that depends on them
     * @param scope  such as compile or test, ignoring case
     */
    @GetMapping("/{id}/dependencies")
    public ResponseEntity<SystemDependencyPage> getDependencies(
        @PathVariable(name = "id") final UUID systemId,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "module", required = false) final String module,
        @RequestParam(name = "scope", required = false) final String scope,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "name") @Pattern(regexp = "name|module|scope") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var dependencies = this.scanDependencyService.getSystemDependencies(
            this.contextProvider.get(),
            new SystemId(systemId),
            new ScanDependencyFilter(query, module, scope),
            new PageQuery(
                pageNumber,
                size,
                new Sort(
                    sort,
                    Sort.Direction.fromValue(direction)
                )
            )
        );

        return ResponseEntity.ok(
            new SystemDependencyPage(
                dependencies.content().stream().map(SystemController::fromDomain).toList(),
                dependencies.page(),
                dependencies.size(),
                dependencies.totalElements(),
                dependencies.totalPages()
            )
        );
    }

    private static SystemDependencyResponse fromDomain(final SystemDependency dependency) {
        return new SystemDependencyResponse(
            dependency.dependency().id(),
            dependency.projectId().id(),
            dependency.scanId().id(),
            dependency.dependency().module(),
            dependency.dependency().name(),
            dependency.dependency().version(),
            dependency.dependency().scope()
        );
    }

    /**
     * Not in the published OpenAPI spec yet, so its model is its own.
     *
     * @param name     such as org.slf4j:slf4j-api
     * @param versions each version of it in use, with the projects that
     *                 use it; more than one is drift between them
     */
    public record SystemLibraryResponse(
        String name,
        List<SystemLibraryVersion> versions
    ) {
    }

    /**
     * @param version  null when the library is used without one
     * @param projects the ids of the projects with a module that depends
     *                 on it
     */
    public record SystemLibraryVersion(
        String version,
        List<UUID> projects
    ) {
    }

    public record SystemLibraryPage(
        List<SystemLibraryResponse> content,
        int page,
        int size,
        int totalElements,
        int totalPages
    ) {
    }

    /**
     * The libraries the system depends on, each once, with every version
     * its projects use: as the latest completed scan of each project found
     * them.
     *
     * @param query part of the library's name, ignoring case
     * @param drift only the libraries used in more than one version
     * @param sort  by name, or by how many versions are in use
     */
    @GetMapping("/{id}/libraries")
    public ResponseEntity<SystemLibraryPage> getLibraries(
        @PathVariable(name = "id") final UUID systemId,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "drift", defaultValue = "false") final boolean drift,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "name") @Pattern(regexp = "name|versions") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var libraries = this.scanDependencyService.getSystemLibraries(
            this.contextProvider.get(),
            new SystemId(systemId),
            query,
            drift,
            new PageQuery(
                pageNumber,
                size,
                new Sort(
                    sort,
                    Sort.Direction.fromValue(direction)
                )
            )
        );

        return ResponseEntity.ok(
            new SystemLibraryPage(
                libraries.content().stream()
                    .map(library -> new SystemLibraryResponse(
                        library.name(),
                        library.versions().stream()
                            .map(version -> new SystemLibraryVersion(
                                version.version(),
                                version.projects().stream().map(project -> project.id()).toList()
                            ))
                            .toList()
                    ))
                    .toList(),
                libraries.page(),
                libraries.size(),
                libraries.totalElements(),
                libraries.totalPages()
            )
        );
    }

    /**
     * Not in the published OpenAPI spec yet, so its model is its own.
     *
     * @param links     each HTTP call with an endpoint it requests
     * @param calls     how many HTTP calls the scans have, linked or not
     * @param endpoints how many endpoints the scans have, linked or not
     */
    public record SystemLinksResponse(
        List<SystemLink> links,
        int calls,
        int endpoints
    ) {
    }

    /**
     * @param match           PATH when the call's whole path is the
     *                        endpoint's; SUFFIX when it is the end of it,
     *                        for a call that starts with a base only known
     *                        when it runs
     * @param crossesProjects whether the call and the endpoint are of
     *                        different projects
     */
    public record SystemLink(
        SystemLinkCall call,
        SystemLinkEndpoint endpoint,
        String match,
        boolean crossesProjects
    ) {
    }

    /**
     * @param id         the call's id in its scan's graph, the focus of
     *                   GET /scans/{id}/graph
     * @param httpMethod null when the scan does not say
     * @param url        as written, with what is only known when it runs
     *                   in braces
     * @param client     what makes the call, such as fetch
     */
    public record SystemLinkCall(
        String id,
        UUID scanId,
        UUID projectId,
        String httpMethod,
        String url,
        String client
    ) {
    }

    /**
     * @param id         the endpoint's id in its scan's graph
     * @param httpMethod such as GET, or ANY
     */
    public record SystemLinkEndpoint(
        String id,
        UUID scanId,
        UUID projectId,
        String httpMethod,
        String url
    ) {
    }

    /**
     * How the system's projects are linked over HTTP: which HTTP calls
     * request which endpoints, as far as their urls and methods say, from
     * what enhancers added to the scans. The links of the latest completed
     * scans are the ones stored in the graph; those of other scans are
     * found anew each time.
     *
     * @param scans the scans to link, such as the ones a graph shows; the
     *              latest completed scan of each project when missing
     */
    @GetMapping("/{id}/links")
    public ResponseEntity<SystemLinksResponse> getLinks(
        @PathVariable(name = "id") final UUID systemId,
        @RequestParam(name = "scans", required = false) final List<UUID> scans
    ) {
        final var found = this.httpLinkService.getLinks(
            this.contextProvider.get(),
            new SystemId(systemId),
            scans == null ? null : scans.stream().map(Scan.ScanId::new).toList()
        );

        return ResponseEntity.ok(
            new SystemLinksResponse(
                found.links().stream().map(SystemController::fromDomain).toList(),
                found.calls(),
                found.endpoints()
            )
        );
    }

    private static SystemLink fromDomain(final HttpLinks.Link link) {
        final var call = link.call();
        final var endpoint = link.endpoint();

        return new SystemLink(
            new SystemLinkCall(
                call.id(), call.scanId().id(), call.projectId().id(), call.httpMethod(), call.url(), call.client()
            ),
            new SystemLinkEndpoint(
                endpoint.id(), endpoint.scanId().id(), endpoint.projectId().id(), endpoint.httpMethod(), endpoint.url()
            ),
            link.match().name(),
            link.crossesProjects()
        );
    }

    @PostMapping("/{id}/projects")
    public ResponseEntity<Project> createProject(
        @PathVariable(name = "id") final UUID systemId,
        @RequestBody final CreateProjectRequest request
    ) {
        final var project = this.projectService.create(
            this.contextProvider.get(),
            new CreateProjectCommand(
                request.getName(),
                request.getDescription(),
                request.getGitUrl(),
                request.getPath(),
                new SystemId(systemId)
            )
        );

        // Projects are addressed on their own, not below their system
        final var location = fromCurrentContextPath()
            .path("/api/v1/projects/{id}")
            .buildAndExpand(project.id().id())
            .toUri();

        return ResponseEntity.created(location).body(
            this.projectMapper.fromDomain(project)
        );
    }

}