package dev.graphnous.persistence.scan.result;

import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.model.ScanResultSchema;
import org.neo4j.driver.Driver;
import org.neo4j.driver.TransactionContext;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores scan results as a graph below the scan:
 * <pre>
 * (Scan)-[:HAS_TARGET]->(ScanTarget)-[:HAS_MODULE]->(Module)
 * (Module)-[:HAS_FILE]->(File)-[:DECLARES]->(Class)
 * (Module)-[:HAS_PACKAGE]->(Package)-[:CONTAINS]->(Class)
 * (Class)-[:HAS_METHOD]->(Method)
 * (Class)-[:HAS_FIELD]->(Field)
 * (Class)-[:EXTENDS|IMPLEMENTS]->(Class)
 * (Class|Method|Field)-[:ANNOTATED_WITH]->(Annotation)
 * (Module)-[:DEPENDS_ON {scope}]->(Dependency)
 * </pre>
 * Every node except {@code Dependency} belongs to one scan and carries its
 * {@code scanId}. Dependencies are shared between scans, so it is possible
 * to ask which projects use a library, and are deleted once no scan uses
 * them any more. Inheritance is only linked to
 * classes found in the same scan; the declared names of all supertypes are
 * kept on the class. The annotations of a method's parameters are linked
 * to the method, with the {@code parameter} and {@code parameterIndex} on
 * the relationship; see {@link ScanResultGraph} for the arguments.
 */
@Repository
public class ScanResultRepositoryImpl implements ScanResultRepository {

    private static final int BATCH_SIZE = 2_000;

    private static final List<String> SCAN_LABELS = List.of(
        "ScanTarget", "Module", "File", "Package", "Class", "Method", "Field", "Annotation"
    );

    private final Driver driver;

    private volatile boolean schemaCreated;

    public ScanResultRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public void save(
        final Scan.ScanId scanId,
        final List<ScanResultSchema> results
    ) {
        createSchema();

        final var id = scanId.id().toString();
        final var graph = ScanResultGraph.of(id, results);

        try (final var session = driver.session()) {
            session.executeWriteWithoutResult(tx -> {
                requireScan(tx, id);

                final var previousDependencies = delete(tx, id);

                write(tx, id, graph.targets(), """
                    MATCH (scan:Scan {id: $scanId})
                    UNWIND $rows AS row
                    CREATE (target:ScanTarget)
                    SET target = row, target.scanId = $scanId
                    CREATE (scan)-[:HAS_TARGET]->(target)
                    """);

                write(tx, id, graph.modules(), """
                    UNWIND $rows AS row
                    MATCH (target:ScanTarget {id: row.targetId})
                    CREATE (module:Module)
                    SET module = row, module.scanId = $scanId
                    REMOVE module.targetId
                    CREATE (target)-[:HAS_MODULE]->(module)
                    """);

                write(tx, id, graph.files(), """
                    UNWIND $rows AS row
                    MATCH (module:Module {id: row.moduleId})
                    CREATE (file:File)
                    SET file = row, file.scanId = $scanId
                    REMOVE file.moduleId
                    CREATE (module)-[:HAS_FILE]->(file)
                    """);

                write(tx, id, graph.packages(), """
                    UNWIND $rows AS row
                    MATCH (module:Module {id: row.moduleId})
                    CREATE (package:Package)
                    SET package = row, package.scanId = $scanId
                    REMOVE package.moduleId
                    CREATE (module)-[:HAS_PACKAGE]->(package)
                    """);

                write(tx, id, graph.classes(), """
                    UNWIND $rows AS row
                    CREATE (class:Class)
                    SET class = row, class.scanId = $scanId
                    REMOVE class.moduleId
                    """);

                write(tx, id, graph.fileClasses(), """
                    UNWIND $rows AS row
                    MATCH (file:File {id: row.fileId})
                    MATCH (class:Class {id: row.classId})
                    CREATE (file)-[:DECLARES]->(class)
                    """);

                write(tx, id, graph.packageClasses(), """
                    UNWIND $rows AS row
                    MATCH (package:Package {id: row.packageId})
                    MATCH (class:Class {id: row.classId})
                    CREATE (package)-[:CONTAINS]->(class)
                    """);

                write(tx, id, graph.methods(), """
                    UNWIND $rows AS row
                    MATCH (class:Class {id: row.classId})
                    CREATE (method:Method)
                    SET method = row, method.scanId = $scanId
                    REMOVE method.classId
                    CREATE (class)-[:HAS_METHOD]->(method)
                    """);

                write(tx, id, graph.fields(), """
                    UNWIND $rows AS row
                    MATCH (class:Class {id: row.classId})
                    CREATE (field:Field)
                    SET field = row, field.scanId = $scanId
                    REMOVE field.classId
                    CREATE (class)-[:HAS_FIELD]->(field)
                    """);

                writeAnnotations(tx, id, "Class", graph.classAnnotations());
                writeAnnotations(tx, id, "Method", graph.methodAnnotations());
                writeAnnotations(tx, id, "Field", graph.fieldAnnotations());

                write(tx, id, graph.dependencies(), """
                    UNWIND $rows AS row
                    MATCH (module:Module {id: row.moduleId})
                    MERGE (dependency:Dependency {coordinates: row.coordinates})
                    ON CREATE SET dependency.name = row.name, dependency.version = row.version
                    CREATE (module)-[:DEPENDS_ON {scope: row.scope}]->(dependency)
                    """);

                write(tx, id, graph.extendsTypes(), """
                    UNWIND $rows AS row
                    MATCH (class:Class {id: row.classId})
                    MATCH (supertype:Class {scanId: $scanId, qualifiedName: row.qualifiedName})
                    MERGE (class)-[:EXTENDS]->(supertype)
                    """);

                write(tx, id, graph.implementsTypes(), """
                    UNWIND $rows AS row
                    MATCH (class:Class {id: row.classId})
                    MATCH (supertype:Class {scanId: $scanId, qualifiedName: row.qualifiedName})
                    MERGE (class)-[:IMPLEMENTS]->(supertype)
                    """);

                deleteUnusedDependencies(tx, previousDependencies);
            });
        }
    }

