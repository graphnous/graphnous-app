package dev.graphnous.application.scan.exports;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanListing;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;

import java.util.Set;

/**
 * Lists what a scan uses of packages, for a user who may read the scan:
 * getting the scan first checks the user's permission and organization.
 */
public class ScanPackageExportService {

    /**
     * The properties package exports are sorted by.
     */
    public static final Set<String> SORTS = Set.of("qualifiedName", "name", "usages");

    private final ScanService scanService;
    private final ScanPackageExportRepository scanPackageExportRepository;

    public ScanPackageExportService(
        final ScanService scanService,
        final ScanPackageExportRepository scanPackageExportRepository
    ) {
        this.scanService = scanService;
        this.scanPackageExportRepository = scanPackageExportRepository;
    }

    public Page<ScanPackageExport> getPackageExports(
        final RequestContext context,
        final Scan.ScanId scanId,
        final ScanPackageExportFilter filter,
        final PageQuery page
    ) {
        scanService.getScan(context, scanId);

        return scanPackageExportRepository.findPackageExports(scanId, filter, ScanListing.sorted(page, SORTS));
    }
}
