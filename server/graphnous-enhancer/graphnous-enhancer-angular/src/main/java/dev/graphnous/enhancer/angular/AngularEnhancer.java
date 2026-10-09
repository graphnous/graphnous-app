package dev.graphnous.enhancer.angular;

import dev.graphnous.core.model.Annotation;
import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Import;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds the components, directives, services and modules of Angular
 * applications, and the HTTP calls they make with Angular's
 * {@code HttpClient}:
 * <pre>
 * (Class)-[:ENHANCE]->(Component {selector, templateUrl, standalone})
 * (Class)-[:ENHANCE]->(Directive {selector, standalone})
 * (Class)-[:ENHANCE]->(Service {providedIn})
 * (Class)-[:ENHANCE]->(NgModule)
 * (Method)-[:ENHANCE]->(HttpCall {httpMethod, client, line})
 * (Component|Directive|Service)-[:HAS_HTTP_CALL]->(HttpCall)
 * (Component|Directive|Service)-[:INJECTS]->(Component|Directive|Service|Class)
 * (Component|Directive)-[:IMPORTS]->(Component|Directive|NgModule)
 * (NgModule)-[:DECLARES]->(Component|Directive)
 * (NgModule)-[:IMPORTS|EXPORTS]->(Component|Directive|NgModule)
 * (NgModule)-[:PROVIDES]->(Service)
 * (NgModule)-[:BOOTSTRAPS]->(Component)
 * </pre>
 * The {@code ENHANCE} relationships come with the nodes' sources. Spring
 * applications have components and services too; the enhancer that added a
 * node tells them apart, as in
 * {@code (:Enhancement {name: 'angular'})-[:ADDED]->(:Component)}. An
 * Angular module is an {@code NgModule}, as {@code Module} is the label of
 * the modules of the scan.
 * <p>
 * A component is a class decorated with {@code @Component}, a directive one
 * decorated with {@code @Directive}, a service one decorated with
 * {@code @Injectable}, and a module one decorated with {@code @NgModule},
 * in a file that imports {@code @angular/core}; a
 * decorator of the same name declared in the project is not Angular's. The
 * decorator's settings give the node's properties.
 * <p>
 * An HTTP call is a call of a method of {@code HttpClient}, such as
 * {@code this.http.get(...)}, on a field or parameter whose declared type
 * is {@code HttpClient}, in a file that imports {@code @angular/common/http}.
 * Its HTTP method is the method called, or {@code REQUEST} and
 * {@code JSONP} for {@code request} and {@code jsonp}, which take it as an
 * argument or are always GET. Scan results do not keep the arguments of
 * calls, so the url is not known.
 * <p>
 * What a component, directive or service takes in its constructor, of the
 * type of a class of the scan, it injects: the Angular node of that class
 * when it has one, else the class. A field's type does not say it was
 * injected, as it may hold any state. What a
 * standalone component or directive lists in its {@code imports}, it
 * imports, and what a module lists in its {@code declarations},
 * {@code imports}, {@code exports}, {@code providers} and
 * {@code bootstrap}, it declares, imports, exports, provides and
 * bootstraps, when one Angular class of the scan has that name. Of a
 * provider such as {@code {provide: A, useClass: B}}, that is the class it
 * uses, else its token; of {@code RouterModule.forRoot(routes)}, the
 * module. Angular's own modules, such as {@code BrowserModule}, are not
 * classes of the scan, so are left out.
 * <p>
 * What is injected with {@code inject(...)} is left out, as scan results
 * keep neither the calls in field initializers nor the types of fields that
 * do not declare one; so is a call on such a field.
 */
public class AngularEnhancer implements Enhancer {

    static final String NAME = "angular";
    static final String VERSION = "1.0.0";

    private static final String CORE = "@angular/core";
    private static final String HTTP = "@angular/common/http";
    private static final String HTTP_CLIENT = "HttpClient";

    /**
     * The HTTP method of each method of {@code HttpClient}.
     */
    private static final Map<String, String> HTTP_METHODS = Map.of(
        "get", "GET",
        "post", "POST",
        "put", "PUT",
        "patch", "PATCH",
        "delete", "DELETE",
        "head", "HEAD",
        "options", "OPTIONS",
        "request", "REQUEST",
        "jsonp", "JSONP"
    );

    /**
     * A call as the scanner writes an unresolved one: the receiver, then the
     * method, e.g. {@code this.http.get} or {@code http.post}.
     */
    private static final Pattern RECEIVER_CALL = Pattern.compile("(this\\.)?([A-Za-z_$][\\w$]*)\\.([A-Za-z_$][\\w$]*)");

