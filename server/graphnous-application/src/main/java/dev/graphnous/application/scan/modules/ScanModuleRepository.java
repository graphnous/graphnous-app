package dev.graphnous.application.scan.modules;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.scan.Scan;

/**
 * Reads the modules a scan found from its graph.
 */
public interface ScanModuleRepository {

    /**
     * A page of the scan's modules that pass the filter, sorted by one of
     * {@link ScanModuleService#SORTS}, then by id.
     */
    Page<ScanModule> findModules(Scan.ScanId scanId, ScanModuleFilter filter, PageQuery page);
}
