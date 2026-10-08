package dev.graphnous.application.scan.files;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.scan.Scan;

/**
 * Reads the files a scan found from its graph.
 */
public interface ScanFileRepository {

    /**
     * A page of the scan's files that pass the filter, sorted by one of
     * {@link ScanFileService#SORTS}, then by id.
     */
    Page<ScanFile> findFiles(Scan.ScanId scanId, ScanFileFilter filter, PageQuery page);
}
