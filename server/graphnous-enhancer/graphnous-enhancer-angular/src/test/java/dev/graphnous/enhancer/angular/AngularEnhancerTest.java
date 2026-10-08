package dev.graphnous.enhancer.angular;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.core.model.Annotation;
import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Import;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.Parameter;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.core.model.TypeRef;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Enhances what the TypeScript scanner made of the application in
 * {@code src/test/resources/angular-shop}.
 */
class AngularEnhancerTest {

    private static final String MODULE = ".|.";
    private static final String ORDERS = MODULE + "|class:src/app/orders/";

    private static final String ORDER_SERVICE = ORDERS + "order.service:OrderService";
    private static final String INVOICE_SERVICE = ORDERS + "invoice.service:InvoiceService";
    private static final String ORDER_LIST = ORDERS + "order-list.component:OrderListComponent";
    private static final String HIGHLIGHT = MODULE + "|class:src/app/shared/highlight.directive:HighlightDirective";

    private final AngularEnhancer enhancer = new AngularEnhancer();

    private final Enhancements enhancements = enhancer.enhance(shop());

    @Test
    void enhancesTypeScript() {
        assertThat(enhancer.forLanguage()).isEqualTo(ScanTarget.Language.TYPESCRIPT);
        assertThat(enhancements.name()).isEqualTo("angular");
        assertThat(enhancements.version()).isEqualTo("1.0.0");
    }

    @Test
    void addsTheComponentsWithTheirSettings() {
        assertThat(node(ORDER_LIST + "|component")).isEqualTo(new Node(
            ORDER_LIST + "|component",
            ORDER_LIST,
            List.of("Component"),
            Map.of("selector", "app-order-list", "templateUrl", "./order-list.component.html", "standalone", true)
        ));
    }

    @Test
    void addsTheDirectives() {
        final var directive = node(HIGHLIGHT + "|directive");

        assertThat(directive.sourceId()).isEqualTo(HIGHLIGHT);
        assertThat(directive.labels()).containsExactly("Directive");
        assertThat(directive.metadata()).containsEntry("selector", "[appHighlight]");
        // Not set, so left out
        assertThat(directive.metadata().get("standalone")).isNull();
    }

    @Test
    void addsTheServices() {
        assertThat(node(ORDER_SERVICE + "|service").metadata()).containsEntry("providedIn", "root");
        assertThat(node(INVOICE_SERVICE + "|service").labels()).containsExactly("Service");
    }

    @Test
    void addsNothingForOtherClasses() {
        assertThat(nodes())
            .extracting(Node::sourceId)
            .noneMatch(source -> source.contains("order:Order") || source.contains("totals:Totals"));
    }

    @Test
    void addsTheHttpCallsMadeWithHttpClient() {
        assertThat(nodes())
            .filteredOn(node -> node.labels().contains("HttpCall"))
            .extracting(Node::id, node -> node.metadata().get("httpMethod"), node -> node.metadata().get("client"))
            .containsExactlyInAnyOrder(
                tuple(ORDER_SERVICE + "|method:src/app/orders/order.service:OrderService.list|httpCall:0", "GET", "this.http"),
                tuple(ORDER_SERVICE + "|method:src/app/orders/order.service:OrderService.find|httpCall:0", "GET", "this.http"),
                tuple(ORDER_SERVICE + "|method:src/app/orders/order.service:OrderService.create|httpCall:0", "POST", "this.http"),
                tuple(ORDER_SERVICE + "|method:src/app/orders/order.service:OrderService.remove|httpCall:0", "DELETE", "this.http"),
                // A field of type HttpClient, set with inject()
                tuple(INVOICE_SERVICE + "|method:src/app/orders/invoice.service:InvoiceService.download|httpCall:0", "REQUEST", "this.client")
            );
        // Without a type, this.http = inject(HttpClient) is not known to be one;
        // nor are the calls of a Map's get, or of pipe on what get returns
    }

    @Test
    void addsAnHttpCallToItsMethodAndService() {
        final var create = ORDER_SERVICE + "|method:src/app/orders/order.service:OrderService.create";
        final var call = node(create + "|httpCall:0");

        assertThat(call.sourceId()).isEqualTo(create);
        assertThat(call.labels()).containsExactly("HttpCall");
        assertThat(call.metadata()).containsEntry("line", 22);
        assertThat(relationships()).contains(new Relationship(ORDER_SERVICE + "|service", call.id(), "HAS_HTTP_CALL"));
    }

    @Test
    void findsWhatComponentsInjectThroughTheirConstructors() {
        assertThat(relationships())
            .filteredOn(relationship -> relationship.type().equals("INJECTS"))
            .containsExactly(
                // Its orders field is of a class of the scan, but state rather than injected;
                // ElementRef is not a class of the scan, and inject(InvoiceService) is not known
                new Relationship(ORDER_LIST + "|component", ORDER_SERVICE + "|service", "INJECTS")
            );
    }

    @Test
    void findsWhatStandaloneComponentsImport() {
        assertThat(relationships())
            .filteredOn(relationship -> relationship.type().equals("IMPORTS"))
            .containsExactly(new Relationship(ORDER_LIST + "|component", HIGHLIGHT + "|directive", "IMPORTS"));
    }

