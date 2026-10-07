package dev.graphnous.persistence.scan.result;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

/**
 * Runs against Neo4j in a container; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class EnhancementRepositoryImplTest {

    private static final String ORDER = "backend|orders|class:com.example.Order";
    private static final String ENTITY = "backend|orders|class:com.example.Entity";
    private static final String CONTROLLER = ORDER + "|controller";
    private static final String ENDPOINT = ORDER + "|endpoint:GET /orders";

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static Driver driver;

    private ScanResultRepositoryImpl results;
    private EnhancementRepositoryImpl repository;

    @BeforeAll
    static void connect() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    static void disconnect() {
        driver.close();
    }

    @BeforeEach
    void clear() {
        query("MATCH (n) DETACH DELETE n", Map.of());

        results = new ScanResultRepositoryImpl(driver);
        repository = new EnhancementRepositoryImpl(driver);
    }

    @Test
    void recordsEachEnhancerThatRanOnTheScan() {
        final var scanId = storedScan();

        repository.save(scanId, List.of(
            enhancements("spring", "1.0.0"),
            enhancements("jpa", "2.1.0"),
            // Once for every result it enhanced
            enhancements("spring", "1.0.0")
        ));

        final var enhancers = list("""
            MATCH (:Scan {id: $scanId})-[:HAS_ENHANCEMENT]->(enhancement:Enhancement)
            RETURN enhancement.name AS name, enhancement.version AS version, enhancement.scanId AS scanId
            ORDER BY name
            """, scanId);

        assertThat(enhancers).containsExactly(
            Map.of("name", "jpa", "version", "2.1.0", "scanId", scanId.id().toString()),
            Map.of("name", "spring", "version", "1.0.0", "scanId", scanId.id().toString())
        );
    }

    @Test
    void createsTheNodesWithTheirLabelsAndMetadata() {
        final var scanId = storedScan();

        repository.save(scanId, List.of(enhancements("spring", "1.0.0", new Enhancement(
            List.of(new Node(CONTROLLER, ORDER, List.of("Spring_Controller", "Controller"), Map.of("baseUrl", "/orders"))),
            List.of()
        ))));

        final var controller = single("""
            MATCH (node {id: $id})
            RETURN labels(node) AS labels, node.baseUrl AS baseUrl, node.scanId AS scanId
            """, Map.of("id", scanId.id() + "|" + CONTROLLER));

        assertThat(controller.get("labels")).asInstanceOf(LIST)
            .containsExactlyInAnyOrder("ENHANCED", "Spring_Controller", "Controller");
        assertThat(controller)
            .containsEntry("baseUrl", "/orders")
            .containsEntry("scanId", scanId.id().toString());
    }

    @Test
    void connectsEachNodeToItsSourceAndItsEnhancer() {
        final var scanId = storedScan();

        repository.save(scanId, List.of(enhancements("spring", "1.0.0", new Enhancement(
            List.of(new Node(CONTROLLER, ORDER, List.of("Controller"), Map.of())),
            List.of()
        ))));

        assertThat(count("""
            MATCH (:Class {qualifiedName: 'com.example.Order'})-[:ENHANCE]->(controller:ENHANCED:Controller)
            MATCH (:Enhancement {name: 'spring'})-[:ADDED]->(controller)
            RETURN count(*)
            """)).isEqualTo(1);
    }

    @Test
    void addsANodeWithoutASourceTheScanHas() {
        final var scanId = storedScan();

        repository.save(scanId, List.of(enhancements("spring", "1.0.0", new Enhancement(
            List.of(
                new Node(CONTROLLER, null, List.of("Controller"), Map.of()),
                new Node(ORDER + "|missing", "backend|orders|class:com.example.Missing", List.of("Controller"), Map.of())
            ),
            List.of()
        ))));

        assertThat(count("MATCH (node:ENHANCED) RETURN count(node)")).isEqualTo(2);
        assertThat(count("MATCH ()-[relationship:ENHANCE]->() RETURN count(relationship)")).isZero();
    }

    @Test
    void connectsNodesOfTheResultAndOfEnhancersById() {
        final var scanId = storedScan();

        repository.save(scanId, List.of(enhancements("spring", "1.0.0", new Enhancement(
            List.of(
                new Node(CONTROLLER, ORDER, List.of("Controller"), Map.of()),
                new Node(ENDPOINT, ORDER, List.of("Endpoint"), Map.of())
            ),
            List.of(
                new Relationship(CONTROLLER, ENDPOINT, "HAS_ENDPOINT"),
                new Relationship(ORDER, ENTITY, "PERSISTED_AS")
            )
        ))));

        assertThat(count("MATCH (:Controller)-[:HAS_ENDPOINT]->(:Endpoint) RETURN count(*)")).isEqualTo(1);
        assertThat(count("MATCH (:Class {qualifiedName: 'com.example.Order'})-[:PERSISTED_AS]->(:Class {qualifiedName: 'com.example.Entity'}) RETURN count(*)"))
            .isEqualTo(1);
    }

    @Test
    void leavesOutRelationshipsToNodesTheScanDoesNotHave() {
        final var scanId = storedScan();
        final var other = storedScan();

        repository.save(scanId, List.of(enhancements("spring", "1.0.0", new Enhancement(
            List.of(),
            List.of(
                new Relationship(ORDER, "backend|orders|class:com.example.Missing", "USES"),
                // The ids of another scan's nodes do not reach outside it
                new Relationship(ORDER, other.id() + "|" + ENTITY, "USES")
            )
        ))));

        assertThat(count("MATCH ()-[relationship:USES]->() RETURN count(relationship)")).isZero();
    }

    @Test
    void addsWhatSeveralResultsAddOnce() {
        final var scanId = storedScan();
        final var enhancement = new Enhancement(
            List.of(new Node(CONTROLLER, ORDER, List.of("Controller"), Map.of())),
            List.of(new Relationship(CONTROLLER, ENTITY, "PERSISTS"))
        );

        repository.save(scanId, List.of(
            enhancements("spring", "1.0.0", enhancement),
            enhancements("spring", "1.0.0", enhancement)
        ));

        assertThat(count("MATCH (node:ENHANCED) RETURN count(node)")).isEqualTo(1);
        assertThat(count("MATCH ()-[relationship:ENHANCE|ADDED|PERSISTS]->() RETURN count(relationship)")).isEqualTo(3);
    }

    @Test
    void rejectsAnInvalidNameWithoutChangingTheGraph() {
        final var scanId = storedScan();
        final var classes = count("MATCH (class:Class) RETURN count(class)");

        assertThatThrownBy(() -> repository.save(scanId, List.of(enhancements("spring", "1.0.0", new Enhancement(
            List.of(new Node(CONTROLLER, ORDER, List.of("Controller"), Map.of())),
            List.of(new Relationship(CONTROLLER, ORDER, "SERVES`]->() DETACH DELETE n //"))
        )))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("relationship type");

        assertThat(count("MATCH (node:ENHANCED|Enhancement) RETURN count(node)")).isZero();
        assertThat(count("MATCH (class:Class) RETURN count(class)")).isEqualTo(classes);
    }

    @Test
    void deletesTheEnhancementsWithTheResults() {
        final var scanId = storedScan();

        repository.save(scanId, List.of(enhancements("spring", "1.0.0", new Enhancement(
            List.of(new Node(CONTROLLER, ORDER, List.of("Controller"), Map.of())),
            List.of()
        ))));

        results.delete(scanId);

        assertThat(count("MATCH (node:ENHANCED|Enhancement) RETURN count(node)")).isZero();
    }

    private static Enhancements enhancements(final String name, final String version, final Enhancement... enhancements) {
        return new Enhancements(name, version, List.of(enhancements));
    }

    private Scan.ScanId storedScan() {
        final var scanId = new Scan.ScanId(UUID.randomUUID());

        query("CREATE (:Scan {id: $id})", Map.of("id", scanId.id().toString()));
        results.save(scanId, List.of(ScanResults.orders()));

        return scanId;
    }

    private static Map<String, Object> single(final String cypher, final Map<String, Object> parameters) {
        try (final var session = driver.session()) {
            return session.run(cypher, parameters).single().asMap();
        }
    }

    private static List<Map<String, Object>> list(final String cypher, final Scan.ScanId scanId) {
        try (final var session = driver.session()) {
            return session.run(cypher, Map.of("scanId", scanId.id().toString())).list(record -> record.asMap());
        }
    }

    private static long count(final String cypher) {
        try (final var session = driver.session()) {
            return session.run(cypher).single().get(0).asLong();
        }
    }

    private static void query(final String cypher, final Map<String, Object> parameters) {
        try (final var session = driver.session()) {
            session.run(cypher, parameters).consume();
        }
    }
}
