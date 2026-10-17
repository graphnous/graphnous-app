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
import dev.graphnous.application.scan.source.SourcePaths;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.scan.usages.ScanUsages;
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

    /**
     * The most endpoints a report lists of those nothing calls that the
     * change has nothing to do with; it counts them all.
     */
    public static final int MAX_OTHER_UNUSED_ENDPOINTS = 20;

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

        final var replacements = replacements(comparison.removed(), comparison.added());

        // Removed first, as what used them is the likelier to break
        final var candidates = Stream.concat(
                comparison.removed().stream()
                    .filter(node -> FOLLOWED_TYPES.contains(node.type()))
                    .map(node -> {
                        final var replacement = replacements.get(node.key());

                        return new Candidate(
                            replacement == null ? ImpactReport.Kind.REMOVED : replacement.kind(),
                            base, node.key(), node.type(), node.name(), List.of(),
                            replacement == null ? null : replacement.key()
                        );
                    }),
                comparison.changed().stream()
                    .filter(node -> FOLLOWED_TYPES.contains(node.type()))
                    .map(node -> new Candidate(ImpactReport.Kind.CHANGED, head, node.key(), node.type(), node.name(), node.properties(), null))
            )
            .toList();

        final var usagesByProject = new TreeMap<String, Integer>();
        final var projectIds = new HashMap<String, Project.ProjectId>();

        final var changes = candidates.stream()
            .limit(MAX_CHANGES)
            .map(candidate -> follow(context, candidate, head, steps, usagesByProject, projectIds))
            // What code still has to update first, then what tests do, then what the head updated
            .sorted(Comparator.comparingInt(Followed::openInCode)
                .thenComparingInt(followed -> followed.change().openUsageCount())
                .thenComparingInt(followed -> followed.change().usageCount())
                .reversed())
            .map(Followed::change)
            .toList();

        final var endpoints = endpoints(
            comparison.removed().stream().filter(node -> node.type().equals("Endpoint")).toList(),
            comparison.added().stream().filter(node -> node.type().equals("Endpoint")).toList()
        );

        final var http = http(context, base, head, endpoints);
        final var brokenCalls = http.brokenCalls();

        return new ImpactReport(
            base,
            head,
            comparison.summary(),
            changes,
            endpoints,
            http.unused(),
            brokenCalls,
            projects(usagesByProject, projectIds, brokenCalls),
            candidates.size() > MAX_CHANGES || comparison.truncated()
        );
    }

    /**
     * Where the report's places are in their repositories, with links to
     * them on GitHub: of the scans compared, and those of the other
     * projects the places are in, as far as the user may read them.
     */
    public ImpactComment.Sources sources(final RequestContext context, final ImpactReport report) {
        final var scans = new HashMap<Scan.ScanId, Scan>();
        scans.put(report.base().id(), report.base());
        scans.put(report.head().id(), report.head());

        final var others = new HashSet<Scan.ScanId>();
        report.changes().forEach(change -> others.addAll(change.projects().keySet()));
        report.brokenCalls().forEach(broken -> others.add(broken.call().scanId()));
        others.removeAll(scans.keySet());

        others.forEach(scanId -> {
            try {
                scans.put(scanId, scanService.getScan(context, scanId));
            } catch (final RuntimeException e) {
                // A place in it is named without a link
            }
        });

        final var projects = new HashMap<Project.ProjectId, Project>();
        final var byScan = new HashMap<Scan.ScanId, Project>();

        scans.forEach((scanId, scan) -> {
            try {
                byScan.put(scanId, projects.computeIfAbsent(scan.projectId(), id -> projectService.getProject(context, id)));
            } catch (final RuntimeException e) {
                // Likewise
            }
        });

        return new RepositorySources(scans, byScan);
    }

    private record Candidate(
        ImpactReport.Kind kind,
        Scan scan,
        String key,
        String type,
        String name,
        List<ScanGraph.PropertyChange> properties,
        String replacement
    ) {
    }

    /**
     * What the head added in place of a removed method or field: of the
     * same class, the one method of the same name with other parameters,
     * which is a changed signature; or else the one method with the same
     * parameters, or field of the same type, under another name, which is
     * a rename. Each added node replaces one removed node at most, and only
     * when no other could be meant.
     */
    static Map<String, Replacement> replacements(final List<ScanGraph.Snapshot> removed, final List<ScanGraph.Snapshot> added) {
        final var found = new LinkedHashMap<String, Replacement>();
        final var taken = new HashSet<String>();

        // Signatures first: a method keeps its name more often than its parameters
        for (final var kind : List.of(ImpactReport.Kind.SIGNATURE_CHANGED, ImpactReport.Kind.RENAMED)) {
            for (final var node : removed) {
                if (found.containsKey(node.key()) || !(node.type().equals("Method") || node.type().equals("Field"))) {
                    continue;
                }

                final var matches = added.stream()
                    .filter(other -> !taken.contains(other.key()) && matches(kind, node, other))
                    .toList();

                if (matches.size() != 1) {
                    continue;
                }

                final var match = matches.getFirst();

                // And it the only removed one it could replace
                final var rivals = removed.stream()
                    .filter(other -> !found.containsKey(other.key()) && matches(kind, other, match))
                    .count();

                if (rivals == 1) {
                    found.put(node.key(), new Replacement(kind, match.key()));
                    taken.add(match.key());
                }
            }
        }

        return found;
    }

    record Replacement(ImpactReport.Kind kind, String key) {
    }

    private static boolean matches(final ImpactReport.Kind kind, final ScanGraph.Snapshot removed, final ScanGraph.Snapshot added) {
        if (!removed.type().equals(added.type()) || !parent(removed.key()).equals(parent(added.key()))) {
            return false;
        }

        final var sameName = removed.name() != null && removed.name().equals(added.name());

        if (kind == ImpactReport.Kind.SIGNATURE_CHANGED) {
            return removed.type().equals("Method") && sameName;
        }

        // What makes it the same under another name: a method's parameters, a field's type
        final var shape = removed.type().equals("Method") ? "parameterTypes" : "type";
        final var before = removed.properties().get(shape);

        return !sameName && before != null && before.equals(added.properties().get(shape));
    }

    /**
     * The endpoints the head removes and adds, with those of the same
     * handler, one each, paired as moved.
     */
    static ImpactReport.Endpoints endpoints(final List<ScanGraph.Snapshot> removed, final List<ScanGraph.Snapshot> added) {
        final var moved = new ArrayList<ImpactReport.Move>();
        final var gone = new ArrayList<ScanGraph.Snapshot>();
        final var paired = new HashSet<String>();

        for (final var endpoint : removed) {
            final var handler = parent(endpoint.key());
            final var fromHandler = added.stream().filter(other -> parent(other.key()).equals(handler)).toList();
            final var sameHandler = removed.stream().filter(other -> parent(other.key()).equals(handler)).count();

            if (fromHandler.size() == 1 && sameHandler == 1) {
                moved.add(new ImpactReport.Move(endpoint, fromHandler.getFirst()));
                paired.add(fromHandler.getFirst().key());
            } else {
                gone.add(endpoint);
            }
        }

        return new ImpactReport.Endpoints(
            gone,
            added.stream().filter(endpoint -> !paired.contains(endpoint.key())).toList(),
            moved
        );
    }

    /**
     * The key of what a node is part of, such as a method's class.
     */
    private static String parent(final String key) {
        final var last = key.lastIndexOf('|');

        return last < 0 ? "" : key.substring(0, last);
    }

    /**
     * The change with what uses it, counting its usages by project.
     */
    /**
     * @param openInCode how many places outside tests use the change that
     *                   the head did not update
     */
    private record Followed(ImpactReport.Change change, int openInCode) {
    }

    private Followed follow(
        final RequestContext context,
        final Candidate candidate,
        final Scan head,
        final int depth,
        final Map<String, Integer> usagesByProject,
        final Map<String, Project.ProjectId> projectIds
    ) {
        final var id = candidate.scan().id().id() + "|" + candidate.key();
        final var found = scanUsageService.getUsages(context, candidate.scan().id(), id, depth);
        final var updated = updatedUsers(context, candidate, head, found.usages());

        // Those the head did not update first, and of those, those in code
        final var usages = found.usages().stream()
            .sorted(Comparator.comparing((ScanUsages.Usage usage) -> isUpdated(usage, updated))
                .thenComparing(ImpactAnalysisService::inTest))
            .toList();
        final var inTests = (int) usages.stream().filter(ImpactAnalysisService::inTest).count();
        final var updatedCount = (int) usages.stream().filter(usage -> isUpdated(usage, updated)).count();

        // The places the change leaves to update
        found.usages().stream().filter(usage -> !isUpdated(usage, updated)).forEach(usage -> {
            final var project = found.projects().get(usage.scanId() == null ? candidate.scan().id() : usage.scanId());

            if (project != null) {
                usagesByProject.merge(project.id().toString(), 1, Integer::sum);
                projectIds.putIfAbsent(project.id().toString(), project);
            }
        });

        final var openInCode = (int) usages.stream().filter(usage -> !isUpdated(usage, updated) && !inTest(usage)).count();

        return new Followed(new ImpactReport.Change(
            candidate.kind(),
            id,
            candidate.scan().id(),
            candidate.type(),
            candidate.name(),
            candidate.properties(),
            candidate.replacement(),
            usages.stream().limit(MAX_USAGES).toList(),
            usages.size(),
            inTests,
            updatedCount,
            updated,
            found.truncated(),
            found.projects()
        ), openInCode);
    }

    /**
     * The users, by key, of the places that use the renamed or re-signed
     * node themselves and that use its replacement in the head: those the
     * head updated. None for other changes, whose head the base's usages
     * cannot be told from.
     */
    private Set<String> updatedUsers(
        final RequestContext context,
        final Candidate candidate,
        final Scan head,
        final List<ScanUsages.Usage> usages
    ) {
        if (candidate.replacement() == null || usages.stream().noneMatch(usage -> usage.depth() == 1)) {
            return Set.of();
        }

        final var replacement = scanUsageService.getUsages(context, head.id(), head.id().id() + "|" + candidate.replacement(), 1);
        final var usingReplacement = new HashSet<String>();
        replacement.usages().forEach(usage -> {
            if (usage.user() != null) {
                usingReplacement.add(key(usage.user().id()));
            }
        });

        final var updated = new HashSet<String>();
        usages.stream()
            .filter(usage -> usage.depth() == 1 && usage.user() != null)
            .map(usage -> key(usage.user().id()))
            .filter(usingReplacement::contains)
            .forEach(updated::add);

        return updated;
    }

    private static boolean isUpdated(final ScanUsages.Usage usage, final Set<String> updated) {
        return usage.depth() == 1 && usage.user() != null && updated.contains(key(usage.user().id()));
    }

    static boolean inTest(final ScanUsages.Usage usage) {
        return SourcePaths.isTest(SourcePaths.inProject(usage.user() == null ? null : usage.user().id(), usage.file()));
    }

    private record Http(List<ImpactReport.BrokenCall> brokenCalls, ImpactReport.UnusedEndpoints unused) {
    }

    /**
     * What the change does over HTTP, from matching the calls of the system
     * with the endpoints of the base and of the head: the calls of the
     * latest completed scan of each other project, and of the head itself,
     * that reach an endpoint of the base and none of the head; and the
     * endpoints of the head that no call reaches.
     */
    private Http http(final RequestContext context, final Scan base, final Scan head, final ImpactReport.Endpoints changed) {
        final var systemId = projectService.getProject(context, head.projectId()).systemId();
        final var others = scanService.getLatestCompletedScans(context, systemId).stream()
            .filter(scan -> !scan.projectId().equals(head.projectId()))
            .toList();

        final var callers = new ArrayList<Scan>();
        callers.add(head);
        callers.addAll(others);

        final var calls = httpLinkRepository.findCalls(callers);
        final var baseEndpoints = httpLinkRepository.findEndpoints(List.of(base));
        final var headEndpoints = httpLinkRepository.findEndpoints(List.of(head));
        final var before = HttpLinkMatcher.match(calls, baseEndpoints);
        final var after = HttpLinkMatcher.match(calls, headEndpoints);

        return new Http(
            brokenCalls(callers, before, after),
            unused(base, head, others, baseEndpoints, headEndpoints, after, changed)
        );
    }

    private List<ImpactReport.BrokenCall> brokenCalls(
        final List<Scan> callers,
        final List<HttpLinks.Link> before,
        final List<HttpLinks.Link> after
    ) {
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
     * The endpoints of the head no call reaches: those the base's calls
     * reached, those the head adds or moves, and the rest.
     */
    private ImpactReport.UnusedEndpoints unused(
        final Scan base,
        final Scan head,
        final List<Scan> others,
        final List<HttpLinks.Endpoint> baseEndpoints,
        final List<HttpLinks.Endpoint> headEndpoints,
        final List<HttpLinks.Link> after,
        final ImpactReport.Endpoints changed
    ) {
        final var called = new HashSet<String>();
        after.forEach(link -> called.add(link.endpoint().id()));

        final var uncalled = headEndpoints.stream()
            .filter(endpoint -> !called.contains(endpoint.id()))
            .sorted(Comparator.comparing((HttpLinks.Endpoint endpoint) -> endpoint.url() == null ? "" : endpoint.url())
                .thenComparing(endpoint -> endpoint.httpMethod() == null ? "" : endpoint.httpMethod()))
            .toList();

        if (uncalled.isEmpty()) {
            return ImpactReport.UnusedEndpoints.NONE;
        }

        // What the base's own calls, and the other projects', reached then
        final var baseCallers = new ArrayList<Scan>();
        baseCallers.add(base);
        baseCallers.addAll(others);

        final var calledBefore = new HashSet<String>();
        HttpLinkMatcher.match(httpLinkRepository.findCalls(baseCallers), baseEndpoints)
            .forEach(link -> calledBefore.add(key(link.endpoint().id())));

        final var added = new HashSet<String>();
        changed.added().forEach(endpoint -> added.add(endpoint.key()));
        changed.moved().forEach(move -> added.add(move.to().key()));

        final var noLongerCalled = uncalled.stream().filter(endpoint -> calledBefore.contains(key(endpoint.id()))).toList();
        final var notCalledYet = uncalled.stream().filter(endpoint -> added.contains(key(endpoint.id()))).toList();
        final var rest = uncalled.stream()
            .filter(endpoint -> !calledBefore.contains(key(endpoint.id())) && !added.contains(key(endpoint.id())))
            .toList();
        final var listed = rest.stream().limit(MAX_OTHER_UNUSED_ENDPOINTS).toList();

        final var ids = new HashSet<String>();
        Stream.of(noLongerCalled, notCalledYet, listed).flatMap(List::stream).forEach(endpoint -> ids.add(endpoint.id()));
        final var handlers = httpLinkRepository.findSources(List.of(head), ids);

        return new ImpactReport.UnusedEndpoints(
            withHandlers(noLongerCalled, handlers),
            withHandlers(notCalledYet, handlers),
            withHandlers(listed, handlers),
            rest.size()
        );
    }

    private static List<ImpactReport.UnusedEndpoint> withHandlers(
        final List<HttpLinks.Endpoint> endpoints,
        final Map<String, HttpLinks.Source> handlers
    ) {
        return endpoints.stream().map(endpoint -> new ImpactReport.UnusedEndpoint(endpoint, handlers.get(endpoint.id()))).toList();
    }

    /**
     * A node's id without its scan's, which is the same in every scan of
     * the repository.
     */
    private static String key(final String id) {
        return ImpactReport.key(id);
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