    /**
     * What a decorator of Angular's makes of a class.
     *
     * @param label      the label of its node
     * @param decorator  the decorator's name
     * @param idPart     what node ids name it
     * @param properties the decorator's settings the node keeps
     */
    private record Kind(String label, String decorator, String idPart, List<String> properties) {
    }

    private static final List<Kind> KINDS = List.of(
        new Kind("Component", "Component", "component", List.of("selector", "templateUrl", "standalone")),
        new Kind("Directive", "Directive", "directive", List.of("selector", "standalone")),
        new Kind("Service", "Injectable", "service", List.of("providedIn")),
        new Kind("NgModule", "NgModule", "ngModule", List.of())
    );

    /**
     * The relationship each setting of a decorator makes, by the decorator:
     * from its class to each Angular class the setting lists.
     */
    private static final Map<String, Map<String, String>> LISTS = Map.of(
        "Component", Map.of("imports", "IMPORTS"),
        "Directive", Map.of("imports", "IMPORTS"),
        "NgModule", Map.of(
            "declarations", "DECLARES",
            "imports", "IMPORTS",
            "exports", "EXPORTS",
            "providers", "PROVIDES",
            "bootstrap", "BOOTSTRAPS"
        )
    );

    /**
     * The name an entry of a list starts with, e.g. {@code RouterModule} for
     * {@code RouterModule.forRoot(routes)}.
     */
    private static final Pattern LEADING_NAME = Pattern.compile("^[A-Za-z_$][\\w$]*");

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

    /**
     * A class of the scan result, by the id of its node, with what its file
     * imports.
     */
    private record Declared(String id, String moduleId, Class type, Set<String> imports) {

        boolean imports(final String module) {
            return imports.contains(module);
        }
    }

    @Override
    public Enhancements enhance(final ScanResult scanResult) {
        final var classes = new ArrayList<Declared>();
        final var targetId = scanResult.getTarget().getPath();

        for (final var module : scanResult.getModules()) {
            final var moduleId = targetId + "|" + module.getPath();

            for (final var file : module.getFiles()) {
                final var imports = imports(file);

                for (final var type : file.getClasses()) {
                    declare(classes, moduleId, type, imports);
                }
            }
        }

        final var enhancements = new ArrayList<Enhancement>();

        // The node of each Angular class, by the id of the class
        final var angularNodes = new LinkedHashMap<String, String>();

        for (final var declared : classes) {
            if (!declared.imports(CORE)) {
                continue;
            }

            for (final var kind : KINDS) {
                decorator(declared.type(), kind.decorator()).ifPresent(decorator -> {
                    final var node = angularNode(declared.id(), kind, decorator);

                    enhancements.add(new Enhancement(List.of(node), List.of()));
                    angularNodes.putIfAbsent(declared.id(), node.id());
                });
            }
        }

        for (final var declared : classes) {
            if (declared.imports(HTTP)) {
                httpCalls(declared, angularNodes.get(declared.id())).ifPresent(enhancements::add);
            }
        }

        final var relationships = new ArrayList<Relationship>();
        relationships.addAll(injections(classes, angularNodes));
        relationships.addAll(listed(classes, angularNodes));

        if (!relationships.isEmpty()) {
            enhancements.add(new Enhancement(List.of(), relationships));
        }

        return new Enhancements(NAME, VERSION, enhancements);
    }

    /**
     * The modules the file imports, such as {@code @angular/core}.
     */
    private static Set<String> imports(final File file) {
        final var imports = new LinkedHashSet<String>();

        for (final Import imported : file.getImports()) {
            imports.add(imported.getName());
        }

        return imports;
    }

    /**
     * Lists the class and the classes nested in it.
     */
    private static void declare(
        final List<Declared> classes,
        final String moduleId,
        final Class type,
        final Set<String> imports
    ) {
        classes.add(new Declared(moduleId + "|class:" + type.getQualifiedName(), moduleId, type, imports));

        type.getClasses().forEach(nested -> declare(classes, moduleId, nested, imports));
    }

    /**
     * The decorator of Angular's with the name, if the class has it: one the
     * scanner could not trace to a declaration of the project, as Angular's
     * are in its dependencies.
     */
    private static Optional<Annotation> decorator(final Class type, final String name) {
        return type.getAnnotations()
            .stream()
            .filter(annotation -> annotation.getName().equals(name) && annotation.getQualifiedName() == null)
            .findFirst();
    }

