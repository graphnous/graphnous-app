package dev.graphnous.enhancer.fetch;

import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Node;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FetchBrowserEnhancerTest {

    private static final String PATH = "src/lib/orders.ts";
    private static final String FILE = "web|.|file:" + PATH;
    private static final String FUNCTION = FILE + "|function:src/lib/orders:load";

    private final FetchBrowserEnhancer enhancer = new FetchBrowserEnhancer();

    @Test
    void enhancesTypeScript() {
        assertThat(enhancer.forLanguage()).isEqualTo(ScanTarget.Language.TYPESCRIPT);
    }

    @Test
    void namesItsEnhancements() {
        final var enhancements = enhancer.enhance(scanResult(file()));

        assertThat(enhancer.name()).isEqualTo("fetch-browser");
        assertThat(enhancer.version()).isEqualTo("1.0.0");
        assertThat(enhancements.name()).isEqualTo("fetch-browser");
        assertThat(enhancements.version()).isEqualTo("1.0.0");
    }

    @Test
    void addsAnHttpCallForEachFetchOfAFunction() {
        final var file = file();
        file.getFunctions().add(function("load", call("fetch", 3, 11), call("console.log", 4, 5), call("fetch", 8, 23)));

        assertThat(enhance(file).nodes()).containsExactly(
            new Node(FUNCTION + "|httpCall:3:11", FUNCTION, List.of("HttpCall"), Map.of(
                "client", "fetch",
                "startLine", 3,
                "startColumn", 11,
                "endLine", 3,
                "endColumn", 40
            )),
            new Node(FUNCTION + "|httpCall:8:23", FUNCTION, List.of("HttpCall"), Map.of(
                "client", "fetch",
                "startLine", 8,
                "startColumn", 23,
                "endLine", 8,
                "endColumn", 40
            ))
        );
    }

    @Test
    void takesTheUrlFromTheFirstArgument() {
        final var call = call("fetch", 3, 11);
        call.setArguments(List.of("`/api/orders/${id}`", "{ method: \"DELETE\" }"));

        final var file = file();
        file.getFunctions().add(function("load", call));

        assertThat(enhance(file).nodes().getFirst().metadata()).containsEntry("url", "/api/orders/{id}");
    }

    @Test
    void addsTheHttpCallsOfMethodsOfClassesAndTheirNestedClasses() {
        final var nested = new Class();
        nested.setQualifiedName("src/lib/orders:Orders.Client");
        nested.getMethods().add(method("src/lib/orders:Orders.Client.get", call("globalThis.fetch", 2, 1)));

        final var orders = new Class();
        orders.setQualifiedName("src/lib/orders:Orders");
        orders.getMethods().add(method("src/lib/orders:Orders.list", call("window.fetch", 1, 1)));
        orders.getClasses().add(nested);

        final var file = file();
        file.getClasses().add(orders);

        assertThat(enhancer.enhance(scanResult(file)).enhancements())
            .flatExtracting(Enhancement::nodes)
            .extracting(Node::sourceId)
            .containsExactly(
                "web|.|class:src/lib/orders:Orders|method:src/lib/orders:Orders.list",
                "web|.|class:src/lib/orders:Orders.Client|method:src/lib/orders:Orders.Client.get"
            );
    }

    @Test
    void addsAnHttpCallForACalleeThatMayBeFetch() {
        // As the generated OpenAPI client calls it
        final var file = file();
        file.getFunctions().add(function("load",
            call("this.configuration.fetchApi || fetch", 1, 1),
            call("this.fetchImpl ?? globalThis.fetch", 2, 1),
            call("(mock ? stub : window.fetch)", 3, 1),
            call("this.config?.fetchApi", 4, 1),
            call("this.fetchApi || fetch()", 5, 1)
        ));

        assertThat(enhance(file).nodes())
            .extracting(node -> node.metadata().get("startLine"))
            .containsExactly(1, 2, 3);
    }

    @Test
    void ignoresAResolvedCalleeThatMayBeFetchByName() {
        final var call = call("this.fetchApi || fetch", 1, 1);
        call.setResolved(true);

        assertThat(FetchBrowserEnhancer.isFetch(call)).isFalse();
    }

    @Test
    void ignoresFetchFunctionsOfTheTargetAndFetchPassedOn() {
        // The scanner resolves calls of the target's own declarations
        final var own = call("src/lib/api:fetch", 1, 1);
        own.setResolved(true);

        final var reference = call("fetch", 5, 1);
        reference.setKind(Call.Kind.METHOD_REFERENCE);

        final var file = file();
        file.getFunctions().add(function("load", own, call("this.client.fetch", 2, 1), reference));

        assertThat(enhancer.enhance(scanResult(file)).enhancements()).isEmpty();
    }

    @Test
    void addsNothingWithoutFetchCalls() {
        final var file = file();
        file.getFunctions().add(function("load", call("console.log", 1, 1)));

        assertThat(enhancer.enhance(scanResult(file)).enhancements()).isEmpty();
    }

    private Enhancement enhance(final File file) {
        return enhancer.enhance(scanResult(file)).enhancements().getFirst();
    }

    private static Call call(final String target, final int line, final int column) {
        final var call = new Call();
        call.setTarget(target);
        call.setKind(Call.Kind.METHOD);
        call.setResolved(false);
        call.setStartLine(line);
        call.setStartColumn(column);
        call.setEndLine(line);
        call.setEndColumn(40);

        return call;
    }

    private static Method function(final String name, final Call... calls) {
        final var function = method("src/lib/orders:" + name, calls);
        function.setName(name);
        function.setKind(Method.Kind.FUNCTION);

        return function;
    }

    private static Method method(final String qualifiedName, final Call... calls) {
        final var method = new Method();
        method.setQualifiedName(qualifiedName);
        method.getCalls().addAll(List.of(calls));

        return method;
    }

    private static File file() {
        final var file = new File();
        file.setPath(PATH);
        file.setLanguage("typescript");

        return file;
    }

    private static ScanResult scanResult(final File file) {
        final var module = new Module();
        module.setPath(".");
        module.getFiles().add(file);

        final var target = new ScanTarget();
        target.setPath("web");
        target.setLanguage(ScanTarget.Language.TYPESCRIPT);

        final var result = new ScanResult();
        result.setTarget(target);
        result.getModules().add(module);

        return result;
    }
}
