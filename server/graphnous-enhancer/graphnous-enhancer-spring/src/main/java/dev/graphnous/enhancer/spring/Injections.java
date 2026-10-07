package dev.graphnous.enhancer.spring;

import dev.graphnous.core.model.Annotation;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.TypeRef;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Relationship;
import dev.graphnous.enhancer.spring.SpringEnhancer.BeanMethod;
import dev.graphnous.enhancer.spring.SpringEnhancer.Declared;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What the Spring classes and beans of a scan result inject: an
 * {@code INJECTS} relationship from their node to what a class has
 * {@code @Autowired}, through a field, or the parameters of a constructor or
 * setter, and to what a {@code @Bean} method takes as its parameters. A
 * class with one constructor has it autowired without the annotation, as
 * Spring does; with several, only an {@code @Autowired} one is.
 * <p>
 * Spring classes provide their own type and their supertypes; beans their
 * declared return type and, when the scan result has its class, its
 * supertypes. An injected type is the node of its own Spring class when the
 * scan result has it, or else the one Spring class or bean that provides
 * it; with several, Spring picks one when it runs, so it is the class of
 * the type itself. A {@code List}, {@code Set} or {@code Collection}
 * injects every Spring class and bean that provides its type, and an
 * {@code Optional}, {@code ObjectProvider} or {@code Provider} what it
 * holds. Types that nothing in the scan result provides or declares, such
 * as those of libraries, are left out.
 */
final class Injections {

    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    private static final List<String> HOLDERS = List.of(
        "java.util.Optional",
        "org.springframework.beans.factory.ObjectProvider",
        "jakarta.inject.Provider",
        "javax.inject.Provider"
    );

    private static final List<String> COLLECTIONS = List.of(
        "java.util.List",
        "java.util.Set",
        "java.util.Collection"
    );

    /**
     * What Spring can inject: the node of a Spring class or bean, with the
     * types it provides.
     */
    private record Candidate(String nodeId, Set<String> types) {
    }

    private final List<Declared> classes;
    private final List<BeanMethod> beans;

    /**
     * The nodes of the Spring classes, by the id of their class.
     */
    private final Map<String, String> springNodes;

    /**
     * The classes, by their qualified name.
     */
    private final Map<String, Declared> byName = new LinkedHashMap<>();

    private final List<Candidate> candidates = new ArrayList<>();

    Injections(
        final List<Declared> classes,
        final Map<String, String> springNodes,
        final List<BeanMethod> beans
    ) {
        this.classes = classes;
        this.springNodes = springNodes;
        this.beans = beans;

        classes.forEach(declared -> byName.putIfAbsent(declared.type().getQualifiedName(), declared));

        for (final var declared : classes) {
            final var node = springNodes.get(declared.id());

            if (node != null) {
                candidates.add(new Candidate(node, provided(declared.type().getQualifiedName())));
            }
        }

        for (final var bean : beans) {
            final var type = name(bean.method().getReturnType());

            if (type != null) {
                candidates.add(new Candidate(bean.nodeId(), provided(SpringEnhancer.rawType(type))));
            }
        }
    }

    /**
     * The injections of each Spring class and bean that has any.
     */
    List<Enhancement> enhancements() {
        final var enhancements = new ArrayList<Enhancement>();

        for (final var declared : classes) {
            final var source = springNodes.get(declared.id());

            if (source != null) {
                add(enhancements, source, autowired(declared.type()));
            }
        }

        for (final var bean : beans) {
            add(enhancements, bean.nodeId(), bean.method().getParameters().stream().map(parameter -> name(parameter.getType())).toList());
        }

        return enhancements;
    }

    private void add(
        final List<Enhancement> enhancements,
        final String source,
        final List<String> injectedTypes
    ) {
        final var targets = new LinkedHashSet<String>();

        injectedTypes.stream()
            .filter(Objects::nonNull)
            .forEach(type -> targets.addAll(targets(type)));

        if (!targets.isEmpty()) {
            enhancements.add(new Enhancement(
                List.of(),
                targets.stream().map(target -> new Relationship(source, target, "INJECTS")).toList()
            ));
        }
    }

