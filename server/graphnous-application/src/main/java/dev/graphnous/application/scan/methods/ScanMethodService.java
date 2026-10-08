package dev.graphnous.application.scan.methods;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanListing;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;

import java.util.Set;

/**
 * Lists the methods, constructors and functions a scan found, for a user
 * who may read the scan: getting the scan first checks the user's
 * permission and organization.
 */
public class ScanMethodService {

    /**
     * The properties methods are sorted by.
     */
    public static final Set<String> SORTS = Set.of("name", "qualifiedName", "kind");

    private final ScanService scanService;
    private final ScanMethodRepository scanMethodRepository;

    public ScanMethodService(
        final ScanService scanService,
        final ScanMethodRepository scanMethodRepository
    ) {
        this.scanService = scanService;
        this.scanMethodRepository = scanMethodRepository;
    }

    public Page<ScanMethod> getMethods(
        final RequestContext context,
        final Scan.ScanId scanId,
        final ScanMethodFilter filter,
        final PageQuery page
    ) {
        scanService.getScan(context, scanId);

        return scanMethodRepository.findMethods(scanId, filter, ScanListing.sorted(page, SORTS));
    }
}
