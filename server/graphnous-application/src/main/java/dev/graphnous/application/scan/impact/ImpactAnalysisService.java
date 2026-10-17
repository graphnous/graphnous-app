package dev.graphnous.application.scan.impact;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.scan.links.HttpLinkMatcher;
import dev.graphnous.application.scan.links.HttpLinkRepository;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Finds what a change of a project affects, from one of its scans to
 * another, for a user who may read both: getting them, their project's
 * system and its scans checks the user's permissions and organization.
 * <p>
 * It is the same every time for the same scans: what the head removes or
 * changes comes from comparing the scans, what uses that from the usages
 * stored with them, and which calls no longer reach an endpoint from
 * matching the calls of the system's latest scans with the endpoints of
 * each, as HTTP links are found.
 */
public class ImpactAnalysisService {

    /**
     * The types of node whose changes are followed to what uses them.
     */
    static final Set<String> FOLLOWED_TYPES = Set.of("Class", "Method", "Field");

    /**
     * The most removed and changed nodes a report follows: each is a lookup
     * of its usages.
     */
    public static final int MAX_CHANGES = 50;

    /**
     * The most usages a change lists; it counts them all.
     */
    public static final int MAX_USAGES = 25;

    private final ScanService scanService;
    private final ProjectService projectService;
    private final ScanGraphService scanGraphService;
    private final ScanUsageService scanUsageService;
    private final HttpLinkRepository httpLinkRepository;

    public ImpactAnalysisService(
        final ScanService scanService,
        final ProjectService projectService,
        final ScanGraphService scanGraphService,
        final ScanUsageService scanUsageService,
        final HttpLinkRepository httpLinkRepository
    ) {
        this.scanService = scanService;
        this.projectService = projectService;
        this.scanGraphService = scanGraphService;
        this.scanUsageService = scanUsageService;
        this.httpLinkRepository = httpLinkRepository;
    }

    /**
     * @param depth how far out to follow the usages of each change, 1 to
     *              {@link ScanUsageService#MAX_DEPTH}; 1, only what uses it
     *              itself, when null
     * @throws ValidationException when the scans are of different projects,
     *                             or the depth is out of range
     */
    public ImpactReport analyze(
        final RequestContext context,
        final Scan.ScanId baseId,
        final Scan.ScanId headId,
        final Integer depth
    ) {
        final var base = scanService.getScan(context, baseId);
        final var head = scanService.getScan(context, headId);

        if (!base.projectId().equals(head.projectId())) {
            throw new ValidationException("The scans to compare must be of the same project");
        }

        final var steps = depth == null ? 1 : depth;

        if (steps < 1 || steps > ScanUsageService.MAX_DEPTH) {
            throw new ValidationException("depth must be between 1 and " + ScanUsageService.MAX_DEPTH);
        }

        final var types = new HashSet<>(FOLLOWED_TYPES);
        types.add("Endpoint");

        final var comparison = scanGraphService.compare(context, base.id(), head.id(), types, Integer.MAX_VALUE);

        // Removed first, as what used them is the likelier to break
        final var candidates = Stream.concat(
                comparison.removed().stream()
                    .filter(node -> FOLLOWED_TYPES.contains(node.type()))
                    .map(node -> new Candidate(ImpactReport.Kind.REMOVED, base, node.key(), node.type(), node.name(), List.of())),
                comparison.changed().stream()
                    .filter(node -> FOLLOWED_TYPES.contains(node.type()))
                    .map(node -> new Candidate(ImpactReport.Kind.CHANGED, head, node.key(), node.type(), node.name(), node.properties()))
            )
            .toList();

        final var usagesByProject = new TreeMap<String, Integer>();
        final var projectIds = new HashMap<String, Project.ProjectId>();

        final var changes = candidates.stream()
            .limit(MAX_CHANGES)
            .map(candidate -> follow(context, candidate, steps, usagesByProject, projectIds))
            .sorted(Comparator.comparingInt(ImpactReport.Change::usageCount).reversed())
            .toList();

        final var brokenCalls = brokenCalls(context, base, head);

        final var endpoints = new ImpactReport.Endpoints(
            comparison.removed().stream().filter(node -> node.type().equals("Endpoint")).toList(),
            comparison.added().stream().filter(node -> node.type().equals("Endpoint")).toList()
        );

        return new ImpactReport(
            base,
            head,
            comparison.summary(),
            changes,
            endpoints,
            brokenCalls,
            projects(usagesByProject, projectIds, brokenCalls),
            candidates.size() > MAX_CHANGES || comparison.truncated()
        );
    }

