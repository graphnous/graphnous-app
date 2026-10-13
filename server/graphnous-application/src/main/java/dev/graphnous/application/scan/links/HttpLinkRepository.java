package dev.graphnous.application.scan.links;

import dev.graphnous.domain.scan.Scan;

import java.util.List;

/**
 * Reads the HTTP calls and endpoints enhancers added to the graphs of
 * scans.
 */
public interface HttpLinkRepository {

    /**
     * The HTTP calls of the scans, each with the project of its scan.
     */
    List<HttpLinks.Call> findCalls(List<Scan> scans);

    /**
     * The endpoints of the scans, each with the project of its scan.
     */
    List<HttpLinks.Endpoint> findEndpoints(List<Scan> scans);

}
