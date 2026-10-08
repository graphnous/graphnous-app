package dev.graphnous.application.scan.classes;

import java.util.List;

/**
 * A class, interface, enum, record, struct, trait or type alias a scan
 * found.
 *
 * @param id          the class's id in the scan's graph, which focuses the
 *                    graph on it
 * @param module      the path of the module that declares it
 * @param file        the path of the file that declares it, if known
 * @param packageName the qualified name of its package, if it has one
 * @param superClass  the declared superclass, with its type arguments
 * @param interfaces  the declared interfaces, with their type arguments
 * @param annotations the qualified names of its annotations, or their
 *                    names when those are unknown
 * @param methods     how many methods it declares
 * @param fields      how many fields it declares
 */
public record ScanClass(
    String id,
    String name,
    String qualifiedName,
    String kind,
    List<String> modifiers,
    String module,
    String file,
    String packageName,
    String superClass,
    List<String> interfaces,
    List<String> annotations,
    long methods,
    long fields
) {
}
