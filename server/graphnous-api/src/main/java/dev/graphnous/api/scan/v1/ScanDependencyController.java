package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.dependencies.ScanDependency;
import dev.graphnous.application.scan.dependencies.ScanDependencyFilter;
import dev.graphnous.application.scan.dependencies.ScanDependencyService;
import dev.graphnous.domain.scan.Scan;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The libraries the modules of a scan depend on, a page at a time.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanDependencyController {

    private final RequestContextProvider contextProvider;
    private final ScanDependencyService scanDependencyService;

    public ScanDependencyController(
        final RequestContextProvider contextProvider,
        final ScanDependencyService scanDependencyService
    ) {
        this.contextProvider = contextProvider;
        this.scanDependencyService = scanDependencyService;
    }

    /**
     * @param id     the library's id in the scan's graph, the focus of
     *               GET /scans/{id}/graph
     * @param module the path of the module that depends on it
     * @param name   such as org.slf4j:slf4j-api
     * @param scope  such as compile or test, if the build system has scopes
     */
    public record ScanDependencyResponse(
        String id,
        String module,
        String name,
        String version,
        String scope
    ) {
    }

    /**
     * One per module and library. Every filter is optional.
     *
     * @param query  part of the library's name, ignoring case
     * @param module the path of the module that depends on them
     * @param scope  such as compile or test, ignoring case
     */
    @GetMapping("/{id}/dependencies")
    public ResponseEntity<ScanItemPage<ScanDependencyResponse>> getDependencies(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "module", required = false) final String module,
        @RequestParam(name = "scope", required = false) final String scope,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "name") @Pattern(regexp = "name|module|scope") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var dependencies = scanDependencyService.getDependencies(
            contextProvider.get(),
            new Scan.ScanId(id),
            new ScanDependencyFilter(query, module, scope),
            new PageQuery(page, size, new Sort(sort, Sort.Direction.fromValue(direction)))
        );

        return ResponseEntity.ok(ScanItemPage.of(dependencies, ScanDependencyController::fromDomain));
    }

    private static ScanDependencyResponse fromDomain(final ScanDependency dependency) {
        return new ScanDependencyResponse(
            dependency.id(),
            dependency.module(),
            dependency.name(),
            dependency.version(),
            dependency.scope()
        );
    }
}
