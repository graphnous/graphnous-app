package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.modules.ScanModule;
import dev.graphnous.application.scan.modules.ScanModuleFilter;
import dev.graphnous.application.scan.modules.ScanModuleService;
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
 * The modules a scan found, a page at a time, with how much each holds.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanModuleController {

    private final RequestContextProvider contextProvider;
    private final ScanModuleService scanModuleService;

    public ScanModuleController(
        final RequestContextProvider contextProvider,
        final ScanModuleService scanModuleService
    ) {
        this.contextProvider = contextProvider;
        this.scanModuleService = scanModuleService;
    }

    /**
     * @param id           the module's id in the scan's graph, the focus of
     *                     GET /scans/{id}/graph
     * @param target       the path of the scan target it is in
     * @param path         its path within the target; what the other lists
     *                     filter by module with
     * @param classes      each class of the module once
     * @param methods      the methods of its classes and the functions
     *                     declared outside any class
     * @param dependencies the libraries it depends on
     */
    public record ScanModuleResponse(
        String id,
        String target,
        String name,
        String path,
        long files,
        long packages,
        long classes,
        long methods,
        long dependencies
    ) {
    }

    /**
     * Every filter is optional.
     *
     * @param query  part of the name or path, ignoring case
     * @param target the path of the scan target
     */
    @GetMapping("/{id}/modules")
    public ResponseEntity<ScanItemPage<ScanModuleResponse>> getModules(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "target", required = false) final String target,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "path") @Pattern(regexp = "path|name") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var modules = scanModuleService.getModules(
            contextProvider.get(),
            new Scan.ScanId(id),
            new ScanModuleFilter(query, target),
            new PageQuery(page, size, new Sort(sort, Sort.Direction.fromValue(direction)))
        );

        return ResponseEntity.ok(ScanItemPage.of(modules, ScanModuleController::fromDomain));
    }

    private static ScanModuleResponse fromDomain(final ScanModule module) {
        return new ScanModuleResponse(
            module.id(),
            module.target(),
            module.name(),
            module.path(),
            module.files(),
            module.packages(),
            module.classes(),
            module.methods(),
            module.dependencies()
        );
    }
}
