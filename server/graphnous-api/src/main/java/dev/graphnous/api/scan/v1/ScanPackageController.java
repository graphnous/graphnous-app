package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.packages.ScanPackage;
import dev.graphnous.application.scan.packages.ScanPackageFilter;
import dev.graphnous.application.scan.packages.ScanPackageService;
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
 * The packages a scan found, a page at a time: Java, Python and Go
 * packages and Rust modules. TypeScript and JavaScript have none.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanPackageController {

    private final RequestContextProvider contextProvider;
    private final ScanPackageService scanPackageService;

    public ScanPackageController(
        final RequestContextProvider contextProvider,
        final ScanPackageService scanPackageService
    ) {
        this.contextProvider = contextProvider;
        this.scanPackageService = scanPackageService;
    }

    /**
     * @param id        the package's id in the scan's graph, the focus of
     *                  GET /scans/{id}/graph
     * @param module    the path of the module it is in
     * @param classes   the classes it contains, nested ones included
     * @param functions the functions its files declare outside any class
     * @param variables the variables its files declare outside any class
     */
    public record ScanPackageResponse(
        String id,
        String module,
        String name,
        String qualifiedName,
        long classes,
        long functions,
        long variables
    ) {
    }

    /**
     * Every filter is optional.
     *
     * @param query  part of the name or qualified name, ignoring case
     * @param module the path of the module they are in
     */
    @GetMapping("/{id}/packages")
    public ResponseEntity<ScanItemPage<ScanPackageResponse>> getPackages(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "module", required = false) final String module,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "qualifiedName") @Pattern(regexp = "qualifiedName|name") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var packages = scanPackageService.getPackages(
            contextProvider.get(),
            new Scan.ScanId(id),
            new ScanPackageFilter(query, module),
            new PageQuery(page, size, new Sort(sort, Sort.Direction.fromValue(direction)))
        );

        return ResponseEntity.ok(ScanItemPage.of(packages, ScanPackageController::fromDomain));
    }

    private static ScanPackageResponse fromDomain(final ScanPackage pkg) {
        return new ScanPackageResponse(
            pkg.id(),
            pkg.module(),
            pkg.name(),
            pkg.qualifiedName(),
            pkg.classes(),
            pkg.functions(),
            pkg.variables()
        );
    }
}
