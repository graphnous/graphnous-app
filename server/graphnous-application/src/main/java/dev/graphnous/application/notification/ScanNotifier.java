package dev.graphnous.application.notification;

import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.function.Function;

/**
 * Notifies the system of what happens to its scans: a scan that starts,
 * completes or fails, and scans that retention deletes.
 * <p>
 * The server does this itself, also outside of a request (recovery and
 * retention run on a schedule), so it stores the notifications without the
 * authorization checks of the {@link NotificationService}. A notification
 * that cannot be stored is logged and left out: it never changes what
 * happens to the scan.
 */
public class ScanNotifier {

    private final NotificationRepository notificationRepository;
    private final ProjectRepository projectRepository;
    private final Clock clock;

    private static final Logger log = LoggerFactory.getLogger(ScanNotifier.class);

    public ScanNotifier(
        final NotificationRepository notificationRepository,
        final ProjectRepository projectRepository,
        final Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.projectRepository = projectRepository;
        this.clock = clock;
    }

    public void scanStarted(final Scan scan) {
        notify(scan, "Scan started", project -> "The scan of %s%s started.".formatted(
            project.name(),
            describe(scan.revision())
        ));
    }

    public void scanCompleted(final Scan scan) {
        notify(scan, "Scan completed", project -> "The scan of %s%s completed.".formatted(
            project.name(),
            describe(scan.revision())
        ));
    }

    /**
     * @param reason why it failed, such as the error of the step it failed in
     */
    public void scanFailed(
        final Scan scan,
        final String reason
    ) {
        notify(scan, "Scan failed", project -> "The scan of %s%s failed: %s".formatted(
            project.name(),
            describe(scan.revision()),
            reason
        ));
    }

    /**
     * Scans of the project that retention deleted; a notification of the
     * project, as the scans are gone.
     *
     * @param reason why they were deleted, such as "they were older than
     *               30 days"
     */
    public void scansDeleted(
        final Project.ProjectId projectId,
        final int count,
        final String reason
    ) {
        if (count <= 0) {
            return;
        }

        try {
            final var project = this.projectRepository.findById(projectId);

            save(
                project,
                null,
                "Scans deleted",
                "Deleted %d %s of %s, as %s.".formatted(
                    count,
                    count == 1 ? "scan" : "scans",
                    project.name(),
                    reason
                )
            );
        } catch (final RuntimeException e) {
            log.warn("Notifying of deleted scans of project projectId={} failed", projectId.id(), e);
        }
    }

    private void notify(
        final Scan scan,
        final String title,
        final Function<Project, String> content
    ) {
        try {
            final var project = this.projectRepository.findById(scan.projectId());

            save(project, scan.id(), title, content.apply(project));
        } catch (final RuntimeException e) {
            log.warn("Notifying '{}' of scan scanId={} failed", title, scan.id().id(), e);
        }
    }

    private void save(
        final Project project,
        final Scan.ScanId scanId,
        final String title,
        final String content
    ) {
        this.notificationRepository.save(new Notification(
            Notification.NotificationId.generate(),
            project.systemId(),
            project.id(),
            scanId,
            title,
            content,
            false,
            this.clock.instant()
        ));
    }

    private static String describe(final Scan.SourceRevision revision) {
        if (revision == null) {
            return "";
        }

        final var branch = revision.branch() == null || revision.branch().isBlank() ? null : revision.branch();
        // The commit once the scan has checked it out, what it was asked for until then
        final var known = revision.revision() == null ? revision.requestedRevision() : revision.revision();
        final var commit = known == null || known.isBlank() ? null : known;

        if (branch != null && commit != null) {
            return " (branch %s, revision %s)".formatted(branch, commit);
        }

        if (branch != null) {
            return " (branch %s)".formatted(branch);
        }

        return commit == null ? "" : " (revision %s)".formatted(commit);
    }
}
