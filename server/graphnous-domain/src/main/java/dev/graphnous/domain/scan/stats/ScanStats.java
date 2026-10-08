package dev.graphnous.domain.scan.stats;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How much a completed scan covered, counted from its results. A scan has
 * at most one.
 *
 * @param numberOfModulesScanned the modules of every target
 * @param languages              the files of every module of every target,
 *                               by the language of their target, such as
 *                               JAVA or TYPESCRIPT; each a path from the
 *                               repository's root
 * @param numberOfClassesParsed  the classes, each counted once per module
 * @param numberOfMethodsParsed  the methods of those classes, and the
 *                               functions declared outside any class
 */
public record ScanStats(
    ScanStatId id,
    Scan.ScanId scanId,
    Project.ProjectId projectId,
    int numberOfModulesScanned,
    Map<String, List<String>> languages,
    int numberOfClassesParsed,
    int numberOfMethodsParsed
) {

    public ScanStats {
        final var copy = new LinkedHashMap<String, List<String>>();
        languages.forEach((language, files) -> copy.put(language, List.copyOf(files)));

        languages = Collections.unmodifiableMap(copy);
    }

    public record ScanStatId(UUID id) {
        public static ScanStatId generate() {
            return new ScanStatId(UUID.randomUUID());
        }
    }
}
