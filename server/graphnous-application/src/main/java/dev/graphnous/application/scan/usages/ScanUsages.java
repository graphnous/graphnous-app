package dev.graphnous.application.scan.usages;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.List;
import java.util.Map;

/**
 * What uses a node of a scan: what a change to it affects.
 */
public final class ScanUsages {

    private ScanUsages() {
    }

    /**
     * A node of the scan's graph, as much of it as a usage shows.
     *
     * @param id            its id in the scan's graph
     * @param type          Class, Method or Field
     * @param name          its name
     * @param qualifiedName its qualified name, if it has one
     */
    public record Node(
        String id,
        String type,
        String name,
        String qualifiedName
    ) {
    }

    /**
     * One thing a call passes, as written where it is called.
     *
     * @param name   the name it is passed under, such as the attribute of
     *               a rendered component; null when passed by position
     * @param value  the expression passed, as written
     * @param spread whether it is spread, as {@code ...ids} or
     *               {@code {...props}}, so what it passes is not known
     */
    public record Argument(
        String name,
        String value,
        boolean spread
    ) {
    }

    /**
     * An HTTP call of an endpoint, as a usage of the method that serves it.
     *
     * @param callMethod     the method the call requests with, such as GET;
     *                       null when the scan does not say
     * @param callUrl        the url the call requests, as written
     * @param endpointMethod the method of the endpoint, or ANY
     * @param endpointUrl    the path of the endpoint, such as
     *                       {@code /api/orders/{id}}
     */
    public record Request(
        String callMethod,
        String callUrl,
        String endpointMethod,
        String endpointUrl
    ) {
    }

    /**
     * One place that uses what was asked about, or one of its members.
     *
     * @param relationship how it uses it: CALLS, INSTANTIATES, READS,
     *                     WRITES or USES from the body of a method or
     *                     function; EXTENDS or IMPLEMENTS from a class;
     *                     OVERRIDES from a method of a subclass with the
     *                     name of the one asked about; or REQUESTS from a
     *                     method or function that calls, over HTTP, an
     *                     endpoint the target serves
     * @param user         the method, function or class that uses it
     * @param owner        the name of the class the user is a method of;
     *                     null for a function or a class
     * @param file         the path of the file the user is in, if known
     * @param line         the line it uses it on; null when the scan does
     *                     not say, as for a class that extends another
     * @param target       what it uses: what was asked about, or a method
     *                     or field of it; or, through inheritance, the
     *                     method of that name of a class above or below
     *                     it, or a class between it and a subclass
     * @param targetOwner  the name of the class the target is a method or
     *                     field of, which says whose it is when it is not
     *                     the one asked about; null for a class or a
     *                     function
     * @param depth        1 for what uses it itself; 2 for what calls
     *                     that, and so on
     * @param arguments    what a call or an instantiation passes, in the
     *                     order written, empty when it passes nothing;
     *                     null for another relationship, and when the
     *                     scan did not record the arguments of calls
     * @param scanId       the scan the user is in: the one asked about, or
     *                     over HTTP that of another project of its system
     * @param request      for REQUESTS, the call and the endpoint; null
     *                     otherwise
     */
    public record Usage(
        String relationship,
        Node user,
        String owner,
        String file,
        Integer line,
        Node target,
        String targetOwner,
        int depth,
        List<Argument> arguments,
        Scan.ScanId scanId,
        Request request
    ) {

        public Usage {
            arguments = arguments == null ? null : List.copyOf(arguments);
        }
    }

    /**
     * @param subject   what was asked about: for what an enhancer added,
     *                  such as a component, the function or class it is of
     * @param truncated whether there are more usages than it holds
     * @param projects  the project of each scan the usages are in
     */
    public record Found(
        Node subject,
        List<Usage> usages,
        boolean truncated,
        Map<Scan.ScanId, Project.ProjectId> projects
    ) {

        public Found {
            usages = List.copyOf(usages);
            projects = Map.copyOf(projects);
        }

        public Found(
            final Node subject,
            final List<Usage> usages,
            final boolean truncated
        ) {
            this(subject, usages, truncated, Map.of());
        }

        public Found withProjects(final Map<Scan.ScanId, Project.ProjectId> projects) {
            return new Found(subject, usages, truncated, projects);
        }
    }
}
