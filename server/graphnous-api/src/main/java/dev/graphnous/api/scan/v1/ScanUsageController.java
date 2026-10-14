package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.scan.Scan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * What uses a node of a scan, to see what a change to it affects.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanUsageController {

    private final RequestContextProvider contextProvider;
    private final ScanUsageService scanUsageService;

    public ScanUsageController(
        final RequestContextProvider contextProvider,
        final ScanUsageService scanUsageService
    ) {
        this.contextProvider = contextProvider;
        this.scanUsageService = scanUsageService;
    }

    /**
     * @param subject   what was asked about: for what an enhancer added,
     *                  such as a component, the function or class it is of
     * @param usages    by file and line
     * @param truncated whether there are more usages than it holds
     */
    public record ScanUsagesResponse(
        UUID scanId,
        ScanUsageNode subject,
        List<ScanUsageResponse> usages,
        boolean truncated
    ) {
    }

    /**
     * @param id   its id in the scan's graph, the focus of
     *             GET /scans/{id}/graph
     * @param type Class, Method or Field
     */
    public record ScanUsageNode(
        String id,
        String type,
        String name,
        String qualifiedName
    ) {
    }

    /**
     * @param relationship CALLS, INSTANTIATES, READS, WRITES, USES, EXTENDS,
     *                     IMPLEMENTS or, for a method of a subclass with
     *                     the name of the one asked about, OVERRIDES
     * @param user         the method, function or class that uses it
     * @param owner        the name of the class the user is a method of
     * @param file         the path of the file the user is in
     * @param line         the line it uses it on, when the scan says
     * @param target       what it uses: the subject, or a method or field
     *                     of it; or, through inheritance, the method of
     *                     that name of a class above or below it
     * @param targetOwner  the name of the class the target is a method or
     *                     field of
     * @param depth        1 for what uses it itself, 2 for what calls that,
     *                     and so on
     */
    public record ScanUsageResponse(
        String relationship,
        ScanUsageNode user,
        String owner,
        String file,
        Integer line,
        ScanUsageNode target,
        String targetOwner,
        int depth
    ) {
    }

    /**
     * What uses the node: where a method is called, a class instantiated,
     * named as a type, extended or implemented, a field read or written.
     * For a class, also where its methods and fields are used. Inheritance
     * is followed: a method is also used where the method of its name of a
     * class above or below its own is, and the methods that override it
     * are usages themselves.
     *
     * @param node  the id of a class, method or field of the scan, or of
     *              what an enhancer added to one
     * @param depth 1, the default, for what uses the node itself; up to 3
     *              to also follow what calls that
     */
    @GetMapping("/{id}/usages")
    public ResponseEntity<ScanUsagesResponse> getUsages(
        @PathVariable("id") final UUID id,
        @RequestParam(name = "node") final String node,
        @RequestParam(name = "depth", required = false) final Integer depth
    ) {
        final var found = scanUsageService.getUsages(
            contextProvider.get(),
            new Scan.ScanId(id),
            node,
            depth
        );

        return ResponseEntity.ok(new ScanUsagesResponse(
            id,
            fromDomain(found.subject()),
            found.usages().stream()
                .map(usage -> new ScanUsageResponse(
                    usage.relationship(),
                    fromDomain(usage.user()),
                    usage.owner(),
                    usage.file(),
                    usage.line(),
                    fromDomain(usage.target()),
                    usage.targetOwner(),
                    usage.depth()
                ))
                .toList(),
            found.truncated()
        ));
    }

    private static ScanUsageNode fromDomain(final ScanUsages.Node node) {
        return new ScanUsageNode(node.id(), node.type(), node.name(), node.qualifiedName());
    }
}
