package dev.graphnous.application.scan.modules;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanListing;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;

import java.util.Set;

/**
 * Lists the modules a scan found, for a user who may read the scan:
 * getting the scan first checks the user's permission and organization.
 */
public class ScanModuleService {

    /**
     * The properties modules are sorted by.
     */
    public static final Set<String> SORTS = Set.of("path", "name");

    private final ScanService scanService;
    private final ScanModuleRepository scanModuleRepository;

    public ScanModuleService(
        final ScanService scanService,
        final ScanModuleRepository scanModuleRepository
    ) {
        this.scanService = scanService;
        this.scanModuleRepository = scanModuleRepository;
    }

    public Page<ScanModule> getModules(
        final RequestContext context,
        final Scan.ScanId scanId,
        final ScanModuleFilter filter,
        final PageQuery page
    ) {
        scanService.getScan(context, scanId);

        return scanModuleRepository.findModules(scanId, filter, ScanListing.sorted(page, SORTS));
    }
}
