package dev.graphnous.api.scan;

import dev.graphnous.application.notification.ScanNotifier;
import dev.graphnous.application.scan.ScanDeleter;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.application.scan.ScanRetention;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deletes expired scans every 6 hours, and the oldest scans of a project
 * when a new one would exceed the number it keeps. A limit of
 * {@value #UNLIMITED} turns either off. Each project with deleted scans gets
 * a notification of how many.
 */
@Component
public class ScheduledScanRetention implements ScanRetention {

    static final int UNLIMITED = -1;

    private final ScanRepository scanRepository;
    private final ScanDeleter scanDeleter;
    private final ScanNotifier scanNotifier;

    private final int days;
    private final int maxPerProject;
    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(ScheduledScanRetention.class);

    @Autowired
    public ScheduledScanRetention(
        final ScanRepository scanRepository,
        final ScanDeleter scanDeleter,
        final ScanNotifier scanNotifier,
        @Value("${graphnous.scans.retention.days:-1}") final int days,
        @Value("${graphnous.scans.retention.max-per-project:-1}") final int maxPerProject
    ) {
        this(scanRepository, scanDeleter, scanNotifier, days, maxPerProject, Clock.systemUTC());
    }

    ScheduledScanRetention(
        final ScanRepository scanRepository,
        final ScanDeleter scanDeleter,
        final ScanNotifier scanNotifier,
        final int days,
        final int maxPerProject,
        final Clock clock
    ) {
        this.scanRepository = scanRepository;
        this.scanDeleter = scanDeleter;
        this.scanNotifier = scanNotifier;

        this.days = requireLimit("graphnous.scans.retention.days", days);
        this.maxPerProject = requireLimit("graphnous.scans.retention.max-per-project", maxPerProject);
        this.clock = clock;
    }

    @Override
    @Scheduled(fixedRateString = "PT6H")
    public void deleteExpired() {
        if (days == UNLIMITED) {
            return;
        }

        final var cutoff = clock.instant().minus(Duration.ofDays(days));
        final var expired = scanRepository.findFinishedCreatedBefore(cutoff);

        if (!expired.isEmpty()) {
            log.info("Deleting {} scans created before {}", expired.size(), cutoff);
        }

        final var byProject = expired.stream().collect(Collectors.groupingBy(
            Scan::projectId,
            LinkedHashMap::new,
            Collectors.mapping(Scan::id, Collectors.toList())
        ));

        byProject.forEach((projectId, scanIds) -> scanNotifier.scansDeleted(
            projectId,
            delete(scanIds),
            "they were older than %d %s".formatted(days, days == 1 ? "day" : "days")
        ));
    }

    @Override
    public void makeRoomFor(final Project.ProjectId projectId) {
        if (maxPerProject == UNLIMITED) {
            return;
        }

        // Room for the new scan too
        final var excess = scanRepository.count(projectId) - maxPerProject + 1;

        if (excess <= 0) {
            return;
        }

        final var oldest = scanRepository.findOldestFinished(projectId, excess);

        if (oldest.size() < excess) {
            // The rest are active; the new scan exceeds the limit until they finish
            log.warn(
                "Project projectId={} keeps {} scans more than {}, as they are active",
                projectId.id(),
                excess - oldest.size(),
                maxPerProject
            );
        }

        if (!oldest.isEmpty()) {
            log.info("Deleting the {} oldest scans of project projectId={}", oldest.size(), projectId.id());
        }

        scanNotifier.scansDeleted(
            projectId,
            delete(oldest),
            "a project keeps at most %d %s".formatted(maxPerProject, maxPerProject == 1 ? "scan" : "scans")
        );
    }

    /**
     * @return how many were deleted
     */
    private int delete(final List<Scan.ScanId> scanIds) {
        var deleted = 0;

        for (final var scanId : scanIds) {
            try {
                scanDeleter.deleteScan(scanId);
                deleted++;
            } catch (final RuntimeException e) {
                // Tried again on the next run; one scan does not keep the others
                log.error("Deleting scan scanId={} failed", scanId.id(), e);
            }
        }

        return deleted;
    }

    private static int requireLimit(final String property, final int value) {
        if (value != UNLIMITED && value < 1) {
            throw new IllegalArgumentException(
                "%s must be %d (keep all) or at least 1, but is %d".formatted(property, UNLIMITED, value)
            );
        }

        return value;
    }
}