    /**
     * The node of an Angular class, e.g. {@code <class>|component}, with the
     * decorator's settings it keeps.
     */
    private static Node angularNode(
        final String classId,
        final Kind kind,
        final Annotation decorator
    ) {
        final var settings = settings(decorator);

        // A setting that is not there leaves the property out
        final var metadata = new HashMap<String, Object>();

        for (final var property : kind.properties()) {
            metadata.put(property, value(settings.get(property)));
        }

        return new Node(classId + "|" + kind.idPart(), classId, List.of(kind.label()), metadata);
    }

    /**
     * The settings a decorator is given, as in {@code @Component({selector: 'app-root'})}.
     */
    private static Map<?, ?> settings(final Annotation decorator) {
        if (decorator.getArguments() == null) {
            return Map.of();
        }

        return decorator.getArguments().getAdditionalProperties().get("value") instanceof Map<?, ?> settings
            ? settings
            : Map.of();
    }

    /**
     * A setting as a property: a string, number or boolean as it is, and
     * anything else, such as an expression, as the scanner wrote it.
     */
    private static Object value(final Object setting) {
        if (setting == null || setting instanceof String || setting instanceof Boolean || setting instanceof Number) {
            return setting;
        }

        return String.valueOf(setting);
    }

    /**
     * The HTTP calls of the class's methods, through its fields and the
     * methods' parameters of type {@code HttpClient}.
     *
     * @param angularNodeId the node of the class, which has the calls; null
     *                      for a class that is not an Angular one
     */
    private static Optional<Enhancement> httpCalls(
        final Declared declared,
        final String angularNodeId
    ) {
        final var clientFields = new LinkedHashSet<String>();

        declared.type().getFields().stream()
            .filter(field -> isHttpClient(field.getType()))
            .forEach(field -> clientFields.add(field.getName()));

        final var nodes = new ArrayList<Node>();
        final var relationships = new ArrayList<Relationship>();

        for (final var method : declared.type().getMethods()) {
            final var methodId = declared.id() + "|method:" + method.getQualifiedName();
            final var clientParameters = method.getParameters()
                .stream()
                .filter(parameter -> isHttpClient(parameter.getType()))
                .map(parameter -> parameter.getName())
                .toList();

            var index = 0;

            for (final var call : method.getCalls()) {
                final var httpCall = httpCall(call, clientFields, clientParameters);

                if (httpCall.isEmpty()) {
                    continue;
                }

                final var callId = methodId + "|httpCall:" + index++;

                final var metadata = new HashMap<String, Object>();
                metadata.put("httpMethod", httpCall.get().httpMethod());
                metadata.put("client", httpCall.get().client());
                metadata.put("line", call.getStartLine());

                nodes.add(new Node(callId, methodId, List.of("HttpCall"), metadata));

                if (angularNodeId != null) {
                    relationships.add(new Relationship(angularNodeId, callId, "HAS_HTTP_CALL"));
                }
            }
        }

        return nodes.isEmpty() ? Optional.empty() : Optional.of(new Enhancement(nodes, relationships));
    }

    /**
     * @param client what the call is made on, as written, e.g. {@code this.http}
     */
    private record HttpCall(String httpMethod, String client) {
    }

    /**
     * The call as an HTTP call, if it calls a method of {@code HttpClient}
     * on one of the clients. The scanner leaves such calls unresolved, as
     * {@code HttpClient} is not part of the project.
     */
    static Optional<HttpCall> httpCall(
        final Call call,
        final Set<String> clientFields,
        final List<String> clientParameters
    ) {
        if (Boolean.TRUE.equals(call.getResolved()) || call.getKind() == Call.Kind.CONSTRUCTOR) {
            return Optional.empty();
        }

        final var matcher = RECEIVER_CALL.matcher(call.getTarget());

        if (!matcher.matches()) {
            return Optional.empty();
        }

        final var onThis = matcher.group(1) != null;
        final var receiver = matcher.group(2);
        final var httpMethod = HTTP_METHODS.get(matcher.group(3));

        final var isClient = onThis ? clientFields.contains(receiver) : clientParameters.contains(receiver);

        if (httpMethod == null || !isClient) {
            return Optional.empty();
        }

        return Optional.of(new HttpCall(httpMethod, (onThis ? "this." : "") + receiver));
    }

    /**
     * Whether the type is Angular's {@code HttpClient}, as written.
     */
    private static boolean isHttpClient(final TypeRef type) {
        return type != null && rawType(type.getName()).equals(HTTP_CLIENT);
    }

