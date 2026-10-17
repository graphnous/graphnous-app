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

    /**
     * Where an enhancer found a call or an endpoint: the method or function
     * that makes the call or serves the endpoint, or the class.
     *
     * @param id        the method's, function's or class's id in its
     *                  scan's graph, which findUsages takes
     * @param type      Method or Class
     * @param className the qualified name of the class the method is of;
     *                  null for a function or a class
     * @param file      the path of its file, as the scan stored it, if known
     * @param line      the line of the call, if the scan says; null for an
     *                  endpoint
     */
    public record Source(
        String id,
        String type,
        String name,
        String qualifiedName,
        String className,
        String file,
        Integer line
    ) {
    }

    /**
     * A call, of an endpoint or of none, with where it is.
     *
     * @param caller where the call is; null when the scan does not say
     * @param match  how sure its link to the endpoint is; null for a call
     *               of no endpoint
     */
    public record CallSite(
        Call call,
        Source caller,
        Match match
    ) {
    }

    /**
     * An endpoint, with what serves it and the calls that request it.
     *
     * @param handler what serves it; null when the scan does not say
     */
    public record EndpointCalls(
        Endpoint endpoint,
        Source handler,
        List<CallSite> calls
    ) {

        public EndpointCalls {
            calls = List.copyOf(calls);
        }
    }

    /**
     * Which of a system's endpoints to list; a null filter does not filter.
     *
     * @param search    part of the url, ignoring case
     * @param projectId the project that serves them
     * @param uncalled  true for only those no call of the system requests
     */
    public record EndpointFilter(
        String search,
        Project.ProjectId projectId,
        boolean uncalled
    ) {

        public EndpointFilter {
            search = search == null || search.isBlank() ? null : search.trim();
        }

        public static EndpointFilter none() {
            return new EndpointFilter(null, null, false);
        }
    }

    /**
     * Which of a system's calls of no endpoint to list; a null filter does
     * not filter.
     *
     * @param search    part of the url, ignoring case
     * @param projectId the project that makes them
     */
    public record CallFilter(
        String search,
        Project.ProjectId projectId
    ) {

        public CallFilter {
            search = search == null || search.isBlank() ? null : search.trim();
        }

        public static CallFilter none() {
            return new CallFilter(null, null);
        }
    }

    /**
     * @param total     how many calls pass the filter
     * @param truncated whether there are more of those than it lists
     */
    public record Calls(
        List<CallSite> calls,
        int total,
        boolean truncated
    ) {

        public Calls {
            calls = List.copyOf(calls);
        }
    }

    /**
     * @param total     how many endpoints pass the filter
     * @param truncated whether there are more of those than it lists
     */
    public record Endpoints(
        List<EndpointCalls> endpoints,
        int total,
        boolean truncated
    ) {

        public Endpoints {
            endpoints = List.copyOf(endpoints);
        }
    }
}
