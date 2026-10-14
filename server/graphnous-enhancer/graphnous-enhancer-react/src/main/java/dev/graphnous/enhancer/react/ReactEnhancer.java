package dev.graphnous.enhancer.react;

import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.Field;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget.Language;
import dev.graphnous.core.model.TypeRef;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the components of React applications:
 * <pre>
 * (Method|Class|Field)-[:ENHANCE]->(Component {name, kind})
 * (Component)-[:RENDERS]->(Component)
 * </pre>
 * The {@code ENHANCE} relationship comes with the node's source.
 * <p>
 * Components are looked for in the files that can hold JSX, {@code .tsx}
 * and {@code .jsx}. A function component is a function of the file itself
 * named as React takes components to be, starting with a capital, such as
 * {@code function OrderList()} or {@code const OrderList = () => ...}; its
 * kind is {@code function}. A class component is a class that extends
 * React's {@code Component} or {@code PureComponent}, at any depth of the
 * file; its kind is {@code class}.
 * <p>
 * The scan result does not say what a function returns unless its source
 * does, so a function is taken by its name, not by whether it returns JSX.
 * <p>
 * A component that is not a function itself, such as
 * {@code const OrderList = memo(...)}, is a variable to the scanner. It is
 * found when the variable is named as a component and its source says it
 * is of one of React's component types, such as
 * {@code const OrderList: MemoExoticComponent<Props> = memo(...)}: its kind
 * is {@code memo}, {@code forwardRef} or {@code lazy} for those types, and
 * {@code function} for the others, such as {@code FC}. Without a type in
 * its source it is not found, as the scan result does not say what it is.
 * <p>
 * A component renders the components it uses as JSX, such as
 * {@code <OrderRow />}, which the scanner reports as calls: of the function,
 * or of the constructor of the class. A function component renders what
 * its body calls, a class component what any of its methods call. Calling
 * a function component as a function reads the same in the scan result,
 * so it counts as rendering it too. A component that is a variable has no
 * calls in the scan result, to it or from it, so it renders nothing and
 * nothing renders it.
 */
public class ReactEnhancer implements Enhancer {

    static final String NAME = "react";
    static final String VERSION = "1.0.0";

    private static final Pattern JSX_FILE = Pattern.compile(".*\\.(tsx|jsx)");

    /**
     * The classes of React a class component extends.
     */
    private static final Set<String> COMPONENT_CLASSES = Set.of("Component", "PureComponent");

    /**
     * The types of React a variable that is a component is of, with the
     * kind of component each makes it.
     */
    private static final Map<String, String> COMPONENT_TYPES = Map.ofEntries(
        Map.entry("FC", "function"),
        Map.entry("FunctionComponent", "function"),
        Map.entry("VFC", "function"),
        Map.entry("VoidFunctionComponent", "function"),
        Map.entry("ComponentType", "function"),
        Map.entry("ExoticComponent", "function"),
        Map.entry("NamedExoticComponent", "function"),
        Map.entry("MemoExoticComponent", "memo"),
        Map.entry("ForwardRefExoticComponent", "forwardRef"),
        Map.entry("LazyExoticComponent", "lazy")
    );

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Language forLanguage() {
        return Language.TYPESCRIPT;
    }

    @Override
    public Enhancements enhance(final ScanResult scanResult) {
        final var targetId = scanResult.getTarget().getPath();

        // The components of each file, in the order of the scan result
        final var files = new ArrayList<List<Found>>();

        for (final var module : scanResult.getModules()) {
            final var moduleId = targetId + "|" + module.getPath();

            for (final var file : module.getFiles()) {
                if (!isJsxFile(file.getPath())) {
                    continue;
                }

                final var components = components(moduleId, file);

                if (!components.isEmpty()) {
                    files.add(components);
                }
            }
        }

        // By what a call to them targets: within their module, and in any
        // module, where the first found of a name wins
        final var rendered = new HashMap<String, Found>();

        files.forEach(components -> components.stream().filter(component -> component.target() != null).forEach(component -> {
            rendered.putIfAbsent(component.moduleId() + "|" + component.target(), component);
            rendered.putIfAbsent(component.target(), component);
        }));

        final var enhancements = files.stream()
            .map(components -> new Enhancement(
                components.stream().map(Found::node).toList(),
                renders(components, rendered)
            ))
            .toList();

        return new Enhancements(NAME, VERSION, enhancements);
    }

