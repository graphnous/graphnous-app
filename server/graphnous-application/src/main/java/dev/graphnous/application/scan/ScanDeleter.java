package dev.graphnous.application.scan;

import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.application.scan.stats.ScanStatRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

/**
 * Deletes scans with everything that belongs to them: their results in the
 * graph, their logs, their steps, their stats and the scans themselves.
 * <p>
 * The stores do not share a transaction, so the scan itself goes last: a
 * delete that fails halfway can be repeated, as the scan is still found.
 */
public class ScanDeleter {

    private final ScanRepository scanRepository;
    private final ScanLogRepository scanLogRepository;
    private final ScanResultRepository scanResultRepository;
    private final ScanStepRepository scanStepRepository;
    private final ScanStatRepository scanStatRepository;

    public ScanDeleter(
        final ScanRepository scanRepository,
        final ScanLogRepository scanLogRepository,
        final ScanResultRepository scanResultRepository,
        final ScanStepRepository scanStepRepository,
        final ScanStatRepository scanStatRepository
    ) {
        this.scanRepository = scanRepository;
        this.scanLogRepository = scanLogRepository;
        this.scanResultRepository = scanResultRepository;
        this.scanStepRepository = scanStepRepository;
        this.scanStatRepository = scanStatRepository;
    }

    /**
     * Refuses when a scan of the project is still active: it would keep
     * writing results and logs for a project that no longer exists.
     */
    public void requireNoActiveScans(final Project.ProjectId projectId) {
        if (scanRepository.hasActiveScans(projectId)) {
            throw new ConflictException(
                "Project %s cannot be deleted while it has active scans".formatted(projectId.id())
            );
        }
    }

    public void deleteScans(final Project.ProjectId projectId) {
        for (final var scanId : scanRepository.findIdsByProjectId(projectId)) {
            deleteScan(scanId);
        }
    }

    public void deleteScan(final Scan.ScanId scanId) {
        scanResultRepository.delete(scanId);
        scanLogRepository.deleteByScanId(scanId);
        scanStepRepository.deleteByScanId(scanId);
        scanStatRepository.deleteByScanId(scanId);
        scanRepository.delete(scanId);
    }
}
