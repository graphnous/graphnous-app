package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.files.ScanFile;
import dev.graphnous.application.scan.files.ScanFileFilter;
import dev.graphnous.application.scan.files.ScanFileService;
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
 * The source files a scan found, a page at a time.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanFileController {

    private final RequestContextProvider contextProvider;
    private final ScanFileService scanFileService;

    public ScanFileController(
        final RequestContextProvider contextProvider,
        final ScanFileService scanFileService
    ) {
        this.contextProvider = contextProvider;
        this.scanFileService = scanFileService;
    }

    /**
     * @param id        the file's id in the scan's graph, the focus of
     *                  GET /scans/{id}/graph
     * @param target    the path of the scan target it is in
     * @param module    the path of the module it is in
     * @param path      its path within the module
     * @param sourceSet MAIN or TEST, if the scanner could tell
     * @param size      in bytes, if known
     * @param classes   the top-level classes it declares
     * @param functions the functions it declares outside any class
     */
    public record ScanFileResponse(
        String id,
        String target,
        String module,
        String path,
        String language,
        String sourceSet,
        Long size,
        String checksum,
        long classes,
        long functions
    ) {
    }

    /**
     * Every filter is optional.
     *
     * @param query     part of the path, ignoring case
     * @param module    the path of the module
     * @param language  such as JAVA, ignoring case
     * @param sourceSet MAIN or TEST
     */
    @GetMapping("/{id}/files")
    public ResponseEntity<ScanItemPage<ScanFileResponse>> getFiles(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "module", required = false) final String module,
        @RequestParam(name = "language", required = false) final String language,
        @RequestParam(name = "sourceSet", required = false) @Pattern(regexp = "(?i)main|test") final String sourceSet,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "path") @Pattern(regexp = "path|language|size") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var files = scanFileService.getFiles(
            contextProvider.get(),
            new Scan.ScanId(id),
            new ScanFileFilter(query, module, language, sourceSet),
            new PageQuery(page, size, new Sort(sort, Sort.Direction.fromValue(direction)))
        );

        return ResponseEntity.ok(ScanItemPage.of(files, ScanFileController::fromDomain));
    }

    private static ScanFileResponse fromDomain(final ScanFile file) {
        return new ScanFileResponse(
            file.id(),
            file.target(),
            file.module(),
            file.path(),
            file.language(),
            file.sourceSet(),
            file.size(),
            file.checksum(),
            file.classes(),
            file.functions()
        );
    }
}
