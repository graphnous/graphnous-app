package dev.graphnous.application.scan.source;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.List;

/**
 * A source file of a repository as the newest scan of its project read it:
 * what someone working on the file finds in the graph.
 */
public final class ScanSource {

    private ScanSource() {
    }

    /**
     * Something a file declares.
     *
     * @param id            its id in the scan's graph, which findUsages and
     *                      the graph take
     * @param type          CLASS, METHOD or FIELD; functions declared outside
     *                      any class are methods, variables fields
     * @param kind          such as CLASS, INTERFACE, METHOD, CONSTRUCTOR or
     *                      FUNCTION; null for a field
     * @param qualifiedName its qualified name, if it has one
     * @param className     the qualified name of the class it is a method or
     *                      field of; null for a class, a function and a
     *                      variable
     */
    public record Declaration(
        String id,
        String type,
        String kind,
        String name,
        String qualifiedName,
        String className
    ) {
    }

    /**
     * A file of a scan, with all it declares, nested classes and the members
     * of its classes included.
     *
     * @param target the path of the scan target the file is in
     * @param module the path of the module the file is in
     * @param path   the path the scan stored, which is within the target,
     *               or within the module for some scanners
     */
    public record File(
        String id,
        String target,
        String module,
        String path,
        String language,
        String sourceSet,
        List<Declaration> declarations
    ) {

        public File {
            declarations = List.copyOf(declarations);
        }
    }

    /**
     * @param path         the path of the file in its repository, as asked
     *                     for
     * @param declarations what the file declares, only those of the symbol
     *                     when one was asked for
     */
    public record Found(
        Project project,
        Scan scan,
        String path,
        File file,
        List<Declaration> declarations
    ) {

        public Found {
            declarations = List.copyOf(declarations);
        }
    }
}
