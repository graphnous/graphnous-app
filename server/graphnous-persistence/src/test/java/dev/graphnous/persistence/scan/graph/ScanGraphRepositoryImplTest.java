package dev.graphnous.persistence.scan.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.scan.Scan;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Reads what ScanResultRepositoryImpl stores, in Neo4j in a container;
 * skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class ScanGraphRepositoryImplTest {

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static Driver driver;

    private ScanGraphRepositoryImpl repository;

    private Scan.ScanId scanId;

    @BeforeAll
    static void connect() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    static void disconnect() {
        driver.close();
    }

    @BeforeEach
    void storeAScan() throws IOException {
        try (final var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }

        scanId = createScan();

        new ScanResultRepositoryImpl(driver).save(scanId, List.of(orders()));

        repository = new ScanGraphRepositoryImpl(driver);
    }

    @Test
    void outlinesTheTargetsAndModules() {
        assertThat(repository.getOverview(scanId).targets()).containsExactly(new ScanGraph.Target(
            "backend",
            "JAVA",
            "25",
            "MAVEN",
            List.of(new ScanGraph.Module("orders", "orders", 2, 1, 3, 2, 2))
        ));
    }

    @Test
    void countsFunctionsOutsideClassesAsMethods() throws IOException {
        final var shop = createScan();
        new ScanResultRepositoryImpl(driver).save(shop, List.of(read("/scan-result-python.json")));

        assertThat(repository.getOverview(shop).targets())
            .flatExtracting(ScanGraph.Target::modules)
            .containsExactly(new ScanGraph.Module("shop", ".", 2, 1, 1, 3, 0));
    }

    @Test
    void findsDecoratedFunctionsOutsideClasses() throws IOException {
        final var shop = createScan();
        new ScanResultRepositoryImpl(driver).save(shop, List.of(read("/scan-result-python.json")));

        assertThat(repository.findAnnotated(shop, "cache", 10)).containsExactly(new ScanGraph.AnnotatedElement(
            "FUNCTION",
            null,
            "app.orders.total",
            new ScanGraph.Annotation("cache", "functools.cache", null, null)
        ));
    }

    @Test
    void outlinesNothingForAScanWithoutResults() {
        assertThat(repository.getOverview(createScan()).targets()).isEmpty();
    }

    @Test
    void findsClassesByPartOfTheirName() {
        assertThat(repository.findClasses(scanId, "ENTITY", 10)).containsExactly(new ScanGraph.ClassSummary(
            "Entity", "com.example.Entity", "CLASS", "orders", "src/main/java/com/example/Entity.java"
        ));

        assertThat(repository.findClasses(scanId, "", 2))
            .extracting(ScanGraph.ClassSummary::qualifiedName)
            .containsExactly("com.example.Entity", "com.example.Identified");
    }

    @Test
    void getsAClassWithItsMembers() {
        final var order = repository.findClass(scanId, "com.example.Order").orElseThrow();

        assertThat(order.kind()).isEqualTo("CLASS");
        assertThat(order.modifiers()).containsExactly("PUBLIC", "FINAL");
        assertThat(order.module()).isEqualTo("orders");
        assertThat(order.file()).isEqualTo("src/main/java/com/example/Order.java");
        assertThat(order.packageName()).isEqualTo("com.example");
        assertThat(order.superClass()).isEqualTo("com.example.Entity<java.util.UUID>");
        assertThat(order.annotations())
            .extracting(ScanGraph.Annotation::name)
            .containsExactly("Deprecated", "Path", "Table");
        assertThat(order.annotations().get(1).arguments()).isEqualTo("{\"value\":\"/orders\"}");

        assertThat(order.methods()).extracting(ScanGraph.Method::name).containsExactly("Order", "compareTo");
        assertThat(order.methods().getFirst().parameterTypes()).containsExactly("java.util.UUID");
        assertThat(order.methods().getFirst().annotations())
            .containsExactly(
                new ScanGraph.Annotation("Inject", "jakarta.inject.Inject", null, null),
                new ScanGraph.Annotation("PathParam", "jakarta.ws.rs.PathParam", "{\"value\":\"id\"}", "id")
            );

        assertThat(order.fields()).extracting(ScanGraph.Field::name).containsExactly("total");
        assertThat(order.fields().getFirst().annotations()).extracting(ScanGraph.Annotation::name).containsExactly("Column");
    }

    @Test
    void getsTheSubtypesOfAClass() {
        assertThat(repository.findClass(scanId, "com.example.Identified").orElseThrow().subtypes())
            .containsExactly("com.example.Order");
    }

    @Test
    void getsNoClassThatIsNotInTheScan() {
        assertThat(repository.findClass(scanId, "com.example.Missing")).isEmpty();
    }

    @Test
    void findsWhatIsAnnotatedBySimpleOrQualifiedName() {
        assertThat(repository.findAnnotated(scanId, "path", 10)).containsExactly(new ScanGraph.AnnotatedElement(
            "CLASS",
            "com.example.Order",
            null,
            new ScanGraph.Annotation("Path", "jakarta.ws.rs.Path", "{\"value\":\"/orders\"}", null)
        ));

        assertThat(repository.findAnnotated(scanId, "jakarta.persistence.Column", 10))
            .extracting(ScanGraph.AnnotatedElement::kind, ScanGraph.AnnotatedElement::member)
            .containsExactly(tuple("FIELD", "total"));
    }

    @Test
    void listsTheDependenciesOfEachModule() {
        assertThat(repository.findDependencies(scanId)).containsExactly(
            new ScanGraph.Dependency("orders", "org.junit.jupiter:junit-jupiter", null, "test"),
            new ScanGraph.Dependency("orders", "org.slf4j:slf4j-api", "2.0.18", "compile")
        );
    }

    @Test
    void showsTheTargetsAndModulesAroundTheScan() {
        final var scan = scanId.id().toString();
        final var target = scan + "|backend";
        final var module = target + "|orders";

        final var graph = repository.findNeighbourhood(scanId, null, 2, 500).orElseThrow();

        assertThat(graph.focus()).isEqualTo(scan);
        assertThat(graph.nodes())
            .extracting(ScanGraph.Node::id, ScanGraph.Node::type, ScanGraph.Node::name, ScanGraph.Node::depth)
            .containsExactly(
                tuple(scan, "Scan", "Scan", 0),
                tuple(target, "ScanTarget", "backend", 1),
                tuple(module, "Module", "orders", 2)
            );
        assertThat(graph.nodes().get(1).properties())
            .containsEntry("language", "JAVA")
            .doesNotContainKeys("id", "scanId", "targetId");
        assertThat(graph.edges())
            .extracting(ScanGraph.Edge::source, ScanGraph.Edge::target, ScanGraph.Edge::type)
            .containsExactlyInAnyOrder(
                tuple(scan, target, "HAS_TARGET"),
                tuple(target, module, "HAS_MODULE")
            );
        assertThat(graph.truncated()).isFalse();
    }

    @Test
    void showsTheNeighboursOfAClass() {
        final var module = scanId.id() + "|backend|orders";
        final var order = module + "|class:com.example.Order";

        final var graph = repository.findNeighbourhood(scanId, order, 1, 500).orElseThrow();

        assertThat(graph.focus()).isEqualTo(order);
        assertThat(graph.nodes()).first().extracting(ScanGraph.Node::id).isEqualTo(order);
        assertThat(graph.nodes())
            .filteredOn(node -> node.depth() == 1)
            .extracting(ScanGraph.Node::type, ScanGraph.Node::name)
            .containsExactlyInAnyOrder(
                tuple("File", "src/main/java/com/example/Order.java"),
                tuple("Package", "com.example"),
                tuple("Method", "Order"),
                tuple("Method", "compareTo"),
                tuple("Field", "total"),
                tuple("Annotation", "@Deprecated"),
                tuple("Annotation", "@Path"),
                tuple("Annotation", "@Table"),
                tuple("Class", "Entity"),
                tuple("Class", "Identified")
            );
        assertThat(graph.edges())
            .extracting(ScanGraph.Edge::source, ScanGraph.Edge::target, ScanGraph.Edge::type)
            .contains(
                tuple(order, module + "|class:com.example.Entity", "EXTENDS"),
                tuple(order, module + "|class:com.example.Identified", "IMPLEMENTS")
            );
    }

    @Test
    void keepsTheNearestNodesWhenThereAreTooMany() {
        final var pkg = scanId.id() + "|backend|orders|package:com.example";

        final var graph = repository.findNeighbourhood(scanId, pkg, 2, 3).orElseThrow();

        assertThat(graph.nodes()).hasSize(3);
        assertThat(graph.nodes()).extracting(ScanGraph.Node::depth).containsExactly(0, 1, 1);
        assertThat(graph.truncated()).isTrue();
    }

    @Test
    void showsOnlyTheModulesOfTheScanThatUseADependency() throws IOException {
        final var other = createScan();
        new ScanResultRepositoryImpl(driver).save(other, List.of(orders()));

        final var graph = repository.findNeighbourhood(scanId, "dependency:org.slf4j:slf4j-api:2.0.18", 1, 500)
            .orElseThrow();

        assertThat(graph.nodes())
            .extracting(ScanGraph.Node::id)
            .containsExactly("dependency:org.slf4j:slf4j-api:2.0.18", scanId.id() + "|backend|orders");
        assertThat(graph.edges())
            .extracting(ScanGraph.Edge::type, ScanGraph.Edge::properties)
            .containsExactly(tuple("DEPENDS_ON", Map.of("scope", "compile")));
    }

    @Test
    void findsNoNodeOfAnotherScan() throws IOException {
        final var other = createScan();
        new ScanResultRepositoryImpl(driver).save(other, List.of(orders()));

        assertThat(repository.findNeighbourhood(scanId, other.id() + "|backend", 1, 500)).isEmpty();
        assertThat(repository.findNeighbourhood(scanId, "missing", 1, 500)).isEmpty();
    }

    @Test
    void showsOnlyTheScanWhenItHasNoResults() {
        final var empty = createScan();

        assertThat(repository.findNeighbourhood(empty, null, 2, 500).orElseThrow().nodes())
            .extracting(ScanGraph.Node::type)
            .containsExactly("Scan");
    }

    private static ScanResult orders() throws IOException {
        return read("/scan-result.json");
    }

    private static ScanResult read(final String resource) throws IOException {
        try (final var json = ScanGraphRepositoryImplTest.class.getResourceAsStream(resource)) {
            return new ObjectMapper().readValue(json, ScanResult.class);
        }
    }

    private static Scan.ScanId createScan() {
        final var scanId = Scan.ScanId.generate();

        try (final var session = driver.session()) {
            session.run("CREATE (:Scan {id: $id})", Map.of("id", scanId.id().toString())).consume();
        }

        return scanId;
    }
}
