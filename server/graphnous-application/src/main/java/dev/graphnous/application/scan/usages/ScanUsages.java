package dev.graphnous.application.scan.usages;

import java.util.List;

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
     * One place that uses what was asked about, or one of its members.
     *
     * @param relationship how it uses it: CALLS, INSTANTIATES, READS,
     *                     WRITES or USES from the body of a method or
     *                     function; EXTENDS or IMPLEMENTS from a class;
     *                     or OVERRIDES from a method of a subclass with
     *                     the name of the one asked about
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
     */
    public record Usage(
        String relationship,
        Node user,
        String owner,
        String file,
        Integer line,
        Node target,
        String targetOwner,
        int depth
    ) {
    }

    /**
     * @param subject   what was asked about: for what an enhancer added,
     *                  such as a component, the function or class it is of
     * @param truncated whether there are more usages than it holds
     */
    public record Found(
        Node subject,
        List<Usage> usages,
        boolean truncated
    ) {

        public Found {
            usages = List.copyOf(usages);
        }
    }
}
