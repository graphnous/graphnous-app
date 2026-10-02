package dev.graphnous.persistence.scan.result;

import dev.graphnous.domain.scan.Scan;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against Neo4j in a container; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class ScanResultRepositoryImplTest {

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static Driver driver;

    private ScanResultRepositoryImpl repository;

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
        query("MATCH (n) DETACH DELETE n");

        repository = new ScanResultRepositoryImpl(driver);
    }

    @Test
    void storesTheResultBelowTheScan() {
        final var scanId = createScan();

        repository.save(scanId, List.of(ScanResults.orders()));

        assertThat(count("""
            MATCH (:Scan {id: $scanId})-[:HAS_TARGET]->(:ScanTarget {path: 'backend'})
                  -[:HAS_MODULE]->(module:Module {name: 'orders'})
            RETURN count(module)
            """, scanId)).isEqualTo(1);

        assertThat(count("""
            MATCH (:Module)-[:HAS_FILE]->(:File {path: 'src/main/java/com/example/Order.java'})
                  -[:DECLARES]->(class:Class {qualifiedName: 'com.example.Order'})
            MATCH (:Package {qualifiedName: 'com.example'})-[:CONTAINS]->(class)
            RETURN count(class)
            """, scanId)).isEqualTo(1);

        assertThat(count("MATCH (class:Class {scanId: $scanId}) RETURN count(class)", scanId)).isEqualTo(3);
        assertThat(count("MATCH (:Class)-[:HAS_METHOD]->(method:Method {scanId: $scanId}) RETURN count(method)", scanId)).isEqualTo(2);
        assertThat(count("MATCH (:Class)-[:HAS_FIELD]->(field:Field {scanId: $scanId, type: 'long'}) RETURN count(field)", scanId)).isEqualTo(1);
    }

    @Test
    void linksInheritanceWithinTheScan() {
        final var scanId = createScan();

        repository.save(scanId, List.of(ScanResults.orders()));

        assertThat(count("""
            MATCH (:Class {scanId: $scanId, qualifiedName: 'com.example.Order'})
                  -[:EXTENDS]->(:Class {qualifiedName: 'com.example.Entity'})
            RETURN count(*)
            """, scanId)).isEqualTo(1);

        // Comparable is not part of the scan, so only Identified is linked
        assertThat(count("""
            MATCH (:Class {scanId: $scanId, qualifiedName: 'com.example.Order'})-[:IMPLEMENTS]->(supertype)
            RETURN count(supertype)
            """, scanId)).isEqualTo(1);
    }

    @Test
    void storesAnnotationsWithTheirArguments() {
        final var scanId = createScan();

        repository.save(scanId, List.of(ScanResults.orders()));

        assertThat(count("""
            MATCH (:Class {scanId: $scanId, qualifiedName: 'com.example.Order'})
                  -[:ANNOTATED_WITH]->(path:Annotation {qualifiedName: 'jakarta.ws.rs.Path'})
            WHERE path.`arguments.value` = '/orders'
            RETURN count(path)
            """, scanId)).isEqualTo(1);

        assertThat(count("""
            MATCH (:Field {scanId: $scanId})-[:ANNOTATED_WITH]->(column:Annotation {name: 'Column'})
            WHERE column.`arguments.name` = 'total'
              AND column.`arguments.nullable` = false
              AND column.`arguments.scale` = [1.0, 2.5]
            RETURN count(column)
            """, scanId)).isEqualTo(1);

        assertThat(count("""
            MATCH (:Method {scanId: $scanId})-[annotated:ANNOTATED_WITH {parameter: 'id', parameterIndex: 0}]
                  ->(:Annotation {qualifiedName: 'jakarta.ws.rs.PathParam', `arguments.value`: 'id'})
            RETURN count(annotated)
            """, scanId)).isEqualTo(1);

        assertThat(count("""
            MATCH (:Method {scanId: $scanId})-[annotated:ANNOTATED_WITH]->(:Annotation {name: 'Inject'})
            WHERE annotated.parameter IS NULL
            RETURN count(annotated)
            """, scanId)).isEqualTo(1);

        assertThat(count("MATCH (annotation:Annotation {scanId: $scanId}) RETURN count(annotation)", scanId))
            .isEqualTo(7);
    }

    @Test
    void sharesDependenciesBetweenScans() {
        final var first = createScan();
        final var second = createScan();

        repository.save(first, List.of(ScanResults.orders()));
        repository.save(second, List.of(ScanResults.orders()));

        assertThat(count("""
            MATCH (dependency:Dependency {coordinates: 'org.slf4j:slf4j-api:2.0.18'})
            RETURN count(dependency)
            """, first)).isEqualTo(1);

        assertThat(count("""
            MATCH (:Module)-[uses:DEPENDS_ON {scope: 'compile'}]->(:Dependency {name: 'org.slf4j:slf4j-api'})
            RETURN count(uses)
            """, first)).isEqualTo(2);
    }

    @Test
    void replacesAnEarlierResultOfTheScan() {
        final var scanId = createScan();

        repository.save(scanId, List.of(ScanResults.orders()));
        repository.save(scanId, List.of(ScanResults.orders()));

        assertThat(count("MATCH (class:Class {scanId: $scanId}) RETURN count(class)", scanId)).isEqualTo(3);
        assertThat(count("MATCH (:Scan {id: $scanId})-[:HAS_TARGET]->(target) RETURN count(target)", scanId)).isEqualTo(1);
    }

    @Test
    void leavesOtherScansAlone() {
        final var first = createScan();
        final var second = createScan();

        repository.save(first, List.of(ScanResults.orders()));
        repository.save(second, List.of(ScanResults.orders()));
        repository.save(second, List.of());

        assertThat(count("MATCH (class:Class {scanId: $scanId}) RETURN count(class)", first)).isEqualTo(3);
        assertThat(count("MATCH (class:Class {scanId: $scanId}) RETURN count(class)", second)).isZero();
    }

    @Test
    void deletesTheResultOfTheScanOnly() {
        final var deleted = createScan();
        final var kept = createScan();

        repository.save(deleted, List.of(ScanResults.orders()));
        repository.save(kept, List.of(ScanResults.orders()));

        repository.delete(deleted);

        assertThat(count("MATCH (node {scanId: $scanId}) RETURN count(node)", deleted)).isZero();
        assertThat(count("MATCH (node {scanId: $scanId}) RETURN count(node)", kept)).isEqualTo(18);

        // The scan itself belongs to the scan repository
        assertThat(count("MATCH (scan:Scan {id: $scanId}) RETURN count(scan)", deleted)).isEqualTo(1);
    }

    @Test
    void deletesDependenciesNoScanUsesAnyMore() {
        final var first = createScan();
        final var second = createScan();

        repository.save(first, List.of(ScanResults.orders()));
        repository.save(second, List.of(ScanResults.orders()));

        repository.delete(first);

        assertThat(count("MATCH (dependency:Dependency) RETURN count(dependency)", first)).isEqualTo(2);

        repository.delete(second);

        assertThat(count("MATCH (dependency:Dependency) RETURN count(dependency)", first)).isZero();
    }

    @Test
    void deletesDependenciesAReplacedResultNoLongerUses() {
        final var scanId = createScan();

        repository.save(scanId, List.of(ScanResults.orders()));

        final var withoutDependencies = ScanResults.orders();
        withoutDependencies.getModules().forEach(module -> module.getDependencies().clear());

        repository.save(scanId, List.of(withoutDependencies));

        assertThat(count("MATCH (dependency:Dependency) RETURN count(dependency)", scanId)).isZero();
    }

    @Test
    void deletingAScanWithoutResultsDoesNothing() {
        final var scanId = createScan();

        repository.delete(scanId);

        assertThat(count("MATCH (scan:Scan {id: $scanId}) RETURN count(scan)", scanId)).isEqualTo(1);
    }

    @Test
    void savesScansThatFinishAtTheSameTime() throws Exception {
        // Start without schema, as a fresh database would
        try (final var session = driver.session()) {
            session.run("SHOW CONSTRAINTS YIELD name").list(record -> record.get("name").asString())
                .forEach(name -> query("DROP CONSTRAINT " + name));
            session.run("SHOW INDEXES YIELD name, type WHERE type <> 'LOOKUP'").list(record -> record.get("name").asString())
                .forEach(name -> query("DROP INDEX " + name + " IF EXISTS"));
        }

        final var fresh = new ScanResultRepositoryImpl(driver);
        final var scans = List.of(createScan(), createScan(), createScan(), createScan());

        try (final var executor = Executors.newFixedThreadPool(scans.size())) {
            final var start = new CountDownLatch(1);
            final var saves = scans.stream()
                .map(scanId -> executor.submit(() -> {
                    start.await();
                    fresh.save(scanId, List.of(ScanResults.orders()));
                    return null;
                }))
                .toList();

            start.countDown();

            for (final var save : saves) {
                save.get();
            }
        }

        for (final var scanId : scans) {
            assertThat(count("MATCH (class:Class {scanId: $scanId}) RETURN count(class)", scanId)).isEqualTo(3);
        }
    }

    @Test
    void failsForAScanThatIsNotInTheGraph() {
        final var missing = new Scan.ScanId(UUID.randomUUID());

        assertThatThrownBy(() -> repository.save(missing, List.of(ScanResults.orders())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is not in the graph");
    }

    private static Scan.ScanId createScan() {
        final var scanId = new Scan.ScanId(UUID.randomUUID());

        query("CREATE (:Scan {id: $id})", Map.of("id", scanId.id().toString()));

        return scanId;
    }

    private static long count(final String cypher, final Scan.ScanId scanId) {
        try (final var session = driver.session()) {
            return session.run(cypher, Map.of("scanId", scanId.id().toString()))
                .single().get(0).asLong();
        }
    }

    private static void query(final String cypher) {
        query(cypher, Map.of());
    }

    private static void query(final String cypher, final Map<String, Object> parameters) {
        try (final var session = driver.session()) {
            session.run(cypher, parameters).consume();
        }
    }
}
