package dev.graphnous.application.scan;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScanRepository {

    Scan save(
        final Scan scan
    );

    Page<Scan> findAll(
        final Project.ProjectId projectId,
        final PageQuery pageQuery
    );

    Scan findById(
        final Scan.ScanId id
    );

    List<Scan.ScanId> findIdsByProjectId(
        final Project.ProjectId projectId
    );

    /**
     * The scans that have not reached an end state yet.
     */
    List<Scan> findActive();

    /**
     * Whether any scan of the project has not reached an end state yet.
     */
    boolean hasActiveScans(
        final Project.ProjectId projectId
    );

    /**
     * The finished scans created before {@code cutoff}.
     */
    List<Scan> findFinishedCreatedBefore(
        final Instant cutoff
    );

    /**
     * The {@code limit} oldest finished scans of the project, oldest first.
     */
    List<Scan.ScanId> findOldestFinished(
        final Project.ProjectId projectId,
        final int limit
    );

    /**
     * Deletes the scan, and its node in the graph.
     */
    void delete(
        final Scan.ScanId scanId
    );

    int count(
        final Project.ProjectId projectId
    );


    void startScan(
        final UUID snapshotId,
        final Scan.ScanId scanId
    );

}
