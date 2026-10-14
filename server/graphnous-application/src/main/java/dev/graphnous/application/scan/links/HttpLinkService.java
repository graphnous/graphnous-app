package dev.graphnous.application.scan.links;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds how the projects of a system are linked over HTTP: which HTTP
 * calls of their scans request which endpoints of them.
 * <p>
 * The links between the latest completed scans of a system are stored in
 * the graph, as {@code (HttpCall)-[:REQUESTS]->(Endpoint)}, so they can be
 * followed like any other relationship. A scan's links are found again
 * when it completes or is enhanced again, against the latest completed
 * scan of each other project of its system; and when they are read and
 * were not stored, or were stored by an earlier version of the matching.
 * The links of other scans, such as earlier ones chosen to compare, are
 * found anew whenever they are asked for.
 */
public class HttpLinkService {

    /**
     * How many scans can be linked at once.
     */
    public static final int MAX_SCANS = 50;

    private static final Logger log = LoggerFactory.getLogger(HttpLinkService.class);

    private final ScanService scanService;
    private final ScanRepository scanRepository;
    private final ProjectRepository projectRepository;
    private final HttpLinkRepository httpLinkRepository;

    public HttpLinkService(
        final ScanService scanService,
        final ScanRepository scanRepository,
        final ProjectRepository projectRepository,
        final HttpLinkRepository httpLinkRepository
    ) {
        this.scanService = scanService;
        this.scanRepository = scanRepository;
        this.projectRepository = projectRepository;
        this.httpLinkRepository = httpLinkRepository;
    }

    /**
     * The links between the scans of a system.
     *
     * @param scanIds the scans to link, such as the ones a graph shows;
     *                the latest completed scan of each project of the
     *                system when null or empty. The links of those are
     *                stored, also when they are the scans asked for.
     *                Getting them checks the user's permission and
     *                organization.
     * @throws ValidationException when there are more scans than
     *                             {@link #MAX_SCANS}
     */
    public HttpLinks.Found getLinks(
        final RequestContext context,
        final System.SystemId systemId,
        final Collection<Scan.ScanId> scanIds
    ) {
        final boolean latest;
        final List<Scan> scans;

        if (scanIds == null || scanIds.isEmpty()) {
            latest = true;
            scans = scanService.getLatestCompletedScans(context, systemId);
        } else {
            if (scanIds.size() > MAX_SCANS) {
                throw new ValidationException("At most " + MAX_SCANS + " scans can be linked at once");
            }

            scans = new ArrayList<>();
            scanIds.stream().distinct().forEach(scanId -> scans.add(scanService.getScan(context, scanId)));

            // The scans a graph shows by default are the latest ones, whose links are stored
            latest = ids(scans).equals(ids(scanService.getLatestCompletedScans(context, systemId)));
        }

        final var calls = httpLinkRepository.findCalls(scans);
        final var endpoints = httpLinkRepository.findEndpoints(scans);
        final List<HttpLinks.Link> links;

        if (latest) {
            // Stored, once those not stored with this matching are found again
            final var linkedWith = httpLinkRepository.findLinkedWith(scans);

            for (final var scan : scans) {
                if (!HttpLinkMatcher.VERSION.equals(linkedWith.get(scan.id()))) {
                    store(scan, scans, calls, endpoints);
                }
            }

            links = httpLinkRepository.findStoredLinks(scans);
        } else {
            links = HttpLinkMatcher.match(calls, endpoints);
        }

        log.debug(
            "Linked scans organizationId={} systemId={} scans={} calls={} endpoints={} links={} stored={}",
            context.organization().id(),
            systemId.id(),
            scans.size(),
            calls.size(),
            endpoints.size(),
            links.size(),
            latest
        );

        return new HttpLinks.Found(links, calls.size(), endpoints.size());
    }

    private static Set<Scan.ScanId> ids(final List<Scan> scans) {
        return scans.stream().map(Scan::id).collect(Collectors.toSet());
    }

    /**
     * Finds and stores the links of the scan again, from its calls and to
     * its endpoints, against the latest completed scan of each other
     * project of its system: as it completed, or was enhanced again. Not
     * for the user: it runs in the background, for any organization.
     * Nothing for a scan that is not completed.
     */
    public void refresh(final Scan.ScanId scanId) {
        final Scan scan;

        try {
            scan = scanRepository.findById(scanId);
        } catch (final NotFoundException e) {
            // Deleted since
            return;
        }

        if (scan.status() != Scan.ScanStatus.COMPLETED) {
            return;
        }

        final var systemId = projectRepository.findById(scan.projectId()).systemId();

        // This scan for its project, whether or not it is the latest
        final var scans = new ArrayList<Scan>();
        scans.add(scan);
        scanRepository.findLatestCompletedInSystem(systemId).stream()
            .filter(other -> !other.projectId().equals(scan.projectId()))
            .forEach(scans::add);

        store(scan, scans, httpLinkRepository.findCalls(scans), httpLinkRepository.findEndpoints(scans));
    }

    /**
     * Stores the links of the scan among the scans: the ones from its
     * calls, and the ones to its endpoints.
     */
    private void store(
        final Scan scan,
        final List<Scan> scans,
        final List<HttpLinks.Call> calls,
        final List<HttpLinks.Endpoint> endpoints
    ) {
        final var links = HttpLinkMatcher.match(calls, endpoints).stream()
            .filter(link -> link.call().scanId().equals(scan.id()) || link.endpoint().scanId().equals(scan.id()))
            .toList();

        httpLinkRepository.replaceLinks(scan.id(), links, HttpLinkMatcher.VERSION);

        log.info(
            "Stored HTTP links scanId={} projectId={} scans={} links={} matcher={}",
            scan.id().id(),
            scan.projectId().id(),
            scans.size(),
            links.size(),
            HttpLinkMatcher.VERSION
        );
    }
}
