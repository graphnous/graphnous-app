package dev.graphnous.application.scan.methods;

import java.util.List;

/**
 * A method, constructor or function a scan found.
 *
 * @param id             the method's id in the scan's graph, which focuses
 *                       the graph on it
 * @param kind           METHOD, CONSTRUCTOR or FUNCTION
 * @param returnType     the declared return type; null for a constructor
 *                       and when the source declares none
 * @param annotations    the qualified names of its annotations, or their
 *                       names when those are unknown
 * @param className      the qualified name of the class it belongs to;
 *                       null for a function declared outside any class
 * @param module         the path of the module that declares it
 * @param file           the path of the file that declares it, if known
 */
public record ScanMethod(
    String id,
    String name,
    String qualifiedName,
    String kind,
    List<String> modifiers,
    String returnType,
    List<String> parameterNames,
    List<String> parameterTypes,
    List<String> annotations,
    String className,
    String module,
    String file
) {
}
