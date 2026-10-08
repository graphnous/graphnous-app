package dev.graphnous.application.scan.stats;

import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Keeps the stats of scans, counted from their results.
 */
public class ScanStatService {

    /**
     * The language of files whose target has none.
     */
    static final String UNKNOWN_LANGUAGE = "UNKNOWN";

    private final ScanStatRepository scanStatRepository;

    private static final Logger log = LoggerFactory.getLogger(ScanStatService.class);

    public ScanStatService(
        final ScanStatRepository scanStatRepository
    ) {
        this.scanStatRepository = scanStatRepository;
    }

    /**
     * Counts the scan's results into its stats: new stats for a scan
     * without any, or replaces the counts of the ones it has.
     */
    public ScanStats createOrUpdate(
        final Scan.ScanId scanId,
        final Project.ProjectId projectId,
        final List<ScanResult> results
    ) {
        final var id = this.scanStatRepository.findByScanId(scanId)
            .map(ScanStats::id)
            .orElseGet(ScanStats.ScanStatId::generate);

        final var stats = this.scanStatRepository.save(count(id, scanId, projectId, results));

        log.info(
            "Saved scan stats scanId={} modules={} files={} classes={} methods={}",
            scanId.id(),
            stats.numberOfModulesScanned(),
            stats.languages().values().stream().mapToInt(List::size).sum(),
            stats.numberOfClassesParsed(),
            stats.numberOfMethodsParsed()
        );

        return stats;
    }

    /**
     * Counts as the results are stored in the graph: classes nested in
     * others count too, and a class listed twice is one class of its
     * module. Files are
     * listed under the language of their target, by their path from the
     * repository's root.
     */
    private static ScanStats count(
        final ScanStats.ScanStatId id,
        final Scan.ScanId scanId,
        final Project.ProjectId projectId,
        final List<ScanResult> results
    ) {
        final var modules = new HashSet<String>();
        final var languages = new TreeMap<String, Set<String>>();
        final var classes = new HashSet<String>();
        final var functions = new HashSet<String>();

        int methods = 0;

        for (final var result : results) {
            final var target = result.getTarget() == null ? null : result.getTarget().getPath();
            final var language = language(result);

            for (final var module : result.getModules()) {
                final var moduleId = target + "|" + module.getPath();
                modules.add(moduleId);

                final var listed = new ArrayList<Class>();

                for (final var file : module.getFiles()) {
                    languages
                        .computeIfAbsent(language, key -> new LinkedHashSet<>())
                        .add(path(target, module.getPath(), file.getPath()));
                    file.getClasses().forEach(type -> addWithNested(listed, type));
                    file.getFunctions().forEach(function ->
                        functions.add(moduleId + "|" + file.getPath() + "|" + function.getQualifiedName())
                    );
                }

                for (final var type : listed) {
                    if (classes.add(moduleId + "|" + type.getQualifiedName())) {
                        methods += type.getMethods().size();
                    }
                }
            }
        }

        final var files = new LinkedHashMap<String, List<String>>();
        languages.forEach((language, paths) -> files.put(language, List.copyOf(paths)));

        // Functions declared outside any class count as methods
        methods += functions.size();

        return new ScanStats(id, scanId, projectId, modules.size(), files, classes.size(), methods);
    }

    private static void addWithNested(final List<Class> listed, final Class type) {
        listed.add(type);
        type.getClasses().forEach(nested -> addWithNested(listed, nested));
    }

    private static String language(final ScanResult result) {
        if (result.getTarget() == null || result.getTarget().getLanguage() == null) {
            return UNKNOWN_LANGUAGE;
        }

        return result.getTarget().getLanguage().value().toUpperCase(Locale.ROOT);
    }

    /**
     * The path from the repository's root, leaving out the {@code .} of a
     * target or module at the root of its parent.
     */
    private static String path(final String... segments) {
        return Arrays.stream(segments)
            .filter(segment -> segment != null && !segment.isBlank() && !segment.equals("."))
            .map(segment -> segment.replaceAll("^\\./|/+$", ""))
            .collect(Collectors.joining("/"));
    }
}