    /**
     * What the Angular classes take in their constructors, of the type of a
     * class of the scan.
     */
    private static List<Relationship> injections(
        final List<Declared> classes,
        final Map<String, String> angularNodes
    ) {
        final var byQualifiedName = new HashMap<String, List<Declared>>();
        classes.forEach(declared -> byQualifiedName
            .computeIfAbsent(declared.type().getQualifiedName(), key -> new ArrayList<>())
            .add(declared));

        final var relationships = new LinkedHashSet<Relationship>();

        for (final var declared : classes) {
            final var nodeId = angularNodes.get(declared.id());

            if (nodeId == null) {
                continue;
            }

            final var types = new ArrayList<TypeRef>();

            declared.type().getMethods().stream()
                .filter(method -> method.getKind() == Method.Kind.CONSTRUCTOR)
                .forEach(constructor -> constructor.getParameters().forEach(parameter -> types.add(parameter.getType())));

            for (final var type : types) {
                if (type == null) {
                    continue;
                }

                for (final var reference : type.getReferences()) {
                    injected(byQualifiedName.getOrDefault(reference, List.of()), declared.moduleId())
                        .filter(injected -> !injected.id().equals(declared.id()))
                        .ifPresent(injected -> relationships.add(new Relationship(
                            nodeId,
                            angularNodes.getOrDefault(injected.id(), injected.id()),
                            "INJECTS"
                        )));
                }
            }
        }

        return List.copyOf(relationships);
    }

    /**
     * The class injected, of those with its qualified name: the one of the
     * same module, else the first.
     */
    private static Optional<Declared> injected(final List<Declared> candidates, final String moduleId) {
        return candidates.stream()
            .filter(candidate -> candidate.moduleId().equals(moduleId))
            .findFirst()
            .or(() -> candidates.stream().findFirst());
    }

    /**
     * What the Angular classes list in their decorators' settings, of the
     * Angular classes of the scan: what standalone components and
     * directives import, and what modules declare, import, export, provide
     * and bootstrap. An entry is matched by a name one Angular class of the
     * scan alone has.
     */
    private static List<Relationship> listed(
        final List<Declared> classes,
        final Map<String, String> angularNodes
    ) {
        final var byName = new HashMap<String, List<String>>();

        for (final var declared : classes) {
            if (angularNodes.containsKey(declared.id())) {
                byName.computeIfAbsent(declared.type().getName(), key -> new ArrayList<>()).add(angularNodes.get(declared.id()));
            }
        }

        final var relationships = new LinkedHashSet<Relationship>();

        for (final var declared : classes) {
            final var nodeId = angularNodes.get(declared.id());

            if (nodeId == null) {
                continue;
            }

            LISTS.forEach((name, types) -> decorator(declared.type(), name).ifPresent(decorator -> {
                final var settings = settings(decorator);

                types.forEach((setting, type) -> {
                    if (!(settings.get(setting) instanceof List<?> entries)) {
                        return;
                    }

                    for (final var entry : entries) {
                        entryName(entry)
                            .map(entryName -> byName.getOrDefault(entryName, List.of()))
                            .filter(targets -> targets.size() == 1 && !targets.getFirst().equals(nodeId))
                            .ifPresent(targets -> relationships.add(new Relationship(nodeId, targets.getFirst(), type)));
                    }
                });
            }));
        }

        return List.copyOf(relationships);
    }

    /**
     * The name of the class an entry of a list names: the name it starts
     * with, e.g. {@code RouterModule} for {@code RouterModule.forRoot([])},
     * or for a provider such as {@code {provide: A, useClass: B}}, the class
     * it uses, else its token.
     */
    static Optional<String> entryName(final Object entry) {
        if (entry instanceof Map<?, ?> provider) {
            return Optional.ofNullable(provider.get("useClass"))
                .or(() -> Optional.ofNullable(provider.get("useExisting")))
                .or(() -> Optional.ofNullable(provider.get("provide")))
                .flatMap(AngularEnhancer::entryName);
        }

        if (!(entry instanceof String text)) {
            return Optional.empty();
        }

        final var matcher = LEADING_NAME.matcher(text.trim());

        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    /**
     * The type without its type arguments or array brackets, e.g.
     * {@code HttpClient} for {@code HttpClient}, or {@code Observable} for
     * {@code Observable<Order[]>}.
     */
    static String rawType(final String type) {
        final var arguments = type.indexOf('<');
        final var raw = (arguments < 0 ? type : type.substring(0, arguments)).trim();

        return raw.replaceAll("(\\[])+$", "");
    }
}
