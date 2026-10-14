package dev.graphnous.application.scan.stats;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;
import dev.graphnous.domain.system.System;

import java.util.List;
import java.util.Optional;

public interface ScanStatRepository {

    ScanStats save(ScanStats stats);

    Optional<ScanStats> findByScanId(Scan.ScanId scanId);

    /**
     * The stats of the project's most recently created scan that has any.
     */
    Optional<ScanStats> findLatestByProjectId(Project.ProjectId projectId);

    /**
     * The stats of the most recently created scan that has any, of each
     * project of the system; nothing for the projects without one.
     */
    List<ScanStats> findLatestBySystemId(System.SystemId systemId);

    void deleteByScanId(Scan.ScanId scanId);

}
