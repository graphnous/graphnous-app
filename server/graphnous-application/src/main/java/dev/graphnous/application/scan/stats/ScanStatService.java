package dev.graphnous.application.scan.stats;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;
import dev.graphnous.scanner.model.Class;
import dev.graphnous.scanner.model.ScanResultSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Keeps the stats of scans, counted from their results.
 */
public class ScanStatService {

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
        final List<ScanResultSchema> results
    ) {
        final var id = this.scanStatRepository.findByScanId(scanId)
            .map(ScanStats::id)
            .orElseGet(ScanStats.ScanStatId::generate);

        final var stats = this.scanStatRepository.save(count(id, scanId, projectId, results));

        log.info(
            "Saved scan stats scanId={} files={} classes={} methods={}",
            scanId.id(),
            stats.numberOfFilesScanned(),
            stats.numberOfClassesParsed(),
            stats.numberOfMethodsParsed()
        );

        return stats;
    }

    /**
     * Counts as the results are stored in the graph: a class listed both
     * in a file and in a package is one class of its module.
     */
    private static ScanStats count(
        final ScanStats.ScanStatId id,
        final Scan.ScanId scanId,
        final Project.ProjectId projectId,
        final List<ScanResultSchema> results
    ) {
        final var files = new HashSet<String>();
        final var classes = new HashSet<String>();

        int methods = 0;

        for (final var result : results) {
            final var target = result.getTarget() == null ? null : result.getTarget().getPath();

            for (final var module : result.getModules()) {
                final var moduleId = target + "|" + module.getPath();

                final var listed = new ArrayList<Class>();

                for (final var file : module.getFiles()) {
                    files.add(moduleId + "|" + file.getPath());
                    listed.addAll(file.getClasses());
                }

                for (final var pkg : module.getPackages()) {
                    listed.addAll(pkg.getClasses());
                }

                for (final var type : listed) {
                    if (classes.add(moduleId + "|" + type.getQualifiedName())) {
                        methods += type.getMethods().size();
                    }
                }
            }
        }

        return new ScanStats(id, scanId, projectId, files.size(), classes.size(), methods);
    }
}
