package dev.graphnous.enhancer.nextjs;

import dev.graphnous.core.model.Field;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Modifier;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class NextJsEnhancerTest {

    private static final String PATH = "src/app/api/orders/route.ts";
    private static final String FILE = "web|.|file:" + PATH;
    private static final String ROUTE = FILE + "|route";

    private final NextJsEnhancer enhancer = new NextJsEnhancer();

    @Test
    void enhancesTypeScript() {
        assertThat(enhancer.forLanguage()).isEqualTo(ScanTarget.Language.TYPESCRIPT);
    }

    @Test
    void namesItsEnhancements() {
        final var enhancements = enhancer.enhance(scanResult(file(PATH)));

        assertThat(enhancer.name()).isEqualTo("nextjs");
        assertThat(enhancer.version()).isEqualTo("1.0.0");
        assertThat(enhancements.name()).isEqualTo("nextjs");
        assertThat(enhancements.version()).isEqualTo("1.0.0");
    }

    @Test
    void addsAnEndpointForEachExportedHttpMethodFunction() {
        final var file = file(PATH);
        file.getFunctions().addAll(List.of(
            function("GET", Modifier.EXPORT, Modifier.ASYNC),
            function("POST", Modifier.EXPORT),
            function("PUT", Modifier.EXPORT),
            function("helper", Modifier.EXPORT),
            function("DELETE")
        ));

        final var enhancement = enhance(file);
        final var get = FILE + "|function:src/app/api/orders/route:GET";

        assertThat(enhancement.nodes()).first()
            .isEqualTo(new Node(ROUTE, FILE, List.of("Route"), Map.of("url", "/api/orders")));
        assertThat(enhancement.nodes()).contains(
            new Node(get + "|endpoint:GET /api/orders", get, List.of("Endpoint"), Map.of("httpMethod", "GET", "url", "/api/orders"))
        );
        assertThat(enhancement.nodes())
            .filteredOn(node -> node.labels().contains("Endpoint"))
            .extracting(node -> node.metadata().get("httpMethod"))
            .containsExactly("GET", "POST", "PUT");
        assertThat(enhancement.relationships()).contains(
            new Relationship(ROUTE, get + "|endpoint:GET /api/orders", "HAS_ENDPOINT")
        );
    }

    @Test
    void addsAnEndpointForAnExportedHttpMethodVariable() {
        final var file = file(PATH);
        final var get = new Field();
        get.setName("GET");
        get.setQualifiedName("src/app/api/orders/route:GET");
        get.getModifiers().add(Modifier.EXPORT);
        file.getVariables().add(get);

        assertThat(enhance(file).nodes())
            .extracting(Node::id, Node::sourceId)
            .contains(tuple(FILE + "|variable:GET|endpoint:GET /api/orders", FILE + "|variable:GET"));
    }

    @Test
    void linksThePathVariablesOfDynamicSegments() {
        final var path = "src/app/api/orders/[id]/route.ts";
        final var file = file(path);
        file.getFunctions().add(function("GET", Modifier.EXPORT));

        final var enhancement = enhance(file);
        final var fileId = "web|.|file:" + path;
        final var endpoint = fileId + "|function:src/app/api/orders/route:GET|endpoint:GET /api/orders/{id}";

        assertThat(enhancement.nodes()).contains(
            new Node(fileId + "|pathVariable:id", fileId, List.of("PathVariable"), Map.of("name", "id", "optional", false))
        );
        assertThat(enhancement.relationships()).contains(
            new Relationship(endpoint, fileId + "|pathVariable:id", "HAS_PATH_VARIABLE")
        );
    }

    @Test
    void addsOnlyTheRouteWithoutHandlers() {
        final var file = file("src/app/api/orders/[id]/route.ts");

        assertThat(enhance(file).nodes()).extracting(node -> node.labels().getFirst()).containsExactly("Route");
    }

    @Test
    void enhancesOnlyApiRoutes() {
        final var page = file("src/app/orders/page.tsx");
        final var route = file("src/app/orders/route.ts");
        final var lib = file("src/lib/api/route.ts");

        assertThat(enhancer.enhance(scanResult(page, route, lib)).enhancements()).isEmpty();
    }

    @Test
    void writesUrlsAsSpringDoes() {
        assertThat(url("app/api/orders/route.ts")).contains("/api/orders");
        assertThat(url("src/app/api/route.tsx")).contains("/api");
        assertThat(url("apps/web/src/app/api/orders/[id]/route.js")).contains("/api/orders/{id}");
        assertThat(url("app/api/files/[...path]/route.ts")).contains("/api/files/{*path}");
        assertThat(url("app/api/docs/[[...slug]]/route.ts")).contains("/api/docs/{*slug}");
    }

    @Test
    void leavesRouteGroupsAndSlotsOutOfTheUrl() {
        assertThat(url("app/(backend)/api/(v1)/orders/route.ts")).contains("/api/orders");
        assertThat(url("app/@modal/api/orders/route.ts")).contains("/api/orders");
    }

    @Test
    void doesNotRoutePrivateFolders() {
        assertThat(url("app/api/_internal/orders/route.ts")).isEmpty();
    }

    @Test
    void makesOnlyTheOptionalCatchAllOptional() {
        assertThat(NextJsEnhancer.route("app/api/[team]/[[...slug]]/route.ts").orElseThrow().pathVariables())
            .containsExactly(
                new NextJsEnhancer.PathVariable("team", false),
                new NextJsEnhancer.PathVariable("slug", true)
            );
    }

    private static Optional<String> url(final String path) {
        return NextJsEnhancer.route(path).map(NextJsEnhancer.Route::url);
    }

    private Enhancement enhance(final File file) {
        return enhancer.enhance(scanResult(file)).enhancements().getFirst();
    }

    private static Method function(final String name, final Modifier... modifiers) {
        final var function = new Method();
        function.setName(name);
        function.setQualifiedName("src/app/api/orders/route:" + name);
        function.setKind(Method.Kind.FUNCTION);
        function.getModifiers().addAll(List.of(modifiers));

        return function;
    }

    private static File file(final String path) {
        final var file = new File();
        file.setPath(path);
        file.setLanguage("typescript");

        return file;
    }

    private static ScanResult scanResult(final File... files) {
        final var module = new Module();
        module.setPath(".");
        module.getFiles().addAll(List.of(files));

        final var target = new ScanTarget();
        target.setPath("web");
        target.setLanguage(ScanTarget.Language.TYPESCRIPT);

        final var result = new ScanResult();
        result.setTarget(target);
        result.getModules().add(module);

        return result;
    }
}
