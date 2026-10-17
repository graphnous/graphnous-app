package dev.graphnous.application.scan.impact;

import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.List;
import java.util.Map;

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
 * @param endpoints      the endpoints the head removes and adds; a changed
 *                       path or method is one of each
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
     * How a node changed: the head no longer has it, or has it with other
     * properties. In Java a method's parameters are part of its id, so a
     * changed signature is a removed method, and a method of the new
     * signature added.
     */
    public enum Kind {
        REMOVED,
        CHANGED
    }

    /**
     * A removed or changed class, method or field, with what uses it: in
     * the base scan for a removed one, as what used it may not have been
     * changed with it; in the head scan for a changed one.
     *
     * @param id         its id in the scan its usages are of
     * @param scanId     that scan
     * @param properties for a changed node, the properties that differ
     * @param usages     the places that use it, at most a limit of them
     * @param usageCount how many places use it, of every project
     * @param truncated  whether there are more usages than it counts, as
     *                   finding them stops at a limit
     * @param projects   the project of each scan the usages are in
     */
    public record Change(
        Kind kind,
        String id,
        Scan.ScanId scanId,
        String type,
        String name,
        List<ScanGraph.PropertyChange> properties,
        List<ScanUsages.Usage> usages,
        int usageCount,
        boolean truncated,
        Map<Scan.ScanId, Project.ProjectId> projects
    ) {

        public Change {
            properties = List.copyOf(properties);
            usages = List.copyOf(usages);
            projects = Map.copyOf(projects);
        }
    }

    /**
     * @param removed the endpoints only the base has, as named in the
     *                comparison, such as GET /api/orders/{id}
     * @param added   the endpoints only the head has
     */
    public record Endpoints(
        List<ScanGraph.Snapshot> removed,
        List<ScanGraph.Snapshot> added
    ) {

        public Endpoints {
            removed = List.copyOf(removed);
            added = List.copyOf(added);
        }
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
