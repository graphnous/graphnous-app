package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.classes.ScanClass;
import dev.graphnous.application.scan.classes.ScanClassFilter;
import dev.graphnous.application.scan.classes.ScanClassService;
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
 * The classes, interfaces, enums, records, structs, traits and type
 * aliases a scan found, a page at a time.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanClassController {

    private final RequestContextProvider contextProvider;
    private final ScanClassService scanClassService;

    public ScanClassController(
        final RequestContextProvider contextProvider,
        final ScanClassService scanClassService
    ) {
        this.contextProvider = contextProvider;
        this.scanClassService = scanClassService;
    }

    /**
     * @param id          the class's id in the scan's graph, the focus of
     *                    GET /scans/{id}/graph
     * @param module      the path of the module that declares it
     * @param file        the path of the file that declares it, if known
     * @param packageName the qualified name of its package, if it has one
     * @param superClass  the declared superclass, with its type arguments
     * @param interfaces  the declared interfaces, with their type arguments
     * @param annotations the qualified names of its annotations, or their
     *                    names when those are unknown
     * @param methods     how many methods it declares
     * @param fields      how many fields it declares
     */
    public record ScanClassResponse(
        String id,
        String name,
        String qualifiedName,
        String kind,
        List<String> modifiers,
        String module,
        String file,
        String packageName,
        String superClass,
        List<String> interfaces,
        List<String> annotations,
        long methods,
        long fields
    ) {
    }

    /**
     * Nested classes are listed too. Every filter is optional.
     *
     * @param query  part of the name or qualified name, ignoring case
     * @param module the path of the module that declares them
     * @param kind   CLASS, INTERFACE, ENUM, RECORD, ANNOTATION, STRUCT, TRAIT
     *               or TYPE_ALIAS, ignoring case
     */
    @GetMapping("/{id}/classes")
    public ResponseEntity<ScanItemPage<ScanClassResponse>> getClasses(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "query", required = false) final String query,
        @RequestParam(name = "module", required = false) final String module,
        @RequestParam(name = "kind", required = false) final String kind,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int page,
        @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "qualifiedName") @Pattern(regexp = "name|qualifiedName|kind") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var classes = scanClassService.getClasses(
            contextProvider.get(),
            new Scan.ScanId(id),
            new ScanClassFilter(query, module, kind),
            new PageQuery(page, size, new Sort(sort, Sort.Direction.fromValue(direction)))
        );

        return ResponseEntity.ok(ScanItemPage.of(classes, ScanClassController::fromDomain));
    }

    private static ScanClassResponse fromDomain(final ScanClass type) {
        return new ScanClassResponse(
            type.id(),
            type.name(),
            type.qualifiedName(),
            type.kind(),
            type.modifiers(),
            type.module(),
            type.file(),
            type.packageName(),
            type.superClass(),
            type.interfaces(),
            type.annotations(),
            type.methods(),
            type.fields()
        );
    }
}
