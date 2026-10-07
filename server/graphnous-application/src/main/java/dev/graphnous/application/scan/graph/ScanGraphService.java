package dev.graphnous.application.scan.graph;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Reads the graph of a scan, for a user who may read the scan: each read
 * gets the scan first, which checks the user's permission and organization.
 */
public class ScanGraphService {

    /**
     * The most a search returns.
     */
    static final int MAX_LIMIT = 200;

    /**
     * The hops shown from the scan itself: its targets and their modules.
     */
    static final int DEFAULT_SCAN_DEPTH = 2;

    /**
     * The hops shown from a focused node: its neighbours.
     */
    static final int DEFAULT_FOCUS_DEPTH = 1;

    /**
     * The most hops a neighbourhood reaches; from the scan, five reach the
     * methods, fields and annotations of its classes.
     */
    static final int MAX_DEPTH = 5;

    /**
     * The most nodes a neighbourhood holds, to keep it drawable.
     */
    static final int MAX_NODES = 500;

    private final ScanService scanService;
    private final ScanGraphRepository scanGraphRepository;

    private static final Logger log = LoggerFactory.getLogger(ScanGraphService.class);

    public ScanGraphService(
        final ScanService scanService,
        final ScanGraphRepository scanGraphRepository
    ) {
        this.scanService = scanService;
        this.scanGraphRepository = scanGraphRepository;
    }

    public ScanGraph.Overview getOverview(
        final RequestContext context,
        final Scan.ScanId scanId
    ) {
        scanService.getScan(context, scanId);

        log.debug("Getting scan graph organizationId={} scanId={}", context.organization().id(), scanId.id());

        return scanGraphRepository.getOverview(scanId);
    }

    /**
     * The part of the scan's graph around a node: the scan's targets and
     * modules when no node is focused, and a node's direct neighbours when
     * no depth is given.
     *
     * @param focus the id of a node of the scan, or null for the scan itself
     * @param depth the hops from the focus, up to {@link #MAX_DEPTH}; null
     *              for the default
     */
    public ScanGraph.Neighbourhood getNeighbourhood(
        final RequestContext context,
        final Scan.ScanId scanId,
        final String focus,
        final Integer depth
    ) {
        scanService.getScan(context, scanId);

        final var node = focus == null || focus.isBlank() ? null : focus.trim();
        final var hops = depth == null ? (node == null ? DEFAULT_SCAN_DEPTH : DEFAULT_FOCUS_DEPTH) : depth;

        if (hops < 0 || hops > MAX_DEPTH) {
            throw new ValidationException("depth must be between 0 and " + MAX_DEPTH);
        }

        log.debug(
            "Getting scan graph organizationId={} scanId={} focus={} depth={}",
            context.organization().id(),
            scanId.id(),
            node,
            hops
        );

        return scanGraphRepository.findNeighbourhood(scanId, node, hops, MAX_NODES)
            .orElseThrow(() -> new NotFoundException(
                "Node " + node + " is not in the graph of scan " + scanId.id()
            ));
    }

    public List<ScanGraph.ClassSummary> findClasses(
        final RequestContext context,
        final Scan.ScanId scanId,
        final String query,
        final int limit
    ) {
        scanService.getScan(context, scanId);

        return scanGraphRepository.findClasses(scanId, query == null ? "" : query.trim(), limit(limit));
    }

    public ScanGraph.ClassDetails getClass(
        final RequestContext context,
        final Scan.ScanId scanId,
        final String qualifiedName
    ) {
        scanService.getScan(context, scanId);

        return scanGraphRepository.findClass(scanId, required(qualifiedName, "qualifiedName"))
            .orElseThrow(() -> new NotFoundException(
                "Class " + qualifiedName + " is not in scan " + scanId.id()
            ));
    }

    public List<ScanGraph.AnnotatedElement> findAnnotated(
        final RequestContext context,
        final Scan.ScanId scanId,
        final String annotation,
        final int limit
    ) {
        scanService.getScan(context, scanId);

        // Accept the annotation as it is written in code, e.g. @GetMapping
        final var name = required(annotation, "annotation").replaceFirst("^@", "");

        return scanGraphRepository.findAnnotated(scanId, name, limit(limit));
    }

    public List<ScanGraph.Dependency> getDependencies(
        final RequestContext context,
        final Scan.ScanId scanId
    ) {
        scanService.getScan(context, scanId);

        return scanGraphRepository.findDependencies(scanId);
    }

    private static int limit(final int limit) {
        return Math.clamp(limit, 1, MAX_LIMIT);
    }

    private static String required(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(name + " is required");
        }

        return value.trim();
    }
}
