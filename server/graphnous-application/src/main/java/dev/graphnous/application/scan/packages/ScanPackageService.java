package dev.graphnous.application.scan.packages;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanListing;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;

import java.util.Set;

/**
 * Lists the packages a scan found, for a user who may read the scan:
 * getting the scan first checks the user's permission and organization.
 */
public class ScanPackageService {

    /**
     * The properties packages are sorted by.
     */
    public static final Set<String> SORTS = Set.of("qualifiedName", "name");

    private final ScanService scanService;
    private final ScanPackageRepository scanPackageRepository;

    public ScanPackageService(
        final ScanService scanService,
        final ScanPackageRepository scanPackageRepository
    ) {
        this.scanService = scanService;
        this.scanPackageRepository = scanPackageRepository;
    }

    public Page<ScanPackage> getPackages(
        final RequestContext context,
        final Scan.ScanId scanId,
        final ScanPackageFilter filter,
        final PageQuery page
    ) {
        scanService.getScan(context, scanId);

        return scanPackageRepository.findPackages(scanId, filter, ScanListing.sorted(page, SORTS));
    }
}
