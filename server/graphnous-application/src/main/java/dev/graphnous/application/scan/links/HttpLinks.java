package dev.graphnous.application.scan.links;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.List;

/**
 * The HTTP calls of scans, the endpoints they serve, and which call is of
 * which endpoint: how projects are linked over HTTP.
 */
public final class HttpLinks {

    private HttpLinks() {
    }

    /**
     * An HTTP call an enhancer found, such as a call of fetch.
     *
     * @param id         its id in its scan's graph
     * @param httpMethod such as GET; null when the scan does not say
     * @param url        what it requests, as written, with what is only
     *                   known when it runs as variables in braces, such as
     *                   {@code /api/orders/{id}}; null when the scan does
     *                   not say
     * @param client     what makes the call, such as fetch
     */
    public record Call(
        String id,
        Scan.ScanId scanId,
        Project.ProjectId projectId,
        String httpMethod,
        String url,
        String client
    ) {
    }

    /**
     * An endpoint an enhancer found, such as a mapping of a Spring
     * controller or a route of a Next.js application.
     *
     * @param id         its id in its scan's graph
     * @param httpMethod such as GET, or ANY for every method
     * @param url        its path, with its variables in braces, such as
     *                   {@code /api/orders/{id}}
     */
    public record Endpoint(
        String id,
        Scan.ScanId scanId,
        Project.ProjectId projectId,
        String httpMethod,
        String url
    ) {
    }

    /**
     * How sure a link is.
     */
    public enum Match {
        /**
         * The call's whole path is the endpoint's.
         */
        PATH,
        /**
         * The call's path is the end of the endpoint's: the call starts
         * with a base only known when it runs, such as
         * {@code {API_URL}/orders}, which may hold the rest.
         */
        SUFFIX
    }

    /**
     * A call that requests an endpoint, as far as their urls and methods
     * say.
     */
    public record Link(
        Call call,
        Endpoint endpoint,
        Match match
    ) {

        /**
         * Whether the call and the endpoint are of different projects.
         */
        public boolean crossesProjects() {
            return !call.projectId().equals(endpoint.projectId());
        }
    }

    /**
     * The links between the scans, with how much there was to link.
     *
     * @param calls     how many HTTP calls the scans have
     * @param endpoints how many endpoints the scans have
     */
    public record Found(
        List<Link> links,
        int calls,
        int endpoints
    ) {

        public Found {
            links = List.copyOf(links);
        }
    }
}
