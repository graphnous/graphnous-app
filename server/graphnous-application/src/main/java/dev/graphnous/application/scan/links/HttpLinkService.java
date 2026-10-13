package dev.graphnous.application.scan.links;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Finds how the projects of a system are linked over HTTP: which HTTP
 * calls of their scans request which endpoints of them. Nothing is stored:
 * the links are found anew from what the enhancers added to the scans.
 */
public class HttpLinkService {

    /**
     * How many scans can be linked at once.
     */
    public static final int MAX_SCANS = 50;

    private static final Logger log = LoggerFactory.getLogger(HttpLinkService.class);

    private final ScanService scanService;
    private final HttpLinkRepository httpLinkRepository;

    public HttpLinkService(
        final ScanService scanService,
        final HttpLinkRepository httpLinkRepository
    ) {
        this.scanService = scanService;
        this.httpLinkRepository = httpLinkRepository;
    }

    /**
     * The links between the scans of a system.
     *
     * @param scanIds the scans to link, such as the ones a graph shows;
     *                the latest completed scan of each project of the
     *                system when null or empty. Getting them checks the
     *                user's permission and organization.
     * @throws ValidationException when there are more scans than
     *                             {@link #MAX_SCANS}
     */
    public HttpLinks.Found getLinks(
        final RequestContext context,
        final System.SystemId systemId,
        final Collection<Scan.ScanId> scanIds
    ) {
        final List<Scan> scans;

        if (scanIds == null || scanIds.isEmpty()) {
            scans = scanService.getLatestCompletedScans(context, systemId);
        } else {
            if (scanIds.size() > MAX_SCANS) {
                throw new ValidationException("At most " + MAX_SCANS + " scans can be linked at once");
            }

            scans = new ArrayList<>();
            scanIds.stream().distinct().forEach(scanId -> scans.add(scanService.getScan(context, scanId)));
        }

        final var calls = httpLinkRepository.findCalls(scans);
        final var endpoints = httpLinkRepository.findEndpoints(scans);
        final var links = HttpLinkMatcher.match(calls, endpoints);

        log.debug(
            "Linked scans organizationId={} systemId={} scans={} calls={} endpoints={} links={}",
            context.organization().id(),
            systemId.id(),
            scans.size(),
            calls.size(),
            endpoints.size(),
            links.size()
        );

        return new HttpLinks.Found(links, calls.size(), endpoints.size());
    }
}
