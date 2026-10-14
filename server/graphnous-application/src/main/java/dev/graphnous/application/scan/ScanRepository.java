package dev.graphnous.application.scan;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;

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

    /**
     * The scans of every project of the system.
     */
    Page<Scan> findAllInSystem(
        final System.SystemId systemId,
        final PageQuery pageQuery
    );

    /**
     * The most recently created completed scan of each project of the
     * system; nothing for the projects without one.
     */
    List<Scan> findLatestCompletedInSystem(
        final System.SystemId systemId
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

    /**
     * How many scans the projects of the system have, whatever their
     * status.
     */
    int countInSystem(
        final System.SystemId systemId
    );


    void startScan(
        final UUID snapshotId,
        final Scan.ScanId scanId
    );

    /**
     * Sets the scan's revision, and only that, so it does not undo a change
     * of its status made meanwhile; in the graph too.
     */
    void updateRevision(
        final Scan.ScanId scanId,
        final String revision
    );
}
