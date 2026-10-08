package dev.graphnous.application.scan.classes;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.scan.Scan;

/**
 * Reads the classes a scan found from its graph.
 */
public interface ScanClassRepository {

    /**
     * A page of the scan's classes, nested ones included, that pass the
     * filter, sorted by one of {@link ScanClassService#SORTS}, then by id.
     */
    Page<ScanClass> findClasses(Scan.ScanId scanId, ScanClassFilter filter, PageQuery page);
}
