package dev.graphnous.persistence.scan.classes;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.classes.ScanClass;
import dev.graphnous.application.scan.classes.ScanClassFilter;
import dev.graphnous.application.scan.classes.ScanClassRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.GraphPages;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

import static dev.graphnous.persistence.scan.GraphPages.string;
import static dev.graphnous.persistence.scan.GraphPages.strings;

/**
 * Reads the classes of a scan, as ScanResultRepositoryImpl stores them. A
 * class belongs to its module through its file, its package or both.
 */
@Repository
public class ScanClassRepositoryImpl implements ScanClassRepository {

    private static final Map<String, String> SORTS = Map.of(
        "name", "class.name",
        "qualifiedName", "class.qualifiedName",
        "kind", "class.kind"
    );

    private final Driver driver;

    public ScanClassRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public Page<ScanClass> findClasses(
        final Scan.ScanId scanId,
        final ScanClassFilter filter,
        final PageQuery page
    ) {
        final var parameters = new HashMap<String, Object>();
        parameters.put("scanId", scanId.id().toString());
        parameters.put("search", filter.search());
        parameters.put("module", filter.module());
        parameters.put("kind", filter.kind());

        return GraphPages.page(
            driver,
            """
                MATCH (class:Class {scanId: $scanId})
                WHERE ($search IS NULL
                       OR toLower(class.name) CONTAINS toLower($search)
                       OR toLower(class.qualifiedName) CONTAINS toLower($search))
                  AND ($kind IS NULL OR class.kind = toUpper($kind))
                WITH class, head(COLLECT {
                    MATCH (module:Module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(class)
                    RETURN DISTINCT module.path ORDER BY module.path
                }) AS module
                WHERE $module IS NULL OR module = $module
                """,
            SORTS,
            "class.id",
            """
                class {.*} AS class,
                module,
                [(file:File)-[:DECLARES]->(class) | file.path][0] AS file,
                [(package:Package)-[:CONTAINS]->(class) | package.qualifiedName][0] AS packageName,
                COUNT { (class)-[:HAS_METHOD]->() } AS methods,
                COUNT { (class)-[:HAS_FIELD]->() } AS fields
                """,
            parameters,
            page,
            record -> {
                final var type = record.get("class");

                return new ScanClass(
                    string(type.get("id")),
                    string(type.get("name")),
                    string(type.get("qualifiedName")),
                    string(type.get("kind")),
                    strings(type.get("modifiers")),
                    string(record.get("module")),
                    string(record.get("file")),
                    string(record.get("packageName")),
                    string(type.get("superClass")),
                    strings(type.get("interfaces")),
                    strings(type.get("annotations")),
                    record.get("methods").asLong(),
                    record.get("fields").asLong()
                );
            }
        );
    }
}
