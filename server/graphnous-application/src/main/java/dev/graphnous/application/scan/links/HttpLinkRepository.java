package dev.graphnous.application.scan.links;

import dev.graphnous.domain.scan.Scan;

import java.util.List;
import java.util.Map;

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

    /**
     * The links stored between the scans: of a call of one of them to an
     * endpoint of one of them.
     */
    List<HttpLinks.Link> findStoredLinks(List<Scan> scans);

    /**
     * The version of the matching each of the scans' links were stored
     * with; none for a scan whose links were not stored, or were removed
     * since, as when it is enhanced again.
     */
    Map<Scan.ScanId, String> findLinkedWith(List<Scan> scans);

    /**
     * Replaces the links of the scan, from its calls and to its endpoints,
     * with these, as found with the version of the matching.
     */
    void replaceLinks(Scan.ScanId scanId, List<HttpLinks.Link> links, String matcherVersion);

}
