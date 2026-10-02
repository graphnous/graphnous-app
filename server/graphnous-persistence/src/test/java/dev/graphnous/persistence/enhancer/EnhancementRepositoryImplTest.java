package dev.graphnous.persistence.enhancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.enhancer.RuleOutcome;
import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.result.ScanResultRepositoryImpl;
import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;
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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applies enhancer rules to a real scan graph: the orders fixture stored as
 * two targets, a Java backend and a TypeScript copy of it as frontend, each
 * with the classes Order, Entity and Identified. Runs against Neo4j in a
 * container; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class EnhancementRepositoryImplTest {

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static final ObjectMapper JSON = new ObjectMapper();

    private static Driver driver;

    private EnhancementRepositoryImpl repository;

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
    void storeScan() {
        query("MATCH (n) DETACH DELETE n", Map.of());

        scanId = new Scan.ScanId(UUID.randomUUID());
        query("CREATE (:Scan {id: $id})", Map.of("id", scanId.id().toString()));

        new ScanResultRepositoryImpl(driver).save(scanId, List.of(
            orders("backend", ScanTarget.Language.JAVA),
            orders("frontend", ScanTarget.Language.TYPESCRIPT)
        ));

        repository = new EnhancementRepositoryImpl(driver);
    }

    @Test
    void labelsAndDescribesTheMatchedNodes() {
        final var outcome = apply("backend", """
            {
              "id": "rest-resources",
              "match": {
                "kind": "Class",
                "where": {
                  "related": {
                    "type": "ANNOTATED_WITH",
                    "to": { "kind": "Annotation", "where": { "property": "name", "equals": "Path" } }
                  }
                }
              },
              "actions": {
                "addLabels": ["RestResource"],
                "setProperties": {
                  "endpoint": { "template": "${node.name}Endpoint" },
                  "className": { "fromProperty": "qualifiedName" },
                  "style": "rest"
                }
              }
            }
            """);

        assertThat(outcome.matched()).isEqualTo(1);
        assertThat(outcome.labelsAdded()).isEqualTo(1);

        final var order = single("""
            MATCH (c:Class:Acme_RestResource {scanId: $scanId})
            RETURN c.name AS name, c.Acme_endpoint AS endpoint, c.Acme_className AS className,
                   c.Acme_style AS style, c.Acme_enhancedBy AS enhancedBy
            """);

        assertThat(order).containsEntry("name", "Order")
            .containsEntry("endpoint", "OrderEndpoint")
            .containsEntry("className", "com.example.Order")
            .containsEntry("style", "rest")
            .containsEntry("enhancedBy", List.of("io.acme.test/rest-resources"));
    }

    @Test
    void limitsATargetScopedRuleToItsTarget() {
        final var outcome = apply("backend", """
            { "id": "classes", "match": { "kind": "Class" }, "actions": { "addLabels": ["Seen"] } }
            """);

        assertThat(outcome.matched()).isEqualTo(3);
        assertThat(count("MATCH (c:Class:Acme_Seen {scanId: $scanId}) RETURN count(c)")).isEqualTo(3);
        assertThat(count("MATCH (c:Class:Acme_Seen {scanId: $scanId}) WHERE c.targetId ENDS WITH '|frontend' "
            + "RETURN count(c)")).isZero();
    }

    @Test
    void connectsNodesAcrossLanguagesInAScanScopedRule() {
        final var outcome = apply(null, """
            {
              "id": "same-class",
              "match": { "kind": "Class", "language": "java" },
              "actions": {
                "addRelationships": [{
                  "type": "SAME_AS",
                  "target": {
                    "kind": "Class",
                    "language": "typescript",
                    "where": { "property": "qualifiedName", "equals": { "source": "qualifiedName" } }
                  },
                  "properties": { "reason": { "template": "${node.name} = ${target.name}" } }
                }]
              }
            }
            """);

        assertThat(outcome.matched()).isEqualTo(3);
        assertThat(outcome.relationshipsCreated()).isEqualTo(3);

        final var link = single("""
            MATCH (java:Class {qualifiedName: 'com.example.Order'})-[r:Acme_SAME_AS]->(ts:Class)
            RETURN java.targetId AS from, ts.targetId AS to, r.Acme_reason AS reason, r.Acme_enhancedBy AS enhancedBy
            """);

        assertThat((String) link.get("from")).endsWith("|backend");
        assertThat((String) link.get("to")).endsWith("|frontend");
        assertThat(link).containsEntry("reason", "Order = Order")
            .containsEntry("enhancedBy", "io.acme.test/same-class");
    }

    @Test
    void skipsNodesWithoutExactlyOneTarget() {
        final var outcome = apply("backend", """
            {
              "id": "one-annotation",
              "match": { "kind": "Class" },
              "actions": {
                "addRelationships": [{
                  "type": "MARKED_BY",
                  "cardinality": "exactlyOne",
                  "target": { "kind": "Annotation", "where": { "property": "name", "in": ["Path", "Table"] } }
                }]
              }
            }
            """);

        // Two annotations match for every class, so none is connected
        assertThat(outcome.matched()).isEqualTo(3);
        assertThat(outcome.skipped()).isEqualTo(3);
        assertThat(outcome.relationshipsCreated()).isZero();
    }

    @Test
    void connectsToOnlyTheFirstTarget() {
        final var outcome = apply("backend", """
            {
              "id": "first-annotation",
              "match": { "kind": "Class", "where": { "property": "name", "equals": "Entity" } },
              "actions": {
                "addRelationships": [{
                  "type": "MARKED_BY",
                  "cardinality": "first",
                  "target": { "kind": "Annotation", "where": { "property": "name", "in": ["Path", "Table"] } }
                }]
              }
            }
            """);

        assertThat(outcome.relationshipsCreated()).isEqualTo(1);
    }

    @Test
    void changesNothingWhenAppliedAgain() {
        final var rule = """
            {
              "id": "entities",
              "match": { "kind": "Class", "where": { "property": "name", "equals": "Order" } },
              "actions": {
                "addLabels": ["Entity"],
                "addRelationships": [{
                  "type": "EXTENDS_ENTITY",
                  "target": { "kind": "Class", "where": { "property": "name", "equals": "Entity" } }
                }]
              }
            }
            """;

        apply("backend", rule);
        final var again = apply("backend", rule);

        assertThat(again.labelsAdded()).isZero();
        assertThat(again.relationshipsCreated()).isZero();
        assertThat(count("MATCH (:Class {scanId: $scanId})-[r:Acme_EXTENDS_ENTITY]->() RETURN count(r)")).isEqualTo(1);
        assertThat(single("MATCH (c:Acme_Entity {scanId: $scanId}) RETURN c.Acme_enhancedBy AS enhancedBy"))
            .containsEntry("enhancedBy", List.of("io.acme.test/entities"));
    }

    @Test
    void matchesNodesWithoutNeighbours() {
        final var outcome = apply("backend", """
            {
              "id": "unannotated",
              "match": {
                "kind": "Class",
                "where": { "related": { "type": "ANNOTATED_WITH", "count": { "min": 0, "max": 0 } } }
              },
              "actions": { "addLabels": ["Plain"] }
            }
            """);

        assertThat(outcome.matched()).isEqualTo(2);
        assertThat(count("MATCH (c:Acme_Plain {scanId: $scanId}) WHERE c.name IN ['Entity', 'Identified'] RETURN count(c)"))
            .isEqualTo(2);
    }

    @Test
    void combinesConditionsAndIgnoresCaseOnRequest() {
        final var outcome = apply("backend", """
            {
              "id": "orders",
              "match": {
                "kind": "Class",
                "where": {
                  "all": [
                    { "property": "name", "matches": "order", "caseSensitive": false },
                    { "not": { "property": "kind", "equals": "INTERFACE" } },
                    { "any": [
                      { "property": "qualifiedName", "startsWith": "com.example." },
                      { "hasLabel": "Missing" }
                    ] }
                  ]
                }
              },
              "actions": { "addLabels": ["Order"] }
            }
            """);

        assertThat(outcome.matched()).isEqualTo(1);
    }

    @Test
    void readsAnnotationArguments() {
        final var outcome = apply("backend", """
            {
              "id": "paths",
              "match": { "kind": "Annotation", "where": { "property": "arguments.value", "equals": "/orders" } },
              "actions": { "setProperties": { "path": { "fromProperty": "arguments.value" } } }
            }
            """);

        assertThat(outcome.matched()).isEqualTo(1);
        assertThat(single("MATCH (a:Annotation {scanId: $scanId}) WHERE a.Acme_path IS NOT NULL RETURN a.Acme_path AS path"))
            .containsEntry("path", "/orders");
    }

    @Test
    void refusesAKindThatIsNotPartOfAScan() {
        // Dependencies are shared between scans; the manifest model already
        // refuses them as the kind a rule matches, and conditions refuse them too
        assertThatThrownBy(() -> apply("backend", """
            {
              "id": "libraries",
              "match": { "kind": "Module", "where": { "related": { "type": "DEPENDS_ON", "to": { "kind": "Dependency" } } } },
              "actions": { "addLabels": ["Library"] }
            }
            """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rule libraries of enhancer io.acme.test")
            .hasMessageContaining("unknown node kind Dependency");
    }

    @Test
    void refusesNamesThatWouldChangeTheQuery() {
        assertThatThrownBy(() -> apply("backend", """
            { "id": "evil", "match": { "kind": "Class" }, "actions": { "addLabels": ["X` DETACH DELETE n //"] } }
            """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid label");

        assertThat(count("MATCH (c:Class {scanId: $scanId}) RETURN count(c)")).isEqualTo(6);
    }

    private RuleOutcome apply(
        final String targetPath,
        final String rule
    ) {
        final var manifest = read("""
            {
              "schemaVersion": "1.0", "id": "io.acme.test", "name": "Test", "version": "1.0.0",
              "namespace": "Acme", "scope": "%s",
              "rules": [%s]
            }
            """.formatted(targetPath == null ? "scan" : "target", rule));

        return repository.apply(scanId, targetPath, manifest, manifest.getRules().getFirst());
    }

    private static ScanResultSchema orders(
        final String path,
        final ScanTarget.Language language
    ) {
        try (final var json = EnhancementRepositoryImplTest.class.getResourceAsStream("/scan-result.json")) {
            final var result = JSON.readValue(json, ScanResultSchema.class);
            result.getTarget().setPath(path);
            result.getTarget().setLanguage(language);

            return result;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static EnhancerManifestSchema read(final String json) {
        try {
            return JSON.readValue(json, EnhancerManifestSchema.class);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private long count(final String cypher) {
        try (final var session = driver.session()) {
            return session.run(cypher, Map.of("scanId", scanId.id().toString())).single().get(0).asLong();
        }
    }

    private Map<String, Object> single(final String cypher) {
        try (final var session = driver.session()) {
            return session.run(cypher, Map.of("scanId", scanId.id().toString())).single().asMap();
        }
    }

    private static void query(final String cypher, final Map<String, Object> parameters) {
        try (final var session = driver.session()) {
            session.run(cypher, parameters).consume();
        }
    }
}
