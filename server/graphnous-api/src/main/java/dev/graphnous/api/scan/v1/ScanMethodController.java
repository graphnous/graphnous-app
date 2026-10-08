package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.methods.ScanMethod;
import dev.graphnous.application.scan.methods.ScanMethodFilter;
import dev.graphnous.application.scan.methods.ScanMethodService;
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

import java.util.List;
import java.util.UUID;

/**
 * The methods, constructors and functions a scan found, a page at a time.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanMethodController {

    private final RequestContextProvider contextProvider;
    private final ScanMethodService scanMethodService;

    public ScanMethodController(
        final RequestContextProvider contextProvider,
        final ScanMethodService scanMethodService
    ) {
        this.contextProvider = contextProvider;
        this.scanMethodService = scanMethodService;
    }

    /**
     * @param id          the method's id in the scan's graph, the focus of
     *                    GET /scans/{id}/graph
     * @param kind        METHOD, CONSTRUCTOR or FUNCTION
     * @param returnType  the declared return type; null for a constructor
     *                    and when the source declares none
     * @param annotations the qualified names of its annotations, or their
     *                    names when those are unknown
     * @param className   the qualified name of its class; null for a
     *                    function declared outside any class
     * @param module      the path of the module that declares it
     * @param file        the path of the file that declares it, if known
     */
    public record ScanMethodResponse(
        String id,
        String name,
        String qualifiedName,
        String kind,
        List<String> modifiers,
        String returnType,
        List<String> parameterNames,
        List<String> parameterTypes,
        List<String> annotations,
        String className,
        String module,
        String file
    ) {
    }

    /**
     * The methods of classes and the functions declared outside any class.
     * Every filter is optional.
     *
     * @param query     part of the name or qualified name, ignoring case
     * @param module    the path of the module that declares them
     * @param kind      METHOD, CONSTRUCTOR or FUNCTION, ignoring case
     * @param className the qualified name of the class they belong to
     */
    @GetMapping("/{id}/methods")
    public ResponseEntity<ScanItemPage<ScanMethodResponse>> getMethods(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "module", required = false) final String module,
        @RequestParam(name = "kind", required = false) @Pattern(regexp = "(?i)method|constructor|function") final String kind,
        @RequestParam(name = "className", required = false) final String className,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "qualifiedName") @Pattern(regexp = "name|qualifiedName|kind") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var methods = scanMethodService.getMethods(
            contextProvider.get(),
            new Scan.ScanId(id),
            new ScanMethodFilter(query, module, kind, className),
            new PageQuery(page, size, new Sort(sort, Sort.Direction.fromValue(direction)))
        );

        return ResponseEntity.ok(ScanItemPage.of(methods, ScanMethodController::fromDomain));
    }

    private static ScanMethodResponse fromDomain(final ScanMethod method) {
        return new ScanMethodResponse(
            method.id(),
            method.name(),
            method.qualifiedName(),
            method.kind(),
            method.modifiers(),
            method.returnType(),
            method.parameterNames(),
            method.parameterTypes(),
            method.annotations(),
            method.className(),
            method.module(),
            method.file()
        );
    }
}
