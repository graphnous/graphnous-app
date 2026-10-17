package dev.graphnous.api.scan.v1;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.exports.ScanPackageExport;
import dev.graphnous.application.scan.exports.ScanPackageExportFilter;
import dev.graphnous.application.scan.exports.ScanPackageExportService;
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
 * What a scan's code uses of the packages it imports and does not scan, a
 * page at a time, such as {@code Button} of {@code @acme/theme}; the
 * usages of one are those of its id.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanPackageExportController {

    private final RequestContextProvider contextProvider;
    private final ScanPackageExportService scanPackageExportService;

    public ScanPackageExportController(
        final RequestContextProvider contextProvider,
        final ScanPackageExportService scanPackageExportService
    ) {
        this.contextProvider = contextProvider;
        this.scanPackageExportService = scanPackageExportService;
    }

    /**
     * @param id            its id in the scan's graph, which
     *                      GET /scans/{id}/usages takes
     * @param pkg           the package, such as {@code @acme/theme}
     * @param module        the module it is imported from as written
     * @param name          the name it is exported by
     * @param qualifiedName the module and the name
     * @param usages        the places that call, render or construct it
     * @param files         the files with such a place
     */
    public record ScanPackageExportResponse(
        String id,
        @JsonProperty("package") String pkg,
        String module,
        String name,
        String qualifiedName,
        long usages,
        long files
    ) {
    }

    /**
     * Every filter is optional.
     *
     * @param query part of the qualified name, ignoring case
     * @param pkg   the name of the package they are of
     */
    @GetMapping("/{id}/package-exports")
    public ResponseEntity<ScanItemPage<ScanPackageExportResponse>> getPackageExports(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "package", required = false) final String pkg,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "qualifiedName") @Pattern(regexp = "qualifiedName|name|usages") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var exports = scanPackageExportService.getPackageExports(
            contextProvider.get(),
            new Scan.ScanId(id),
            new ScanPackageExportFilter(query, pkg),
            new PageQuery(page, size, new Sort(sort, Sort.Direction.fromValue(direction)))
        );

        return ResponseEntity.ok(ScanItemPage.of(exports, ScanPackageExportController::fromDomain));
    }

    private static ScanPackageExportResponse fromDomain(final ScanPackageExport export) {
        return new ScanPackageExportResponse(
            export.id(),
            export.pkg(),
            export.module(),
            export.name(),
            export.qualifiedName(),
            export.usages(),
            export.files()
        );
    }
}