    /**
     * A component found in the scan result.
     *
     * @param moduleId the id of its module
     * @param target   what a call that renders it targets: the qualified
     *                 name of the function, or of the class's constructor;
     *                 null when nothing in the scan result does
     * @param calls    the calls of its body
     */
    private record Found(
        Node node,
        String moduleId,
        String target,
        List<Call> calls
    ) {
    }

    /**
     * The components of the file: its function components, then those
     * that are variables, then its class components.
     */
    private static List<Found> components(
        final String moduleId,
        final File file
    ) {
        final var fileId = moduleId + "|file:" + file.getPath();
        final var components = new ArrayList<Found>();

        for (final var function : file.getFunctions()) {
            if (isFunctionComponent(function)) {
                components.add(new Found(
                    component(fileId + "|function:" + function.getQualifiedName(), function.getName(), "function"),
                    moduleId,
                    function.getQualifiedName(),
                    function.getCalls()
                ));
            }
        }

        for (final var variable : file.getVariables()) {
            variableComponentKind(variable).ifPresent(kind -> components.add(new Found(
                component(fileId + "|variable:" + variable.getName(), variable.getName(), kind),
                moduleId,
                // Rendering a variable is not a call in the scan result
                null,
                List.of()
            )));
        }

        for (final var type : file.getClasses()) {
            addClassComponents(moduleId, type, components);
        }

        return components;
    }

    private static void addClassComponents(
        final String moduleId,
        final Class type,
        final List<Found> components
    ) {
        if (isClassComponent(type)) {
            components.add(new Found(
                component(moduleId + "|class:" + type.getQualifiedName(), type.getName(), "class"),
                moduleId,
                type.getQualifiedName() + ".constructor",
                type.getMethods().stream().flatMap(method -> method.getCalls().stream()).toList()
            ));
        }

        type.getClasses().forEach(nested -> addClassComponents(moduleId, nested, components));
    }

    private static Node component(
        final String sourceId,
        final String name,
        final String kind
    ) {
        return new Node(sourceId + "|component", sourceId, List.of("Component"), Map.of("name", name, "kind", kind));
    }

    /**
     * A RENDERS relationship from each of the components to each component
     * it calls, once, in the order of the calls.
     */
    private static List<Relationship> renders(
        final List<Found> components,
        final Map<String, Found> rendered
    ) {
        final var relationships = new LinkedHashSet<Relationship>();

        for (final var component : components) {
            for (final var call : component.calls()) {
                if (Boolean.FALSE.equals(call.getResolved())) {
                    continue;
                }

                // The component of that name in its own module, before one
                // of another module
                final var target = rendered.getOrDefault(
                    component.moduleId() + "|" + call.getTarget(),
                    rendered.get(call.getTarget())
                );

                if (target != null) {
                    relationships.add(new Relationship(component.node().id(), target.node().id(), "RENDERS"));
                }
            }
        }

        return List.copyOf(relationships);
    }

    /**
     * Whether the file can hold JSX.
     */
    static boolean isJsxFile(final String path) {
        return path != null && JSX_FILE.matcher(path).matches();
    }

    /**
     * Whether the function is named as a component: React renders only
     * what starts with a capital as one.
     */
    static boolean isFunctionComponent(final Method function) {
        final var name = function.getName();

        return function.getKind() == Method.Kind.FUNCTION
            && name != null
            && !name.isEmpty()
            && Character.isUpperCase(name.charAt(0));
    }

    /**
     * The kind of component the variable is, when it is named as one and
     * its source says it is of one of React's component types, written by
     * itself or as of React, such as {@code React.FC<Props>}.
     */
    static Optional<String> variableComponentKind(final Field variable) {
        final var name = variable.getName();
        final var type = variable.getType() == null ? null : variable.getType().getName();

        if (name == null || name.isEmpty() || !Character.isUpperCase(name.charAt(0)) || type == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(COMPONENT_TYPES.get(rawType(type)));
    }

    /**
     * Whether the class extends React's Component or PureComponent, written
     * by itself or as of React, such as {@code React.Component<Props>}.
     */
    static boolean isClassComponent(final Class type) {
        return type.getSuperClasses()
            .stream()
            .map(TypeRef::getName)
            .anyMatch(name -> name != null && COMPONENT_CLASSES.contains(rawType(name)));
    }

    /**
     * The name of the type without its type arguments and what it is of:
     * {@code Component} for {@code React.Component<Props>}.
     */
    private static String rawType(final String name) {
        final var generics = name.indexOf('<');
        final var raw = (generics < 0 ? name : name.substring(0, generics)).trim();

        return raw.substring(raw.lastIndexOf('.') + 1);
    }
}