    @Test
    void leavesDecoratorsOfTheSameNameThatAreNotAngularsAlone() {
        // From another framework, and declared in the project
        final var nest = type("src/orders:OrdersService", decorator("Injectable", null));
        final var own = type("src/widgets:Widget", decorator("Component", "src/decorators:Component"));

        final var result = enhancer.enhance(result(
            file("src/orders.ts", List.of("@nestjs/common"), nest),
            file("src/widgets.ts", List.of("@angular/core"), own)
        ));

        assertThat(result.enhancements()).isEmpty();
    }

    @Test
    void findsHttpCallsOnParametersOfTypeHttpClient() {
        final var client = new Parameter();
        client.setName("http");
        client.setType(typeRef("HttpClient"));

        final var load = new Method();
        load.setName("load");
        load.setQualifiedName("src/orders:load");
        load.setKind(Method.Kind.FUNCTION);
        load.setParameters(List.of(client));
        load.setCalls(List.of(unresolved("http.patch"), unresolved("this.http.patch"), unresolved("other.patch")));

        final var loader = type("src/orders:OrderLoader");
        loader.setMethods(List.of(load));

        final var result = enhancer.enhance(result(file("src/orders.ts", List.of("@angular/common/http"), loader)));

        assertThat(result.enhancements())
            .flatExtracting(Enhancement::nodes)
            .extracting(node -> node.metadata().get("httpMethod"), node -> node.metadata().get("client"))
            .containsExactly(tuple("PATCH", "http"));
        // Not an Angular class, so nothing has the call but its method
        assertThat(result.enhancements()).flatExtracting(Enhancement::relationships).isEmpty();
    }

    @Test
    void knowsTheMethodsOfHttpClient() {
        final var fields = Set.of("http");

        for (final var method : List.of("get", "post", "put", "patch", "delete", "head", "options", "request", "jsonp")) {
            assertThat(AngularEnhancer.httpCall(unresolved("this.http." + method), fields, List.of())).as(method).isPresent();
        }

        assertThat(AngularEnhancer.httpCall(unresolved("this.http.get().pipe"), fields, List.of())).isEmpty();
        assertThat(AngularEnhancer.httpCall(unresolved("this.http.handle"), fields, List.of())).isEmpty();
    }

    @Test
    void addsNothingToAnApplicationWithoutAngular() {
        assertThat(enhancer.enhance(result(file("src/index.ts", List.of("express"), type("src/index:App")))).enhancements())
            .isEmpty();
    }

    @Test
    void stripsTypeArgumentsAndArrays() {
        assertThat(AngularEnhancer.rawType("HttpClient")).isEqualTo("HttpClient");
        assertThat(AngularEnhancer.rawType("Observable<Order[]>")).isEqualTo("Observable");
        assertThat(AngularEnhancer.rawType("Order[][]")).isEqualTo("Order");
    }

    private List<Node> nodes() {
        return enhancements.enhancements().stream().flatMap(enhancement -> enhancement.nodes().stream()).toList();
    }

    private List<Relationship> relationships() {
        return enhancements.enhancements().stream().flatMap(enhancement -> enhancement.relationships().stream()).toList();
    }

    private Node node(final String id) {
        return nodes().stream()
            .filter(node -> node.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No node " + id + " in " + nodes().stream().map(Node::id).toList()));
    }

    private static ScanResult shop() {
        try (final var json = AngularEnhancerTest.class.getResourceAsStream("/angular-shop.json")) {
            return new ObjectMapper().readValue(json, ScanResult.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ScanResult result(final File... files) {
        final var module = new Module();
        module.setPath(".");
        module.setFiles(List.of(files));

        final var target = new ScanTarget();
        target.setPath(".");
        target.setLanguage(ScanTarget.Language.TYPESCRIPT);

        final var result = new ScanResult();
        result.setTarget(target);
        result.setModules(List.of(module));

        return result;
    }

    private static File file(final String path, final List<String> imports, final Class... classes) {
        final var file = new File();
        file.setPath(path);
        file.setImports(imports.stream().map(name -> {
            final var imported = new Import();
            imported.setName(name);
            return imported;
        }).toList());
        file.setClasses(List.of(classes));

        return file;
    }

    private static Class type(final String qualifiedName, final Annotation... decorators) {
        final var type = new Class();
        type.setName(qualifiedName.substring(qualifiedName.lastIndexOf(':') + 1));
        type.setQualifiedName(qualifiedName);
        type.setAnnotations(List.of(decorators));

        return type;
    }

    private static TypeRef typeRef(final String name) {
        final var type = new TypeRef();
        type.setName(name);

        return type;
    }

    private static Annotation decorator(final String name, final String qualifiedName) {
        final var annotation = new Annotation();
        annotation.setName(name);
        annotation.setQualifiedName(qualifiedName);

        return annotation;
    }

    private static Call unresolved(final String target) {
        final var call = new Call();
        call.setTarget(target);
        call.setKind(Call.Kind.METHOD);
        call.setResolved(false);

        return call;
    }
}
