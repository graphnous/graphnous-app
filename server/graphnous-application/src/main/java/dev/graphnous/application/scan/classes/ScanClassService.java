package dev.graphnous.application.scan.classes;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanListing;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;

import java.util.Set;

/**
 * Lists the classes a scan found, for a user who may read the scan:
 * getting the scan first checks the user's permission and organization.
 */
public class ScanClassService {

    /**
     * The properties classes are sorted by.
     */
    public static final Set<String> SORTS = Set.of("name", "qualifiedName", "kind");

    private final ScanService scanService;
    private final ScanClassRepository scanClassRepository;

    public ScanClassService(
        final ScanService scanService,
        final ScanClassRepository scanClassRepository
    ) {
        this.scanService = scanService;
        this.scanClassRepository = scanClassRepository;
    }

    public Page<ScanClass> getClasses(
        final RequestContext context,
        final Scan.ScanId scanId,
        final ScanClassFilter filter,
        final PageQuery page
    ) {
        scanService.getScan(context, scanId);

        return scanClassRepository.findClasses(scanId, filter, ScanListing.sorted(page, SORTS));
    }
}
