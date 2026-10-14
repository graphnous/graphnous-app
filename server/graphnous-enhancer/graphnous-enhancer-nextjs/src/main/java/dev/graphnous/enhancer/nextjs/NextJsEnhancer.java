package dev.graphnous.enhancer.nextjs;

import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Modifier;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget.Language;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Finds the API routes of Next.js applications and their endpoints:
 * <pre>
 * (File)-[:ENHANCE]->(Route {url})-[:HAS_ENDPOINT]->(Endpoint {httpMethod, url})
 * (Method|Field)-[:ENHANCE]->(Endpoint)-[:HAS_PATH_VARIABLE]->(PathVariable {name, optional})
 * (File)-[:ENHANCE]->(PathVariable)
 * </pre>
 * The {@code ENHANCE} relationships come with the nodes' sources.
 * A route is a {@code route.ts} or {@code route.tsx} (or {@code .js},
 * {@code .jsx}) of the App Router under its {@code api} folder, e.g.
 * {@code src/app/api/orders/[id]/route.ts}. Its url is its folder below
 * {@code app}, without route groups such as {@code (shop)} and parallel
 * route slots such as {@code @modal}; routes in private folders, such as
 * {@code _lib}, are not routed.
 * <p>
 * Each function the route exports named after an HTTP method, such as
 * {@code export async function GET()} or {@code export const POST = ...},
 * is its endpoint for that method; so is an exported variable of that name,
 * as for {@code export const GET = handler}.
 * <p>
 * The dynamic segments of the url are its path variables, written as in
 * Spring urls so endpoints of both read alike: {@code [id]} is
 * {@code {id}}, and the catch-all {@code [...slug]} and optional catch-all
 * {@code [[...slug]]} are {@code {*slug}}. The optional catch-all is the
 * only optional one.
 */
public class NextJsEnhancer implements Enhancer {

    static final String NAME = "nextjs";
    static final String VERSION = "1.0.0";

    /**
     * The HTTP methods a route handler can be named after.
     */
    private static final List<String> HTTP_METHODS = List.of(
        "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    );

    private static final Pattern ROUTE_FILE = Pattern.compile("route\\.(ts|tsx|js|jsx)");

    private static final Pattern OPTIONAL_CATCH_ALL = Pattern.compile("\\[\\[\\.\\.\\.(.+)]]");
    private static final Pattern CATCH_ALL = Pattern.compile("\\[\\.\\.\\.(.+)]");
    private static final Pattern DYNAMIC = Pattern.compile("\\[(.+)]");

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
        final var enhancements = new ArrayList<Enhancement>();
        final var targetId = scanResult.getTarget().getPath();

        for (final var module : scanResult.getModules()) {
            final var moduleId = targetId + "|" + module.getPath();

            for (final var file : module.getFiles()) {
                route(file.getPath())
                    .map(route -> enhancement(moduleId + "|file:" + file.getPath(), file, route))
                    .ifPresent(enhancements::add);
            }
        }

        return new Enhancements(NAME, VERSION, enhancements);
    }

    /**
     * The url of a route and its dynamic segments.
     */
    record Route(String url, List<PathVariable> pathVariables) {
    }

    record PathVariable(String name, boolean optional) {
    }

    private static Enhancement enhancement(
        final String fileId,
        final File file,
        final Route route
    ) {
        final var routeId = fileId + "|route";

        final var nodes = new ArrayList<Node>();
        final var relationships = new ArrayList<Relationship>();

        nodes.add(new Node(routeId, fileId, List.of("Route"), Map.of("url", route.url())));

        final var pathVariables = route.pathVariables()
            .stream()
            .map(variable -> new Node(
                fileId + "|pathVariable:" + variable.name(),
                fileId,
                List.of("PathVariable"),
                Map.of("name", variable.name(), "optional", variable.optional())
            ))
            .toList();

        final var handlers = new ArrayList<Handler>();

        for (final var function : file.getFunctions()) {
            if (isHandler(function.getName(), function.getModifiers())) {
                handlers.add(new Handler(fileId + "|function:" + function.getQualifiedName(), function.getName()));
            }
        }

        for (final var variable : file.getVariables()) {
            if (isHandler(variable.getName(), variable.getModifiers())) {
                handlers.add(new Handler(fileId + "|variable:" + variable.getName(), variable.getName()));
            }
        }

        if (!handlers.isEmpty()) {
            nodes.addAll(pathVariables);
        }

        for (final var handler : handlers) {
            final var endpointId = handler.id() + "|endpoint:" + handler.httpMethod() + " " + route.url();

            nodes.add(new Node(endpointId, handler.id(), List.of("Endpoint"), Map.of(
                "httpMethod", handler.httpMethod(),
                "url", route.url()
            )));
            relationships.add(new Relationship(routeId, endpointId, "HAS_ENDPOINT"));

            for (final var pathVariable : pathVariables) {
                relationships.add(new Relationship(endpointId, pathVariable.id(), "HAS_PATH_VARIABLE"));
            }
        }

        return new Enhancement(nodes, relationships);
    }

    /**
     * A function or variable of a route that handles an HTTP method, by
     * the id of its node.
     */
    private record Handler(String id, String httpMethod) {
    }

    /**
     * Whether the function or variable is exported and named after an HTTP
     * method; only exports handle requests.
     */
    private static boolean isHandler(
        final String name,
        final List<Modifier> modifiers
    ) {
        return HTTP_METHODS.contains(name) && modifiers.contains(Modifier.EXPORT);
    }

    /**
     * The route of the file, if it is an API route of the App Router.
     */
    static Optional<Route> route(final String path) {
        if (path == null) {
            return Optional.empty();
        }

        final var segments = Arrays.asList(path.replace('\\', '/').split("/"));

        if (!ROUTE_FILE.matcher(segments.getLast()).matches()) {
            return Optional.empty();
        }

        final var folders = segments.subList(0, segments.size() - 1);
        final var app = folders.indexOf("app");

        if (app < 0) {
            return Optional.empty();
        }

        final var url = new ArrayList<String>();
        final var pathVariables = new ArrayList<PathVariable>();

        for (final var folder : folders.subList(app + 1, folders.size())) {
            if (folder.startsWith("_")) {
                return Optional.empty();
            }

            if (folder.isEmpty() || folder.startsWith("@") || (folder.startsWith("(") && folder.endsWith(")"))) {
                continue;
            }

            url.add(segment(folder, pathVariables));
        }

        if (url.isEmpty() || !url.getFirst().equals("api")) {
            return Optional.empty();
        }

        return Optional.of(new Route("/" + String.join("/", url), pathVariables));
    }

    /**
     * The url segment of the folder, adding its path variable if it is
     * dynamic.
     */
    private static String segment(
        final String folder,
        final List<PathVariable> pathVariables
    ) {
        final var optionalCatchAll = OPTIONAL_CATCH_ALL.matcher(folder);

        if (optionalCatchAll.matches()) {
            pathVariables.add(new PathVariable(optionalCatchAll.group(1), true));

            return "{*" + optionalCatchAll.group(1) + "}";
        }

        final var catchAll = CATCH_ALL.matcher(folder);

        if (catchAll.matches()) {
            pathVariables.add(new PathVariable(catchAll.group(1), false));

            return "{*" + catchAll.group(1) + "}";
        }

        final var dynamic = DYNAMIC.matcher(folder);

        if (dynamic.matches()) {
            pathVariables.add(new PathVariable(dynamic.group(1), false));

            return "{" + dynamic.group(1) + "}";
        }

        return folder;
    }
}
