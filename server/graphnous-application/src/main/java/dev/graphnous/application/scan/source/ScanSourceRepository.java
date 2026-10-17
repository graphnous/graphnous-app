package dev.graphnous.application.scan.source;

import dev.graphnous.domain.scan.Scan;

import java.util.List;

/**
 * Reads the files a scan found, with what they declare, from its graph.
 */
public interface ScanSourceRepository {

    /**
     * The files of the scan whose stored path the given one ends with: the
     * candidates for a path that also names the target and module, which
     * {@link ScanSourceService} then picks from.
     */
    List<ScanSource.File> findFilesEndingPath(Scan.ScanId scanId, String path);
}
