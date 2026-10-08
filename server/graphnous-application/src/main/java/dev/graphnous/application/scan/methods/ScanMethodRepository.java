package dev.graphnous.application.scan.methods;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.scan.Scan;

/**
 * Reads the methods a scan found from its graph.
 */
public interface ScanMethodRepository {

    /**
     * A page of the scan's methods, constructors and functions that pass
     * the filter, sorted by one of {@link ScanMethodService#SORTS}, then by
     * id.
     */
    Page<ScanMethod> findMethods(Scan.ScanId scanId, ScanMethodFilter filter, PageQuery page);
}
