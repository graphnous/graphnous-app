package dev.graphnous.application.scan.files;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanListing;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;

import java.util.Set;

/**
 * Lists the files a scan found, for a user who may read the scan: getting
 * the scan first checks the user's permission and organization.
 */
public class ScanFileService {

    /**
     * The properties files are sorted by.
     */
    public static final Set<String> SORTS = Set.of("path", "language", "size");

    private final ScanService scanService;
    private final ScanFileRepository scanFileRepository;

    public ScanFileService(
        final ScanService scanService,
        final ScanFileRepository scanFileRepository
    ) {
        this.scanService = scanService;
        this.scanFileRepository = scanFileRepository;
    }

    public Page<ScanFile> getFiles(
        final RequestContext context,
        final Scan.ScanId scanId,
        final ScanFileFilter filter,
        final PageQuery page
    ) {
        scanService.getScan(context, scanId);

        return scanFileRepository.findFiles(scanId, filter, ScanListing.sorted(page, SORTS));
    }
}
