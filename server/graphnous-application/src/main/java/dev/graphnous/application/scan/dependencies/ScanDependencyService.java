package dev.graphnous.application.scan.dependencies;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanListing;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;

import java.util.Set;

/**
 * Lists what the modules of a scan depend on, for a user who may read the
 * scan: getting the scan first checks the user's permission and
 * organization.
 */
public class ScanDependencyService {

    /**
     * The properties dependencies are sorted by.
     */
    public static final Set<String> SORTS = Set.of("name", "module", "scope");

    private final ScanService scanService;
    private final ScanDependencyRepository scanDependencyRepository;

    public ScanDependencyService(
        final ScanService scanService,
        final ScanDependencyRepository scanDependencyRepository
    ) {
        this.scanService = scanService;
        this.scanDependencyRepository = scanDependencyRepository;
    }

    public Page<ScanDependency> getDependencies(
        final RequestContext context,
        final Scan.ScanId scanId,
        final ScanDependencyFilter filter,
        final PageQuery page
    ) {
        scanService.getScan(context, scanId);

        return scanDependencyRepository.findDependencies(scanId, filter, ScanListing.sorted(page, SORTS));
    }
}
