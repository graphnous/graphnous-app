package dev.graphnous.application.scan.usages;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;

/**
 * Finds what uses a node of a scan, for a user who may read the scan:
 * getting the scan first checks the user's permission and organization.
 */
public class ScanUsageService {

    /**
     * How far out usages are followed at most: what uses it, what calls
     * that, and what calls that.
     */
    public static final int MAX_DEPTH = 3;

    /**
     * How many usages are returned at most.
     */
    public static final int MAX_USAGES = 1_000;

    private final ScanService scanService;
    private final ScanUsageRepository scanUsageRepository;

    public ScanUsageService(
        final ScanService scanService,
        final ScanUsageRepository scanUsageRepository
    ) {
        this.scanService = scanService;
        this.scanUsageRepository = scanUsageRepository;
    }

    /**
     * @param depth how far out to follow; 1, only what uses the node
     *              itself, when null
     * @throws ValidationException when the node is missing or the depth is
     *                             not between 1 and {@link #MAX_DEPTH}
     * @throws NotFoundException   when the scan has no such node
     */
    public ScanUsages.Found getUsages(
        final RequestContext context,
        final Scan.ScanId scanId,
        final String nodeId,
        final Integer depth
    ) {
        scanService.getScan(context, scanId);

        if (nodeId == null || nodeId.isBlank()) {
            throw new ValidationException("node is required");
        }

        final var steps = depth == null ? 1 : depth;

        if (steps < 1 || steps > MAX_DEPTH) {
            throw new ValidationException("depth must be between 1 and " + MAX_DEPTH);
        }

        return scanUsageRepository.findUsages(scanId, nodeId.trim(), steps, MAX_USAGES)
            .orElseThrow(() -> new NotFoundException(
                "Node " + nodeId.trim() + " is not in the graph of scan " + scanId.id()
            ));
    }
}
