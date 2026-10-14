package dev.graphnous.persistence.scan.usages;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.enhancer.angular.AngularEnhancer;
import dev.graphnous.persistence.scan.graph.ScanGraphRepositoryImpl;
import dev.graphnous.persistence.scan.result.EnhancementRepositoryImpl;
import dev.graphnous.persistence.scan.result.ScanResultRepositoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * What uses the nodes of the scan of an Angular application, as the
 * TypeScript scanner found it, stored by ScanResultRepositoryImpl in Neo4j
 * in a container; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class ScanUsageRepositoryImplTest {

    private static final String ORDERS = "src/app/orders/";

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static Driver driver;

    private final Scan.ScanId scanId = new Scan.ScanId(UUID.randomUUID());

    private ScanUsageRepositoryImpl repository;

    @BeforeAll
    static void connect() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    static void disconnect() {
        driver.close();
    }

    @BeforeEach
    void storeTheScan() throws IOException {
        final ScanResult result;

        try (final var json = ScanUsageRepositoryImplTest.class.getResourceAsStream("/scan-result-angular.json")) {
            result = new ObjectMapper().readValue(json, ScanResult.class);
        }

        // Besides what the application has: ngOnInit constructs Totals, twice,
        // and the invoice service calls ngOnInit
        final var ngOnInit = method(result, ORDERS + "order-list.component:OrderListComponent.ngOnInit");
        final var withArguments = call(ORDERS + "totals:Totals.constructor", Call.Kind.CONSTRUCTOR, 22);
        withArguments.getArguments().add(argument(null, "this.orders", false));
        withArguments.getArguments().add(argument("currency", "\"EUR\"", false));
        withArguments.getArguments().add(argument(null, "options", true));
        ngOnInit.getCalls().add(withArguments);
        ngOnInit.getCalls().add(call(ORDERS + "totals:Totals.constructor", Call.Kind.CONSTRUCTOR, 23));
        // Not of the scan, so not a usage
        ngOnInit.getCalls().add(call("rxjs:Subject.constructor", Call.Kind.CONSTRUCTOR, 24));

        method(result, ORDERS + "invoice.service:InvoiceService.refresh")
            .getCalls().add(call(ORDERS + "order-list.component:OrderListComponent.ngOnInit", Call.Kind.METHOD, 15));

        try (final var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
            session.run("CREATE (:Scan {id: $id})", Map.of("id", scanId.id().toString())).consume();
        }

        new ScanResultRepositoryImpl(driver).save(scanId, List.of(result));
        new EnhancementRepositoryImpl(driver).save(scanId, List.of(new AngularEnhancer().enhance(result)));

        repository = new ScanUsageRepositoryImpl(driver);
    }

    @Test
    void findsWhereAMethodIsCalled() {
        final var list = classId("order.service:OrderService") + "|method:" + ORDERS + "order.service:OrderService.list";

        final var found = repository.findUsages(scanId, list, 1, 100).orElseThrow();

        assertThat(found.subject()).isEqualTo(
            new ScanUsages.Node(list, "Method", "list", ORDERS + "order.service:OrderService.list")
        );
        assertThat(found.usages())
            .extracting(
                ScanUsages.Usage::relationship, usage -> usage.user().name(), ScanUsages.Usage::owner,
                ScanUsages.Usage::file, ScanUsages.Usage::line, ScanUsages.Usage::depth
            )
            .containsExactly(tuple("CALLS", "ngOnInit", "OrderListComponent", ORDERS + "order-list.component.ts", 21, 1));
        assertThat(found.truncated()).isFalse();
    }

    @Test
    void findsWhereAClassAndItsMembersAreUsed() {
        final var orderService = repository.findUsages(scanId, classId("order.service:OrderService"), 1, 100).orElseThrow();

        // Its method is called, and its own methods read its field
        assertThat(orderService.usages())
            .extracting(ScanUsages.Usage::relationship, usage -> usage.user().name(), usage -> usage.target().name(), ScanUsages.Usage::line)
            .contains(
                tuple("CALLS", "ngOnInit", "list", 21),
                tuple("READS", "list", "http", 13),
                tuple("READS", "remove", "http", 26)
            );

        final var order = repository.findUsages(scanId, classId("order:Order"), 1, 100).orElseThrow();

        assertThat(order.usages())
            .extracting(ScanUsages.Usage::relationship, usage -> usage.user().name(), ScanUsages.Usage::file, ScanUsages.Usage::line)
            .containsExactly(
                tuple("USES", "list", ORDERS + "order.service.ts", 13),
                tuple("USES", "find", ORDERS + "order.service.ts", 18),
                tuple("USES", "create", ORDERS + "order.service.ts", 22)
            );
    }

    @Test
    void findsEachPlaceAClassIsInstantiated() {
        final var totals = repository.findUsages(scanId, classId("totals:Totals"), 1, 100).orElseThrow();

        assertThat(totals.usages())
            .filteredOn(usage -> usage.relationship().equals("INSTANTIATES"))
            .extracting(usage -> usage.user().name(), ScanUsages.Usage::line)
            .containsExactly(tuple("ngOnInit", 22), tuple("ngOnInit", 23));
    }

    @Test
    void findsWhereAFieldIsReadAndWritten() {
        final var orders = classId("order-list.component:OrderListComponent") + "|field:orders";

        assertThat(repository.findUsages(scanId, orders, 1, 100).orElseThrow().usages())
            .extracting(ScanUsages.Usage::relationship, usage -> usage.user().name(), ScanUsages.Usage::line)
            .containsExactly(tuple("WRITES", "ngOnInit", 21));
    }

    @Test
    void followsWhatCallsWhatUsesIt() {
        final var list = classId("order.service:OrderService") + "|method:" + ORDERS + "order.service:OrderService.list";

        final var found = repository.findUsages(scanId, list, 2, 100).orElseThrow();

        assertThat(found.usages())
            .extracting(ScanUsages.Usage::depth, usage -> usage.user().name(), usage -> usage.target().name(), ScanUsages.Usage::line)
            .containsExactly(
                tuple(1, "ngOnInit", "list", 21),
                tuple(2, "refresh", "ngOnInit", 15)
            );
    }

    @Test
    void findsTheUsagesOfWhatAnEnhancerAddedThroughWhatItEnhances() {
        final var service = classId("order.service:OrderService");

        final var found = repository.findUsages(scanId, service + "|service", 1, 100).orElseThrow();

        assertThat(found.subject().id()).isEqualTo(service);
        assertThat(found.usages()).isNotEmpty();
    }

    @Test
    void findsAMethodUsedThroughTheMethodsItOverridesAndThatOverrideIt() throws IOException {
        final var scan = storeWithInheritance();
        final var list = id(scan, "order.service:OrderService") + "|method:" + ORDERS + "order.service:OrderService.list";

        final var found = repository.findUsages(scan, list, 1, 100).orElseThrow();

        assertThat(found.usages())
            .extracting(
                ScanUsages.Usage::relationship, ScanUsages.Usage::owner, usage -> usage.user().name(),
                ScanUsages.Usage::targetOwner, ScanUsages.Usage::line
            )
            .containsExactlyInAnyOrder(
                // Called as itself, and through the interface it implements
                tuple("CALLS", "OrderListComponent", "ngOnInit", "OrderService", 21),
                tuple("CALLS", "InvoiceService", "refresh", "Repo", 30),
                // Has to follow a change to it
                tuple("OVERRIDES", "CachedOrderService", "list", "OrderService", null)
            );
    }

    @Test
    void findsAnInterfacesMethodUsedThroughTheMethodsThatImplementIt() throws IOException {
        final var scan = storeWithInheritance();
        final var list = id(scan, "repo:Repo") + "|method:" + ORDERS + "repo:Repo.list";

        assertThat(repository.findUsages(scan, list, 1, 100).orElseThrow().usages())
            .extracting(ScanUsages.Usage::relationship, ScanUsages.Usage::owner, ScanUsages.Usage::targetOwner)
            .containsExactlyInAnyOrder(
                tuple("CALLS", "InvoiceService", "Repo"),
                tuple("CALLS", "OrderListComponent", "OrderService"),
                tuple("OVERRIDES", "OrderService", "Repo"),
                tuple("OVERRIDES", "CachedOrderService", "Repo")
            );
    }

    @Test
    void findsTheClassesBelowTheOnesThatExtendAClass() throws IOException {
        final var scan = storeWithInheritance();

        assertThat(repository.findUsages(scan, id(scan, "repo:Repo"), 1, 100).orElseThrow().usages())
            .filteredOn(usage -> usage.user().type().equals("Class"))
            .extracting(ScanUsages.Usage::relationship, usage -> usage.user().name(), usage -> usage.target().name())
            .containsExactlyInAnyOrder(
                tuple("IMPLEMENTS", "OrderService", "Repo"),
                tuple("EXTENDS", "CachedOrderService", "OrderService"),
                tuple("EXTENDS", "SuperCachedOrderService", "CachedOrderService")
            );
    }

    @Test
    void keepsConstructorsToTheirOwnClass() throws IOException {
        final var scan = storeWithInheritance();
        final var component = id(scan, "order-list.component:OrderListComponent");

        // The constructor of another class is not one that overrides it
        assertThat(repository.findUsages(scan, component + "|method:" + ORDERS + "order-list.component:OrderListComponent.constructor", 1, 100)
            .orElseThrow().usages())
            .extracting(ScanUsages.Usage::relationship)
            .doesNotContain("OVERRIDES");
    }

    /**
     * The application with an interface the order service implements,
     * whose method the invoice service calls, and two classes below the
     * order service, the first of which overrides its method; as a scan
     * of its own.
     */
    private Scan.ScanId storeWithInheritance() throws IOException {
        final ScanResult result;

        try (final var json = ScanUsageRepositoryImplTest.class.getResourceAsStream("/scan-result-angular.json")) {
            result = new ObjectMapper().readValue(json, ScanResult.class);
        }

        final var repo = type("repo:Repo", dev.graphnous.core.model.Class.Kind.INTERFACE);
        repo.getMethods().add(declared("repo:Repo.list", "list"));

        final var cached = type("cached:CachedOrderService", dev.graphnous.core.model.Class.Kind.CLASS);
        cached.getSuperClasses().add(reference("order.service:OrderService"));
        cached.getMethods().add(declared("cached:CachedOrderService.list", "list"));

        final var superCached = type("cached:SuperCachedOrderService", dev.graphnous.core.model.Class.Kind.CLASS);
        superCached.getSuperClasses().add(reference("cached:CachedOrderService"));

        final var file = new dev.graphnous.core.model.File();
        file.setPath(ORDERS + "cached.ts");
        file.getClasses().addAll(List.of(repo, cached, superCached));
        result.getModules().getFirst().getFiles().add(file);

        result.getModules().stream()
            .flatMap(module -> module.getFiles().stream())
            .flatMap(sourceFile -> sourceFile.getClasses().stream())
            .filter(type -> (ORDERS + "order.service:OrderService").equals(type.getQualifiedName()))
            .forEach(type -> type.getInterfaces().add(reference("repo:Repo")));

        method(result, ORDERS + "invoice.service:InvoiceService.refresh")
            .getCalls().add(call(ORDERS + "repo:Repo.list", Call.Kind.METHOD, 30));

        final var scan = new Scan.ScanId(UUID.randomUUID());

        try (final var session = driver.session()) {
            session.run("CREATE (:Scan {id: $id})", Map.of("id", scan.id().toString())).consume();
        }

        new ScanResultRepositoryImpl(driver).save(scan, List.of(result));

        return scan;
    }

    private static dev.graphnous.core.model.Class type(final String qualifiedName, final dev.graphnous.core.model.Class.Kind kind) {
        final var type = new dev.graphnous.core.model.Class();
        type.setName(qualifiedName.substring(qualifiedName.indexOf(':') + 1));
        type.setQualifiedName(ORDERS + qualifiedName);
        type.setKind(kind);

        return type;
    }

    private static Method declared(final String qualifiedName, final String name) {
        final var method = new Method();
        method.setName(name);
        method.setQualifiedName(ORDERS + qualifiedName);
        method.setKind(Method.Kind.METHOD);

        return method;
    }

    private static dev.graphnous.core.model.TypeRef reference(final String qualifiedName) {
        final var reference = new dev.graphnous.core.model.TypeRef();
        reference.setName(ORDERS + qualifiedName);

        return reference;
    }

    private static String id(final Scan.ScanId scan, final String qualifiedName) {
        return scan.id() + "|.|.|class:" + ORDERS + qualifiedName;
    }

    @Test
    void saysWhatEachCallPasses() {
        final var totals = repository.findUsages(scanId, classId("totals:Totals"), 1, 100).orElseThrow();

        assertThat(totals.usages())
            .filteredOn(usage -> usage.relationship().equals("INSTANTIATES"))
            .extracting(ScanUsages.Usage::line, ScanUsages.Usage::arguments)
            .containsExactly(
                tuple(22, List.of(
                    new ScanUsages.Argument(null, "this.orders", false),
                    new ScanUsages.Argument("currency", "\"EUR\"", false),
                    new ScanUsages.Argument(null, "options", true)
                )),
                // Passes nothing, which is not the same as not saying
                tuple(23, List.of())
            );

        // Only a call passes anything
        assertThat(totals.usages())
            .filteredOn(usage -> !List.of("CALLS", "INSTANTIATES").contains(usage.relationship()))
            .allSatisfy(usage -> assertThat(usage.arguments()).isNull());
    }

    private static dev.graphnous.core.model.Argument argument(
        final String name,
        final String value,
        final boolean spread
    ) {
        final var argument = new dev.graphnous.core.model.Argument();
        argument.setName(name);
        argument.setValue(value);
        argument.setSpread(spread);

        return argument;
    }

    @Test
    void saysWhenThereAreMoreUsagesThanFit() {
        final var found = repository.findUsages(scanId, classId("order.service:OrderService"), 1, 2).orElseThrow();

        assertThat(found.usages()).hasSize(2);
        assertThat(found.truncated()).isTrue();
    }

    @Test
    void findsNoNodeTheScanDoesNotHave() {
        assertThat(repository.findUsages(scanId, "missing", 1, 100)).isEmpty();
        assertThat(repository.findUsages(Scan.ScanId.generate(), classId("order:Order"), 1, 100)).isEmpty();
    }

    @Test
    void drawsTheGraphWithoutWhatBodiesUse() {
        final var ngOnInit = classId("order-list.component:OrderListComponent")
            + "|method:" + ORDERS + "order-list.component:OrderListComponent.ngOnInit";

        final var graph = new ScanGraphRepositoryImpl(driver).findNeighbourhood(scanId, ngOnInit, 1, 500).orElseThrow();

        assertThat(graph.edges()).extracting(ScanGraph.Edge::type)
            .doesNotContain("CALLS", "INSTANTIATES", "READS", "WRITES", "USES");
        // Only its class, not the methods and fields its body uses
        assertThat(graph.nodes()).extracting(ScanGraph.Node::type, ScanGraph.Node::name)
            .contains(tuple("Class", "OrderListComponent"))
            .doesNotContain(tuple("Method", "list"), tuple("Class", "Totals"));
    }

    @Test
    void drawsWhatBodiesUseWhenItIsAskedFor() {
        final var ngOnInit = classId("order-list.component:OrderListComponent")
            + "|method:" + ORDERS + "order-list.component:OrderListComponent.ngOnInit";
        final var graphs = new ScanGraphRepositoryImpl(driver);

        // It is a layer of the scan, though not shown without asking
        assertThat(graphs.findNeighbourhood(scanId, ngOnInit, 1, 500).orElseThrow().layers())
            .extracting(ScanGraph.Layer::id)
            .contains("result", "usages");

        final var graph = graphs.findNeighbourhood(scanId, ngOnInit, 1, 500, java.util.Set.of("result", "usages")).orElseThrow();

        assertThat(graph.nodes()).extracting(ScanGraph.Node::type, ScanGraph.Node::name)
            .contains(tuple("Method", "list"), tuple("Class", "Totals"), tuple("Field", "orders"));
        // Once for both places it constructs Totals, which it counts
        assertThat(graph.edges())
            .filteredOn(edge -> edge.type().equals("INSTANTIATES"))
            .extracting(edge -> edge.properties().get("places"))
            .containsExactly(2);
        assertThat(graph.edges()).extracting(ScanGraph.Edge::type).contains("CALLS", "READS", "WRITES");

        // Alone, it is drawn on every layer
        assertThat(graphs.findNeighbourhood(scanId, ngOnInit, 1, 500, java.util.Set.of("usages")).orElseThrow().nodes())
            .extracting(ScanGraph.Node::type, ScanGraph.Node::name)
            .contains(tuple("Class", "OrderListComponent"), tuple("Method", "list"));
    }

    private String classId(final String qualifiedName) {
        return scanId.id() + "|.|.|class:" + ORDERS + qualifiedName;
    }

    private static Method method(final ScanResult result, final String qualifiedName) {
        return result.getModules().stream()
            .flatMap(module -> module.getFiles().stream())
            .flatMap(file -> file.getClasses().stream())
            .flatMap(type -> type.getMethods().stream())
            .filter(method -> qualifiedName.equals(method.getQualifiedName()))
            .findFirst()
            .orElseThrow();
    }

    private static Call call(final String target, final Call.Kind kind, final int line) {
        final var call = new Call();
        call.setTarget(target);
        call.setKind(kind);
        call.setStartLine(line);

        return call;
    }
}
