package dev.graphnous.application.scan.graph;

import java.util.List;
import java.util.Map;

/**
 * What a scan's graph holds, as read back from it.
 */
public final class ScanGraph {

    private ScanGraph() {
    }

    /**
     * The targets of the scan and their modules, with how much each module
     * holds; empty for a scan without results.
     */
    public record Overview(List<Target> targets) {
    }

    public record Target(
        String path,
        String language,
        String languageVersion,
        String buildSystem,
        List<Module> modules
    ) {
    }

    /**
     * @param classes each class of the module once, whether it is listed by
     *                its file, its package or both
     * @param methods the methods of its classes and the functions declared
     *                outside any class
     */
    public record Module(
        String name,
        String path,
        long files,
        long packages,
        long classes,
        long methods,
        long dependencies
    ) {
    }

    /**
     * @param module the path of the module that declares the class
     * @param file   the path of the file that declares the class, if known
     */
    public record ClassSummary(
        String name,
        String qualifiedName,
        String kind,
        String module,
        String file
    ) {
    }

    /**
     * @param superClass the declared superclass, with its type arguments
     * @param interfaces the declared interfaces, with their type arguments
     * @param subtypes   the qualified names of the classes of the scan that
     *                   extend or implement this one
     */
    public record ClassDetails(
        String name,
        String qualifiedName,
        String kind,
        List<String> modifiers,
        List<String> typeParameters,
        String module,
        String file,
        String packageName,
        String superClass,
        List<String> interfaces,
        List<String> subtypes,
        List<Annotation> annotations,
        List<Method> methods,
        List<Field> fields
    ) {
    }

    public record Method(
        String name,
        String kind,
        List<String> modifiers,
        String returnType,
        List<String> parameterNames,
        List<String> parameterTypes,
        List<Annotation> annotations
    ) {
    }

    public record Field(
        String name,
        String type,
        List<String> modifiers,
        List<Annotation> annotations
    ) {
    }

    /**
     * @param arguments the arguments as JSON, if it has any
     * @param parameter the method parameter it is on, if any
     */
    public record Annotation(
        String name,
        String qualifiedName,
        String arguments,
        String parameter
    ) {
    }

    /**
     * A class, method or field with an annotation, or a function or variable
     * declared outside any class.
     *
     * @param kind      CLASS, METHOD, FIELD, FUNCTION or VARIABLE
     * @param className the qualified name of the class, or of the class the
     *                  method or field belongs to; null for a function or
     *                  variable
     * @param member    the name of the method or field, or the qualified
     *                  name of the function or variable; null for a class
     */
    public record AnnotatedElement(
        String kind,
        String className,
        String member,
        Annotation annotation
    ) {
    }

    /**
     * The part of a scan's graph around a node: the nodes within some hops
     * of it, and the relationships between them.
     *
     * @param focus     the id of the node it is around; the scan's own when
     *                  none was asked for
     * @param truncated whether there were more nodes within reach than it
     *                  holds; the nearest are kept
     */
    public record Neighbourhood(
        String focus,
        int depth,
        List<Node> nodes,
        List<Edge> edges,
        boolean truncated
    ) {
    }

    /**
     * @param id         what a focus refers to it by
     * @param type       its label, such as Module, Class or Method
     * @param name       what to call it: a name, qualified name or path
     * @param depth      the hops from the focus
     * @param properties what the scan found about it, as stored
     */
    public record Node(
        String id,
        String type,
        String name,
        int depth,
        Map<String, Object> properties
    ) {
    }

    /**
     * @param type the relationship, such as HAS_METHOD or EXTENDS
     */
    public record Edge(
        String source,
        String target,
        String type,
        Map<String, Object> properties
    ) {
    }

    /**
     * @param module the path of the module that depends on it
     */
    public record Dependency(
        String module,
        String name,
        String version,
        String scope
    ) {
    }
}
