package dev.graphnous.application.scan.impact;

import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a change of a project affects, from one of its scans (the base, such
 * as of main) to another (the head, such as of a branch): what of the
 * project the head removes or changes, what uses that, and the HTTP calls
 * of the system that no longer reach an endpoint.
 *
 * @param base           the scan compared from
 * @param head           the scan compared to
 * @param summary        how many nodes of each type the head adds, removes
 *                       and changes, of every type
 * @param changes        the removed and changed classes, methods and
 *                       fields, most used first, each with what uses it
 * @param endpoints      the endpoints the head removes, adds and moves
 * @param unused         the endpoints of the head no call of the system
 *                       reaches
 * @param brokenCalls    the calls of the system that reach an endpoint of
 *                       the project in the base and none in the head
 * @param projects       the projects the changes affect: of the usages, and
 *                       of the broken calls, with how many places in each
 * @param truncated      whether there are more changes than the report
 *                       follows, as it follows at most a limit of them
 */
public record ImpactReport(
    Scan base,
    Scan head,
    List<ScanGraph.TypeChanges> summary,
    List<Change> changes,
    Endpoints endpoints,
    UnusedEndpoints unused,
    List<BrokenCall> brokenCalls,
    List<AffectedProject> projects,
    boolean truncated
) {

    public ImpactReport {
        summary = List.copyOf(summary);
        changes = List.copyOf(changes);
        brokenCalls = List.copyOf(brokenCalls);
        projects = List.copyOf(projects);
    }

    /**
     * A report without endpoints nothing calls.
     */
    public ImpactReport(
        final Scan base,
        final Scan head,
        final List<ScanGraph.TypeChanges> summary,
        final List<Change> changes,
        final Endpoints endpoints,
        final List<BrokenCall> brokenCalls,
        final List<AffectedProject> projects,
        final boolean truncated
    ) {
        this(base, head, summary, changes, endpoints, UnusedEndpoints.NONE, brokenCalls, projects, truncated);
    }

    /**
     * How a node changed. In Java a method's parameters are part of its id,
     * so a changed signature is a removed method and an added one, which
     * the report pairs where it can tell.
     */
    public enum Kind {
        /**
         * The head no longer has it.
         */
        REMOVED,
        /**
         * The head has it, with other properties.
         */
        CHANGED,
        /**
         * The head has it under another name: the one method of its class
         * with its parameters, or field with its type, that the head adds.
         */
        RENAMED,
        /**
         * The head has a method of its class and name with other
         * parameters: the one it adds.
         */
        SIGNATURE_CHANGED
    }

    /**
     * A removed or changed class, method or field, with what uses it: in
     * the base scan for a removed one, as what used it may not have been
     * changed with it; in the head scan for a changed one.
     *
     * @param id             its id in the scan its usages are of
     * @param scanId         that scan
     * @param properties     for a changed node, the properties that differ
     * @param replacement    for a renamed node, or a method whose signature
     *                       changed, the key of what the head has instead;
     *                       null otherwise
     * @param usages         the places that use it, at most a limit of
     *                       them, those outside tests first
     * @param usageCount     how many places use it, of every project
     * @param testUsageCount how many of those are in test code
     * @param updatedUsageCount how many of those the head updated: for a
     *                       renamed node, or a method whose signature
     *                       changed, the places that use it themselves and
     *                       use its replacement in the head
     * @param updatedUsers   the keys of the users of those places: their ids
     *                       without the scan's
     * @param truncated      whether there are more usages than it counts,
     *                       as finding them stops at a limit
     * @param projects       the project of each scan the usages are in
     */
    public record Change(
        Kind kind,
        String id,
        Scan.ScanId scanId,
        String type,
        String name,
        List<ScanGraph.PropertyChange> properties,
        String replacement,
        List<ScanUsages.Usage> usages,
        int usageCount,
        int testUsageCount,
        int updatedUsageCount,
        Set<String> updatedUsers,
        boolean truncated,
        Map<Scan.ScanId, Project.ProjectId> projects
    ) {

        public Change {
            properties = List.copyOf(properties);
            usages = List.copyOf(usages);
            updatedUsers = Set.copyOf(updatedUsers);
            projects = Map.copyOf(projects);
        }

        /**
         * A change of which the head updated no place.
         */
        public Change(
            final Kind kind,
            final String id,
            final Scan.ScanId scanId,
            final String type,
            final String name,
            final List<ScanGraph.PropertyChange> properties,
            final String replacement,
            final List<ScanUsages.Usage> usages,
            final int usageCount,
            final int testUsageCount,
            final boolean truncated,
            final Map<Scan.ScanId, Project.ProjectId> projects
        ) {
            this(kind, id, scanId, type, name, properties, replacement, usages, usageCount, testUsageCount, 0, Set.of(), truncated, projects);
        }

        /**
         * A change without a replacement, whose usages are none in tests.
         */
        public Change(
            final Kind kind,
            final String id,
            final Scan.ScanId scanId,
            final String type,
            final String name,
            final List<ScanGraph.PropertyChange> properties,
            final List<ScanUsages.Usage> usages,
            final int usageCount,
            final boolean truncated,
            final Map<Scan.ScanId, Project.ProjectId> projects
        ) {
            this(kind, id, scanId, type, name, properties, null, usages, usageCount, 0, truncated, projects);
        }

        /**
         * How many places outside tests use it.
         */
        public int codeUsageCount() {
            return usageCount - testUsageCount;
        }

        /**
         * How many places use it that the head did not update.
         */
        public int openUsageCount() {
            return usageCount - updatedUsageCount;
        }

        /**
         * Whether the head updated the place.
         */
        public boolean isUpdated(final ScanUsages.Usage usage) {
            return usage.depth() == 1 && usage.user() != null && updatedUsers.contains(ImpactReport.key(usage.user().id()));
        }
    }

    /**
     * @param removed the endpoints only the base has, as named in the
     *                comparison, such as GET /api/orders/{id}
     * @param added   the endpoints only the head has
     * @param moved   the endpoints whose path or method changed: one the
     *                base has and one the head has, of the same handler;
     *                in neither list
     */
    public record Endpoints(
        List<ScanGraph.Snapshot> removed,
        List<ScanGraph.Snapshot> added,
        List<Move> moved
    ) {

        public Endpoints {
            removed = List.copyOf(removed);
            added = List.copyOf(added);
            moved = List.copyOf(moved);
        }

        public Endpoints(final List<ScanGraph.Snapshot> removed, final List<ScanGraph.Snapshot> added) {
            this(removed, added, List.of());
        }
    }

    /**
     * The endpoints of the head that no call Graphnous found reaches, in
     * the project or the other projects of its system: possibly unused, as
     * clients outside the system may still call them.
     *
     * @param noLongerCalled those calls reached in the base, and none
     *                       reaches in the head: the change removed or
     *                       changed their last callers
     * @param notCalledYet   those the head adds, or moves, that nothing
     *                       calls
     * @param others         the rest, at most a limit of them
     * @param otherCount     how many the rest are
     */
    public record UnusedEndpoints(
        List<UnusedEndpoint> noLongerCalled,
        List<UnusedEndpoint> notCalledYet,
        List<UnusedEndpoint> others,
        int otherCount
    ) {

        public static final UnusedEndpoints NONE = new UnusedEndpoints(List.of(), List.of(), List.of(), 0);

        public UnusedEndpoints {
            noLongerCalled = List.copyOf(noLongerCalled);
            notCalledYet = List.copyOf(notCalledYet);
            others = List.copyOf(others);
        }
    }

    /**
     * @param handler what serves it; null when the scan does not say
     */
    public record UnusedEndpoint(HttpLinks.Endpoint endpoint, HttpLinks.Source handler) {
    }

    /**
     * A node's id without its scan's, which is the same in every scan of
     * the repository.
     */
    static String key(final String id) {
        final var bar = id.indexOf('|');

        return bar < 0 ? id : id.substring(bar + 1);
    }

    /**
     * An endpoint as the base and as the head has it.
     */
    public record Move(ScanGraph.Snapshot from, ScanGraph.Snapshot to) {
    }

    /**
     * A call that requests an endpoint of the project in the base scan and
     * none in the head.
     *
     * @param caller    where the call is; null when the scan does not say
     * @param endpoints the endpoints of the base it reached
     */
    public record BrokenCall(
        HttpLinks.Call call,
        HttpLinks.Source caller,
        List<HttpLinks.Endpoint> endpoints
    ) {

        public BrokenCall {
            endpoints = List.copyOf(endpoints);
        }
    }

    /**
     * @param usages      how many usages of the changes are in the project
     * @param brokenCalls how many of its calls no longer reach an endpoint
     */
    public record AffectedProject(
        Project.ProjectId projectId,
        int usages,
        int brokenCalls
    ) {
    }
}
