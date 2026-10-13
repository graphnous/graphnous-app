package dev.graphnous.domain.scan.stats;

import dev.graphnous.domain.system.System;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * How much the latest scans of a system's projects covered together: the
 * stats of each project's latest scan that has any, added up.
 *
 * @param numberOfProjectsScanned the projects with a scan that has stats
 * @param numberOfModulesScanned  the modules of those projects
 * @param languages               the files of those projects by language,
 *                                such as JAVA or TYPESCRIPT; each a path
 *                                from the root of its project's repository
 * @param numberOfClassesParsed   the classes of those projects
 * @param numberOfMethodsParsed   the methods of those projects
 */
public record SystemStats(
    System.SystemId systemId,
    int numberOfProjectsScanned,
    int numberOfModulesScanned,
    Map<String, List<String>> languages,
    int numberOfClassesParsed,
    int numberOfMethodsParsed
) {

    public SystemStats {
        final var copy = new LinkedHashMap<String, List<String>>();
        languages.forEach((language, files) -> copy.put(language, List.copyOf(files)));

        languages = Collections.unmodifiableMap(copy);
    }

    /**
     * Adds up the stats of projects, one per project.
     */
    public static SystemStats of(final System.SystemId systemId, final List<ScanStats> projects) {
        final var languages = new TreeMap<String, List<String>>();

        int modules = 0;
        int classes = 0;
        int methods = 0;

        for (final var stats : projects) {
            modules += stats.numberOfModulesScanned();
            classes += stats.numberOfClassesParsed();
            methods += stats.numberOfMethodsParsed();

            // Projects may have files at the same path, so files are not deduplicated
            stats.languages().forEach((language, files) ->
                languages.computeIfAbsent(language, key -> new ArrayList<>()).addAll(files)
            );
        }

        return new SystemStats(systemId, projects.size(), modules, languages, classes, methods);
    }
}
