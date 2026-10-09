package dev.graphnous.persistence.scan.result;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.enhancer.angular.AngularEnhancer;
import dev.graphnous.persistence.scan.graph.ScanGraphRepositoryImpl;
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
 * What the Angular enhancer adds to what the TypeScript scanner found in an
 * Angular application, stored with the scan's graph, in Neo4j in a
 * container; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class AngularEnhancementsTest {

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static Driver driver;

    private final Scan.ScanId scanId = new Scan.ScanId(UUID.randomUUID());

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

        try (final var json = AngularEnhancementsTest.class.getResourceAsStream("/scan-result-angular.json")) {
            result = new ObjectMapper().readValue(json, ScanResult.class);
        }

        try (final var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
            session.run("CREATE (:Scan {id: $id})", Map.of("id", scanId.id().toString())).consume();
        }

        new ScanResultRepositoryImpl(driver).save(scanId, List.of(result));
        new EnhancementRepositoryImpl(driver).save(scanId, List.of(new AngularEnhancer().enhance(result)));
    }

    @Test
    void linksTheComponentsServicesDirectivesAndModulesToTheirClasses() {
        try (final var session = driver.session()) {
            final var nodes = session.run("""
                    MATCH (:Scan {id: $scanId})-[:HAS_ENHANCEMENT]->(angular:Enhancement {name: 'angular', version: '1.0.0'})
                    MATCH (angular)-[:ADDED]->(node:ENHANCED)<-[:ENHANCE]-(class:Class {scanId: $scanId})
                    RETURN class.name AS class, [label IN labels(node) WHERE label <> 'ENHANCED'] AS labels
                    ORDER BY class
                    """,
                Map.of("scanId", scanId.id().toString())
            ).list(record -> tuple(record.get("class").asString(), record.get("labels").asList(value -> value.asString())));

            assertThat(nodes).containsExactly(
                tuple("AppComponent", List.of("Component")),
                tuple("AppModule", List.of("NgModule")),
                tuple("HighlightDirective", List.of("Directive")),
                tuple("InvoiceService", List.of("Service")),
                tuple("OrderListComponent", List.of("Component")),
                tuple("OrderService", List.of("Service")),
                tuple("SharedModule", List.of("NgModule"))
            );
        }
    }

    @Test
    void linksTheHttpCallsToTheirMethodsAndServices() {
        try (final var session = driver.session()) {
            final var calls = session.run("""
                    MATCH (service:Service)-[:HAS_HTTP_CALL]->(call:HttpCall {scanId: $scanId})<-[:ENHANCE]-(method:Method)
                    MATCH (:Class {name: 'OrderService'})-[:ENHANCE]->(service)
                    RETURN method.name AS method, call.httpMethod AS httpMethod
                    ORDER BY method
                    """,
                Map.of("scanId", scanId.id().toString())
            ).list(record -> tuple(record.get("method").asString(), record.get("httpMethod").asString()));

            assertThat(calls).containsExactly(
                tuple("create", "POST"),
                tuple("find", "GET"),
                tuple("list", "GET"),
                tuple("remove", "DELETE")
            );
        }
    }

    @Test
    void linksWhatTheComponentInjectsAndImports() {
        try (final var session = driver.session()) {
            final var links = session.run("""
                    MATCH (:Class {scanId: $scanId, name: 'OrderListComponent'})-[:ENHANCE]->(component:Component)
                    MATCH (component)-[link:INJECTS|IMPORTS]->(node)<-[:ENHANCE]-(class:Class)
                    RETURN type(link) AS link, class.name AS class
                    ORDER BY link
                    """,
                Map.of("scanId", scanId.id().toString())
            ).list(record -> tuple(record.get("link").asString(), record.get("class").asString()));

            assertThat(links).containsExactly(
                tuple("IMPORTS", "HighlightDirective"),
                tuple("INJECTS", "OrderService")
            );
        }
    }

    @Test
    void linksWhatTheModuleDeclaresImportsProvidesAndBootstraps() {
        try (final var session = driver.session()) {
            final var links = session.run("""
                    MATCH (:Class {scanId: $scanId, name: 'AppModule'})-[:ENHANCE]->(module:NgModule)
                    MATCH (module)-[link]->(node)<-[:ENHANCE]-(class:Class)
                    RETURN type(link) AS link, class.name AS class
                    ORDER BY link, class
                    """,
                Map.of("scanId", scanId.id().toString())
            ).list(record -> tuple(record.get("link").asString(), record.get("class").asString()));

            assertThat(links).containsExactly(
                tuple("BOOTSTRAPS", "AppComponent"),
                tuple("DECLARES", "AppComponent"),
                tuple("IMPORTS", "OrderListComponent"),
                tuple("IMPORTS", "SharedModule"),
                tuple("PROVIDES", "InvoiceService"),
                tuple("PROVIDES", "OrderService")
            );
        }
    }

    @Test
    void showsTheNodesInTheGraphByTheirOwnLabel() {
        final var focus = scanId.id() + "|.|.|class:src/app/orders/order-list.component:OrderListComponent";

        final var graph = new ScanGraphRepositoryImpl(driver).findNeighbourhood(scanId, focus, 1, 100).orElseThrow();

        assertThat(graph.nodes())
            .filteredOn(node -> node.id().equals(focus + "|component"))
            .singleElement()
            .satisfies(component -> {
                assertThat(component.type()).isEqualTo("Component");
                assertThat(component.properties()).containsEntry("selector", "app-order-list");
            });
    }
}