    @Override
    public void delete(final Scan.ScanId scanId) {
        try (final var session = driver.session()) {
            session.executeWriteWithoutResult(tx ->
                deleteUnusedDependencies(tx, delete(tx, scanId.id().toString()))
            );
        }
    }

    private static void writeAnnotations(
        final TransactionContext tx,
        final String scanId,
        final String ownerLabel,
        final List<Map<String, Object>> rows
    ) {
        write(tx, scanId, rows, """
            UNWIND $rows AS row
            MATCH (owner:%s {id: row.ownerId})
            CREATE (annotation:Annotation)
            SET annotation += row.properties, annotation.id = row.id, annotation.scanId = $scanId
            CREATE (owner)-[:ANNOTATED_WITH {parameter: row.parameter, parameterIndex: row.parameterIndex}]->(annotation)
            """.formatted(ownerLabel));
    }

    private static void requireScan(
        final TransactionContext tx,
        final String scanId
    ) {
        final var found = tx.run(
            "MATCH (scan:Scan {id: $scanId}) RETURN count(scan) AS found",
            Map.of("scanId", scanId)
        ).single().get("found").asLong();

        if (found == 0) {
            throw new IllegalStateException("Scan " + scanId + " is not in the graph");
        }
    }

    /**
     * Deletes the nodes of the scan and returns the coordinates of the
     * dependencies it used, which may no longer be used at all.
     */
    private static List<String> delete(
        final TransactionContext tx,
        final String scanId
    ) {
        final var dependencies = tx.run("""
                MATCH (:Module {scanId: $scanId})-[:DEPENDS_ON]->(dependency:Dependency)
                RETURN collect(DISTINCT dependency.coordinates) AS coordinates
                """,
                Map.of("scanId", scanId)
            )
            .single()
            .get("coordinates")
            .asList(value -> value.asString());

        for (final var label : SCAN_LABELS) {
            tx.run(
                "MATCH (node:" + label + " {scanId: $scanId}) DETACH DELETE node",
                Map.of("scanId", scanId)
            ).consume();
        }

        return dependencies;
    }

    private static void deleteUnusedDependencies(
        final TransactionContext tx,
        final List<String> coordinates
    ) {
        tx.run("""
                UNWIND $coordinates AS coordinates
                MATCH (dependency:Dependency {coordinates: coordinates})
                WHERE NOT (dependency)<-[:DEPENDS_ON]-()
                DELETE dependency
                """,
            Map.of("coordinates", coordinates)
        ).consume();
    }

    private static void write(
        final TransactionContext tx,
        final String scanId,
        final List<Map<String, Object>> rows,
        final String query
    ) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            final var batch = rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()));

            tx.run(query, Map.of("scanId", scanId, "rows", batch)).consume();
        }
    }

    /**
     * Ids are unique per label, and scan nodes are looked up by scan; schema
     * changes cannot share a transaction with writes.
     * <p>
     * Scans finishing at the same time would otherwise create the schema
     * concurrently and deadlock on its label locks, so one thread does it
     * while the others wait. Each statement runs as a managed transaction,
     * which the driver retries on such transient errors, e.g. when several
     * servers start at once.
     */
    private void createSchema() {
        if (schemaCreated) {
            return;
        }

        synchronized (this) {
            if (schemaCreated) {
                return;
            }

            final var statements = new ArrayList<String>();

            for (final var label : SCAN_LABELS) {
                final var name = label.toLowerCase();

                statements.add(
                    "CREATE CONSTRAINT " + name + "_id IF NOT EXISTS FOR (n:" + label + ") REQUIRE n.id IS UNIQUE"
                );
                statements.add(
                    "CREATE INDEX " + name + "_scan IF NOT EXISTS FOR (n:" + label + ") ON (n.scanId)"
                );
            }

            statements.add(
                "CREATE INDEX class_scan_qualified_name IF NOT EXISTS FOR (n:Class) ON (n.scanId, n.qualifiedName)"
            );
            statements.add(
                "CREATE CONSTRAINT dependency_coordinates IF NOT EXISTS FOR (n:Dependency) REQUIRE n.coordinates IS UNIQUE"
            );

            try (final var session = driver.session()) {
                for (final var statement : statements) {
                    session.executeWriteWithoutResult(tx -> tx.run(statement).consume());
                }
            }

            schemaCreated = true;
        }
    }
}
