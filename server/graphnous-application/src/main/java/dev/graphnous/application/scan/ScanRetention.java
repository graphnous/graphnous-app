package dev.graphnous.application.scan;

import dev.graphnous.domain.project.Project;

/**
 * Limits how many scans are kept, and for how long. Only finished scans
 * are deleted: an active scan is still writing its results.
 */
public interface ScanRetention {

    /**
     * Deletes the finished scans older than the retention period.
     */
    void deleteExpired();

    /**
     * Deletes the oldest finished scans of the project, so one more scan
     * fits within the number a project keeps. Called before a scan is
     * created.
     */
    void makeRoomFor(Project.ProjectId projectId);

}
