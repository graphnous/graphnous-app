package dev.graphnous.domain.scan.stats;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.UUID;

/**
 * How much a completed scan covered, counted from its results. A scan has
 * at most one.
 *
 * @param numberOfFilesScanned   the files of every module of every target
 * @param numberOfClassesParsed  the classes, each counted once per module
 * @param numberOfMethodsParsed  the methods of those classes
 */
public record ScanStats(
    ScanStatId id,
    Scan.ScanId scanId,
    Project.ProjectId projectId,
    int numberOfFilesScanned,
    int numberOfClassesParsed,
    int numberOfMethodsParsed
) {

    public record ScanStatId(UUID id) {
        public static ScanStatId generate() {
            return new ScanStatId(UUID.randomUUID());
        }
    }
}
