package dev.graphnous.enhancer.spring;

import dev.graphnous.core.model.Annotation;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget.Language;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds the controllers of Spring web applications and their endpoints, and
 * the applications, configurations, services, repositories, components
 * and beans of Spring applications:
 * <pre>
 * (Class)-[:ENHANCE]->(Controller {baseUrl})-[:HAS_ENDPOINT]->(Endpoint {httpMethod, url})
 * (Method)-[:ENHANCE]->(Endpoint)-[:HAS_QUERY_PARAMETER]->(QueryParameter {name, type, defaultValue, optional})
 * (Endpoint)-[:HAS_PATH_VARIABLE]->(PathVariable {name, type, optional})
 * (Endpoint)-[:HAS_REQUEST_BODY]->(RequestBody {name, type, optional})
 * (Method)-[:ENHANCE]->(QueryParameter|PathVariable|RequestBody)
 * (Class)-[:ENHANCE]->(Service|Repository|Component|Application|Configuration)
 * (Method)-[:ENHANCE]->(Bean {name, type})
 * (Controller|Service|...|Bean)-[:INJECTS]->(Controller|Service|...|Bean|Class)
 * </pre>
 * The {@code ENHANCE} relationships come with the nodes' sources.
 * A controller is a class annotated with {@code @Controller} or
 * {@code @RestController}; its {@code @RequestMapping} gives the base url
 * of its endpoints. Every method of it with a request mapping, such as
 * {@code @GetMapping}, is an endpoint for each of its paths and HTTP
 * methods. A {@code @RequestMapping} without methods matches every method,
 * which the endpoint names {@value #ANY_METHOD}.
 * <p>
 * The {@code @RequestParam}, {@code @PathVariable} and {@code @RequestBody}
 * parameters of an endpoint's method are its query parameters, path
 * variables and request body, shared by the method's endpoints. They are
 * optional when they are not required, are an {@code Optional}, or, for a
 * query parameter, have a default value. A query parameter or path variable
 * map without a name takes all of them, so it is none of its own; a request
 * body is named by its parameter.
 * <p>
 * A service is a class annotated with {@code @Service}, a repository one
 * annotated with {@code @Repository}, a component one annotated with
 * {@code @Component}, an application one annotated with
 * {@code @SpringBootApplication}, and a configuration one annotated with
 * {@code @Configuration}. Only these annotations themselves count, not
 * others annotated with them.
 * <p>
 * A bean is a method annotated with {@code @Bean}, named by the
 * annotation or else by the method, of the type the method returns.
 * <p>
 * What these classes have {@code @Autowired}, and what bean methods take,
 * they inject; see {@link Injections}.
 */
public class SpringEnhancer implements Enhancer {

    static final String ANY_METHOD = "ANY";

    private static final String STEREOTYPE = "org.springframework.stereotype.";
    private static final String WEB = "org.springframework.web.bind.annotation.";

    private static final List<String> CONTROLLERS = List.of(
        STEREOTYPE + "Controller",
        WEB + "RestController"
    );

    /**
     * The stereotypes of classes that are a node of their own, by the label
     * of that node.
     */
    private static final List<Map.Entry<String, String>> STEREOTYPES = List.of(
        Map.entry("Service", STEREOTYPE + "Service"),
        Map.entry("Repository", STEREOTYPE + "Repository"),
        Map.entry("Component", STEREOTYPE + "Component"),
        Map.entry("Application", "org.springframework.boot.autoconfigure.SpringBootApplication"),
        Map.entry("Configuration", "org.springframework.context.annotation.Configuration")
    );

    private static final String BEAN = "org.springframework.context.annotation.Bean";

    private static final String REQUEST_MAPPING = WEB + "RequestMapping";

    /**
     * The mappings of one HTTP method; a request mapping names its own.
     */
    private static final Map<String, String> MAPPINGS = Map.of(
        WEB + "GetMapping", "GET",
        WEB + "PostMapping", "POST",
        WEB + "PutMapping", "PUT",
        WEB + "PatchMapping", "PATCH",
        WEB + "DeleteMapping", "DELETE",
        REQUEST_MAPPING, ANY_METHOD
    );

    /**
     * How a method parameter binds to a part of the request, by its
     * annotation: as a node with the label, which the method's endpoints
     * have through the relationship.
     *
     * @param defaults whether the annotation can give a default value
     * @param maps     whether a map without a name takes all of them
     */
    private record Binding(
        String annotation,
        String label,
        String relationship,
        boolean defaults,
        boolean maps
    ) {

        /**
         * What node ids name it, e.g. {@code queryParameter}.
         */
        String idPart() {
            return Character.toLowerCase(label.charAt(0)) + label.substring(1);
        }
    }

    private static final List<Binding> BINDINGS = List.of(
        new Binding(WEB + "RequestParam", "QueryParameter", "HAS_QUERY_PARAMETER", true, true),
        new Binding(WEB + "PathVariable", "PathVariable", "HAS_PATH_VARIABLE", false, true),
        new Binding(WEB + "RequestBody", "RequestBody", "HAS_REQUEST_BODY", false, false)
    );

    /**
     * Parameters of these types take every query parameter or path variable.
     */
    private static final List<String> MAPS = List.of(
        "java.util.Map",
        "org.springframework.util.MultiValueMap"
    );

    static final String NAME = "spring";
    static final String VERSION = "1.0.0";

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
        return Language.JAVA;
    }

    @Override
    public Enhancements enhance(final ScanResult scanResult) {
        final var classes = new ArrayList<Declared>();
        final var targetId = scanResult.getTarget().getPath();

        for (final var module : scanResult.getModules()) {
            final var moduleId = targetId + "|" + module.getPath();

            for (final var file : module.getFiles()) {
                for (final var type : file.getClasses()) {
                    declare(classes, moduleId, type);
                }
            }
        }

        final var enhancements = new ArrayList<Enhancement>();

        // By the id of their class
        final var springNodes = new LinkedHashMap<String, String>();
        final var beans = new ArrayList<BeanMethod>();

        for (final var declared : classes) {
            final var classId = declared.id();
            final var type = declared.type();

            if (type.getAnnotations().stream().anyMatch(annotation -> isAny(annotation, CONTROLLERS))) {
                final var controller = controller(classId, type);

                enhancements.add(controller);
                springNodes.putIfAbsent(classId, controller.nodes().getFirst().id());
            }

            for (final var stereotype : STEREOTYPES) {
                if (type.getAnnotations().stream().anyMatch(annotation -> is(annotation, stereotype.getValue()))) {
                    final var node = stereotype(classId, stereotype.getKey());

                    enhancements.add(node);
                    springNodes.putIfAbsent(classId, node.nodes().getFirst().id());
                }
            }

            for (final var method : type.getMethods()) {
                final var bean = method.getAnnotations().stream().filter(annotation -> is(annotation, BEAN)).findFirst();

                if (bean.isPresent()) {
                    final var node = bean(classId + "|method:" + method.getQualifiedName(), method, bean.get());

                    enhancements.add(new Enhancement(List.of(node), List.of()));
                    beans.add(new BeanMethod(node.id(), method));
                }
            }
        }

        enhancements.addAll(new Injections(classes, springNodes, beans).enhancements());

        return new Enhancements(NAME, VERSION, enhancements);
    }

    /**
     * A class of the scan result, by the id of its node.
     */
    record Declared(String id, Class type) {
    }

    /**
     * A {@code @Bean} method, by the id of its bean's node.
     */
    record BeanMethod(String nodeId, Method method) {
    }

    /**
     * The bean of a {@code @Bean} method, named by the first name the
     * annotation gives or else by the method.
     */
    private static Node bean(
        final String methodId,
        final Method method,
        final Annotation annotation
    ) {
        final var named = argument(annotation, "name") != null ? argument(annotation, "name") : argument(annotation, "value");
        final var name = strings(named).stream().findFirst().orElse(method.getName());

        // A method the scanner found no return type for leaves the type out
        final var metadata = new HashMap<String, Object>();
        metadata.put("name", name);
        metadata.put("type", method.getReturnType() == null ? null : method.getReturnType().getName());

        return new Node(methodId + "|bean", methodId, List.of("Bean"), metadata);
    }

    /**
     * Lists the class and the classes nested in it.
     */
    private static void declare(
        final List<Declared> classes,
        final String moduleId,
        final Class type
    ) {
        classes.add(new Declared(moduleId + "|class:" + type.getQualifiedName(), type));

        type.getClasses().forEach(nested -> declare(classes, moduleId, nested));
    }

    /**
     * The node of a class with a stereotype, e.g. {@code <class>|service}
     * labelled {@code Service}.
     */
    private static Enhancement stereotype(
        final String classId,
        final String label
    ) {
        final var id = classId + "|" + Character.toLowerCase(label.charAt(0)) + label.substring(1);

        return new Enhancement(
            List.of(new Node(id, classId, List.of(label), Map.of())),
            List.of()
        );
    }

    private static Enhancement controller(
        final String classId,
        final Class type
    ) {
        final var controllerId = classId + "|controller";
        final var baseUrl = type.getAnnotations()
            .stream()
            .filter(annotation -> isAny(annotation, List.of(REQUEST_MAPPING)))
            .findFirst()
            .flatMap(annotation -> paths(annotation).stream().findFirst())
            .orElse("");

        final var nodes = new ArrayList<Node>();
        final var relationships = new ArrayList<Relationship>();

        nodes.add(new Node(controllerId, classId, List.of("Controller"), Map.of("baseUrl", baseUrl)));

        for (final var method : type.getMethods()) {
            final var methodId = classId + "|method:" + method.getQualifiedName();

            final var endpoints = endpoints(method, baseUrl);

            if (endpoints.isEmpty()) {
                continue;
            }

            final var parameters = parameters(methodId, method);
            parameters.forEach(parameter -> nodes.add(parameter.node()));

            for (final var endpoint : endpoints) {
                final var endpointId = methodId + "|endpoint:" + endpoint.httpMethod() + " " + endpoint.url();

                nodes.add(new Node(endpointId, methodId, List.of("Endpoint"), Map.of(
                    "httpMethod", endpoint.httpMethod(),
                    "url", endpoint.url()
                )));
                relationships.add(new Relationship(controllerId, endpointId, "HAS_ENDPOINT"));

                for (final var parameter : parameters) {
                    relationships.add(new Relationship(endpointId, parameter.node().id(), parameter.binding().relationship()));
                }
            }
        }

        return new Enhancement(nodes, relationships);
    }

    private record Endpoint(String httpMethod, String url) {
    }

    private record BoundParameter(Binding binding, Node node) {
    }

    /**
     * The query parameters, path variables and request body of the method's
     * parameters, named by their annotation or else by the parameter.
     */
    private static List<BoundParameter> parameters(
        final String methodId,
        final Method method
    ) {
        final var parameters = new ArrayList<BoundParameter>();

        for (final var parameter : method.getParameters()) {
            for (final var binding : BINDINGS) {
                final var bound = parameter.getAnnotations()
                    .stream()
                    .filter(annotation -> is(annotation, binding.annotation()))
                    .findFirst();

                if (bound.isEmpty()) {
                    continue;
                }

                final var annotation = bound.get();
                final var type = parameter.getType() == null ? null : parameter.getType().getName();
                final var named = argument(annotation, "name") != null ? argument(annotation, "name") : argument(annotation, "value");

                if (binding.maps() && named == null && type != null && MAPS.contains(rawType(type))) {
                    continue;
                }

                final var name = named == null ? parameter.getName() : String.valueOf(named);
                final var defaultValue = binding.defaults() ? argument(annotation, "defaultValue") : null;
                final var optional = Boolean.FALSE.equals(argument(annotation, "required"))
                    || defaultValue != null
                    || (type != null && rawType(type).equals("java.util.Optional"));

                // A null value leaves the property out
                final var metadata = new HashMap<String, Object>();
                metadata.put("name", name);
                metadata.put("type", type);
                metadata.put("optional", optional);

                if (binding.defaults()) {
                    metadata.put("defaultValue", defaultValue == null ? null : String.valueOf(defaultValue));
                }

                parameters.add(new BoundParameter(binding, new Node(
                    methodId + "|" + binding.idPart() + ":" + name,
                    methodId,
                    List.of(binding.label()),
                    metadata
                )));
            }
        }

        return parameters;
    }

    /**
     * The type without its type arguments, e.g. {@code java.util.Optional}
     * for {@code java.util.Optional<java.lang.String>}.
     */
    static String rawType(final String type) {
        final var arguments = type.indexOf('<');

        return (arguments < 0 ? type : type.substring(0, arguments)).trim();
    }

    /**
     * The endpoints of the method's request mappings: one for each of their
     * paths and HTTP methods, with no path meaning the base url itself.
     */
    private static List<Endpoint> endpoints(
        final Method method,
        final String baseUrl
    ) {
        final var endpoints = new ArrayList<Endpoint>();

        for (final var annotation : method.getAnnotations()) {
            final var mapping = mapping(annotation);

            if (mapping.isEmpty()) {
                continue;
            }

            final var httpMethods = mapping.get().equals(ANY_METHOD) ? requestMethods(annotation) : List.of(mapping.get());
            final var paths = paths(annotation);

            for (final var httpMethod : httpMethods) {
                for (final var path : paths.isEmpty() ? List.of("") : paths) {
                    endpoints.add(new Endpoint(httpMethod, url(baseUrl, path)));
                }
            }
        }

        return endpoints.stream().distinct().toList();
    }

    /**
     * The HTTP method of a mapping annotation, or {@value #ANY_METHOD} for
     * a request mapping, which names its methods itself.
     */
    private static Optional<String> mapping(final Annotation annotation) {
        return MAPPINGS.entrySet()
            .stream()
            .filter(entry -> is(annotation, entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    /**
     * The methods a request mapping names, e.g. {@code GET} for
     * {@code RequestMethod.GET}; {@value #ANY_METHOD} when it names none.
     */
    private static List<String> requestMethods(final Annotation annotation) {
        final var methods = strings(argument(annotation, "method"))
            .stream()
            .map(method -> method.substring(method.lastIndexOf('.') + 1))
            .toList();

        return methods.isEmpty() ? List.of(ANY_METHOD) : methods;
    }

    /**
     * The paths of a mapping, given as {@code path} or its alias {@code value}.
     */
    private static List<String> paths(final Annotation annotation) {
        final var path = argument(annotation, "path");

        return strings(path != null ? path : argument(annotation, "value"));
    }

    private static Object argument(
        final Annotation annotation,
        final String name
    ) {
        return annotation.getArguments() == null
            ? null
            : annotation.getArguments().getAdditionalProperties().get(name);
    }

    /**
     * An argument of one value or an array of them, as strings.
     */
    private static List<String> strings(final Object argument) {
        if (argument instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }

        return argument == null ? List.of() : List.of(String.valueOf(argument));
    }

    /**
     * The base url and the path joined by one slash, starting with one, e.g.
     * {@code /orders/{id}} for {@code /orders/} and {@code {id}}.
     */
    static String url(
        final String baseUrl,
        final String path
    ) {
        final var url = (trimSlashes(baseUrl) + "/" + trimSlashes(path)).replaceAll("/+$", "");

        return url.startsWith("/") ? url : "/" + url;
    }

    private static String trimSlashes(final String path) {
        return path.replaceAll("^/+|/+$", "");
    }

    private static boolean isAny(
        final Annotation annotation,
        final List<String> qualifiedNames
    ) {
        return qualifiedNames.stream().anyMatch(qualifiedName -> is(annotation, qualifiedName));
    }

    /**
     * Whether the annotation is the one with the qualified name. Without a
     * qualified name, as when the scanner could not resolve it, its simple
     * name has to match.
     */
    static boolean is(
        final Annotation annotation,
        final String qualifiedName
    ) {
        if (annotation.getQualifiedName() != null) {
            return annotation.getQualifiedName().equals(qualifiedName);
        }

        return qualifiedName.endsWith("." + annotation.getName());
    }
}