    private record Candidate(
        ImpactReport.Kind kind,
        Scan scan,
        String key,
        String type,
        String name,
        List<ScanGraph.PropertyChange> properties
    ) {
    }

    /**
     * The change with what uses it, counting its usages by project.
     */
    private ImpactReport.Change follow(
        final RequestContext context,
        final Candidate candidate,
        final int depth,
        final Map<String, Integer> usagesByProject,
        final Map<String, Project.ProjectId> projectIds
    ) {
        final var id = candidate.scan().id().id() + "|" + candidate.key();
        final var found = scanUsageService.getUsages(context, candidate.scan().id(), id, depth);

        found.usages().forEach(usage -> {
            final var project = found.projects().get(usage.scanId() == null ? candidate.scan().id() : usage.scanId());

            if (project != null) {
                usagesByProject.merge(project.id().toString(), 1, Integer::sum);
                projectIds.putIfAbsent(project.id().toString(), project);
            }
        });

        return new ImpactReport.Change(
            candidate.kind(),
            id,
            candidate.scan().id(),
            candidate.type(),
            candidate.name(),
            candidate.properties(),
            found.usages().stream().limit(MAX_USAGES).toList(),
            found.usages().size(),
            found.truncated(),
            found.projects()
        );
    }

    /**
     * The calls of the system that reach an endpoint of the base and none
     * of the head: of the latest completed scan of each other project, and
     * of the head itself.
     */
    private List<ImpactReport.BrokenCall> brokenCalls(final RequestContext context, final Scan base, final Scan head) {
        final var systemId = projectService.getProject(context, head.projectId()).systemId();

        final var callers = new ArrayList<Scan>();
        callers.add(head);
        scanService.getNewestCompletedScans(context, systemId).stream()
            .filter(scan -> !scan.projectId().equals(head.projectId()))
            .forEach(callers::add);

        final var calls = httpLinkRepository.findCalls(callers);
        final var before = HttpLinkMatcher.match(calls, httpLinkRepository.findEndpoints(List.of(base)));
        final var after = HttpLinkMatcher.match(calls, httpLinkRepository.findEndpoints(List.of(head)));

        final var stillReaching = new HashSet<String>();
        after.forEach(link -> stillReaching.add(link.call().id()));

        final var broken = new LinkedHashMap<String, List<HttpLinks.Link>>();
        before.stream()
            .filter(link -> !stillReaching.contains(link.call().id()))
            .forEach(link -> broken.computeIfAbsent(link.call().id(), id -> new ArrayList<>()).add(link));

        if (broken.isEmpty()) {
            return List.of();
        }

        final var sources = httpLinkRepository.findSources(callers, broken.keySet());

        return broken.values().stream()
            .map(links -> new ImpactReport.BrokenCall(
                links.getFirst().call(),
                sources.get(links.getFirst().call().id()),
                links.stream().map(HttpLinks.Link::endpoint).toList()
            ))
            .toList();
    }

    /**
     * The projects with usages of the changes or broken calls, most
     * affected first.
     */
    private static List<ImpactReport.AffectedProject> projects(
        final Map<String, Integer> usagesByProject,
        final Map<String, Project.ProjectId> projectIds,
        final List<ImpactReport.BrokenCall> brokenCalls
    ) {
        final var broken = new TreeMap<String, Integer>();

        brokenCalls.forEach(call -> {
            final var project = call.call().projectId();
            broken.merge(project.id().toString(), 1, Integer::sum);
            projectIds.putIfAbsent(project.id().toString(), project);
        });

        final var keys = new TreeSet<String>();
        keys.addAll(usagesByProject.keySet());
        keys.addAll(broken.keySet());

        return keys.stream()
            .map(key -> new ImpactReport.AffectedProject(
                projectIds.get(key),
                usagesByProject.getOrDefault(key, 0),
                broken.getOrDefault(key, 0)
            ))
            .sorted(Comparator.comparingInt((ImpactReport.AffectedProject project) -> project.usages() + project.brokenCalls())
                .reversed())
            .toList();
    }
}