    /**
     * The declared types the class has autowired, in declaration order:
     * its fields', then those of its constructor's parameters, then those of
     * its setters' parameters.
     */
    private static List<String> autowired(final Class type) {
        final var fields = type.getFields()
            .stream()
            .filter(field -> isAutowired(field.getAnnotations()))
            .map(field -> name(field.getType()));

        final var constructors = type.getMethods()
            .stream()
            .filter(method -> method.getKind() == Method.Kind.CONSTRUCTOR)
            .toList();

        final var autowiredConstructors = constructors.stream()
            .filter(constructor -> isAutowired(constructor.getAnnotations()))
            .toList();

        // The one constructor is autowired without the annotation
        final var injectedConstructors = autowiredConstructors.isEmpty() && constructors.size() == 1
            ? constructors
            : autowiredConstructors;

        final var setters = type.getMethods()
            .stream()
            .filter(method -> method.getKind() != Method.Kind.CONSTRUCTOR)
            .filter(method -> isAutowired(method.getAnnotations()));

        final var parameters = Stream.concat(injectedConstructors.stream(), setters)
            .flatMap(method -> method.getParameters().stream())
            .map(parameter -> name(parameter.getType()));

        return Stream.concat(fields, parameters).toList();
    }

    private static boolean isAutowired(final List<Annotation> annotations) {
        return annotations.stream().anyMatch(annotation -> SpringEnhancer.is(annotation, AUTOWIRED));
    }

    /**
     * What a declared type injects; see the class.
     */
    private List<String> targets(final String type) {
        final var raw = SpringEnhancer.rawType(type);
        final var argument = typeArgument(type);

        if (argument != null && HOLDERS.contains(raw)) {
            return targets(argument);
        }

        if (argument != null && COLLECTIONS.contains(raw)) {
            return providing(SpringEnhancer.rawType(argument));
        }

        return one(raw);
    }

    private List<String> one(final String qualifiedName) {
        final var declared = byName.get(qualifiedName);

        if (declared != null && springNodes.containsKey(declared.id())) {
            return List.of(springNodes.get(declared.id()));
        }

        final var providing = providing(qualifiedName);

        if (providing.size() == 1) {
            return providing;
        }

        return declared == null ? List.of() : List.of(declared.id());
    }

    /**
     * The nodes of the Spring classes and beans that provide the type.
     */
    private List<String> providing(final String qualifiedName) {
        return candidates.stream()
            .filter(candidate -> candidate.types().contains(qualifiedName))
            .map(Candidate::nodeId)
            .distinct()
            .toList();
    }

    /**
     * The type and, when the scan result has its class, the supertypes that
     * class declares.
     */
    private Set<String> provided(final String qualifiedName) {
        final var types = new LinkedHashSet<String>();
        types.add(qualifiedName);

        final var declared = byName.get(qualifiedName);

        if (declared != null) {
            Stream.concat(declared.type().getSuperClasses().stream(), declared.type().getInterfaces().stream())
                .map(supertype -> SpringEnhancer.rawType(supertype.getName()))
                .forEach(types::add);
        }

        return types;
    }

    /**
     * The one type argument of a type, e.g. {@code com.example.Handler} for
     * {@code java.util.List<com.example.Handler>}; null for none or several.
     */
    static String typeArgument(final String type) {
        final var start = type.indexOf('<');
        final var end = type.lastIndexOf('>');

        if (start < 0 || end < start) {
            return null;
        }

        final var argument = type.substring(start + 1, end).trim();
        var depth = 0;

        for (final var character : argument.toCharArray()) {
            if (character == '<') {
                depth++;
            } else if (character == '>') {
                depth--;
            } else if (character == ',' && depth == 0) {
                return null;
            }
        }

        // List<? extends Handler> injects the handlers too
        return argument.replaceFirst("^\\?\\s+extends\\s+", "");
    }

    private static String name(final TypeRef type) {
        return type == null ? null : type.getName();
    }
}
