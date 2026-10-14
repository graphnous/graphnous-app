package dev.graphnous.domain.scan;

import dev.graphnous.domain.project.Project;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @param startedAt when the scan started running; null until then
 * @param enhancers the names of the enhancers chosen to run on its results
 *                  when it was created; null when none were chosen, which
 *                  runs every installed enhancer, and empty for none
 * @param excludes  the patterns of the files left out of its results, such
 *                  as {@code *.spec.ts}; none when every file is kept
 * @param languages the languages it scans, such as {@code JAVA}; none when
 *                  it scans every language it finds
 */
public record Scan(
    ScanId id,
    Project.ProjectId projectId,
    ScanStatus status,
    SourceRevision revision,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Set<String> enhancers,
    List<String> excludes,
    Set<String> languages
) {

    public Scan {
        enhancers = enhancers == null ? null : Set.copyOf(enhancers);
        excludes = excludes == null ? List.of() : List.copyOf(excludes);
        languages = languages == null ? Set.of() : Set.copyOf(languages);
    }

    /**
     * A scan of every language it finds.
     */
    public Scan(
        final ScanId id,
        final Project.ProjectId projectId,
        final ScanStatus status,
        final SourceRevision revision,
        final Instant createdAt,
        final Instant updatedAt,
        final Instant startedAt,
        final Set<String> enhancers,
        final List<String> excludes
    ) {
        this(id, projectId, status, revision, createdAt, updatedAt, startedAt, enhancers, excludes, null);
    }

    /**
     * A scan that keeps every file.
     */
    public Scan(
        final ScanId id,
        final Project.ProjectId projectId,
        final ScanStatus status,
        final SourceRevision revision,
        final Instant createdAt,
        final Instant updatedAt,
        final Instant startedAt,
        final Set<String> enhancers
    ) {
        this(id, projectId, status, revision, createdAt, updatedAt, startedAt, enhancers, null, null);
    }

    /**
     * A scan for which no enhancers were chosen.
     */
    public Scan(
        final ScanId id,
        final Project.ProjectId projectId,
        final ScanStatus status,
        final SourceRevision revision,
        final Instant createdAt,
        final Instant updatedAt,
        final Instant startedAt
    ) {
        this(id, projectId, status, revision, createdAt, updatedAt, startedAt, null, null, null);
    }

    /**
     * The scan with a new status at {@code at}. The first move to RUNNING
     * records when the scan started.
     */
    public Scan withStatus(
        final ScanStatus newStatus,
        final Instant at
    ) {
        final var started = startedAt == null && newStatus == ScanStatus.RUNNING
            ? at
            : startedAt;

        return new Scan(id, projectId, newStatus, revision, createdAt, at, started, enhancers, excludes, languages);
    }

    /**
     * The scan of the commit it checked out, as its full hash; what it was
     * asked for stays its requested revision.
     */
    public Scan withRevision(final String commit) {
        return new Scan(
            id,
            projectId,
            status,
            new SourceRevision(commit, revision.branch(), revision.requestedRevision()),
            createdAt,
            updatedAt,
            startedAt,
            enhancers,
            excludes,
            languages
        );
    }

    public record ScanId(UUID id) {
        public static Scan.ScanId generate() {
            return new Scan.ScanId(UUID.randomUUID());
        }
    }

    public enum ScanStatus {
        PENDING(false),
        QUEUED(false),
        RUNNING(false),
        COMPLETED(true),
        FAILED(true)
        ;

        private boolean endState;

        ScanStatus(boolean endState) {
            this.endState = endState;
        }

        public boolean isEndState() {
            return this.endState;
        }
    }

    /**
     * The source a scan is of.
     *
     * @param revision          the commit the scan is of, as its full hash
     *                          once the scan has checked it out; null until
     *                          then. For an uploaded scan, the revision it
     *                          was uploaded with
     * @param branch            the branch the scan is of
     * @param requestedRevision the revision the scan was asked for, such as
     *                          a tag or a short commit hash, as it was
     *                          given; null for the tip of the branch
     */
    public record SourceRevision(String revision, String branch, String requestedRevision) {

        /**
         * A source whose revision is the one it was asked for, as for an
         * uploaded scan.
         */
        public SourceRevision(final String revision, final String branch) {
            this(revision, branch, revision);
        }

        /**
         * The source to check out for a scan asked for this revision, or
         * the tip of the branch when it is null; the commit is known once
         * it is checked out.
         */
        public static SourceRevision requested(final String requestedRevision, final String branch) {
            return new SourceRevision(null, branch, requestedRevision);
        }
    }
}
