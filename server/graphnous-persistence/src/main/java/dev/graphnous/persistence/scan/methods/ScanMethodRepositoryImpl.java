package dev.graphnous.persistence.scan.methods;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.methods.ScanMethod;
import dev.graphnous.application.scan.methods.ScanMethodFilter;
import dev.graphnous.application.scan.methods.ScanMethodRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.GraphPages;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

import static dev.graphnous.persistence.scan.GraphPages.string;
import static dev.graphnous.persistence.scan.GraphPages.strings;

/**
 * Reads the methods of a scan's classes and the functions of its files, as
 * ScanResultRepositoryImpl stores them: a method belongs to its class, and
 * through it to a file; a function belongs to its file directly.
 */
@Repository
public class ScanMethodRepositoryImpl implements ScanMethodRepository {

    private static final Map<String, String> SORTS = Map.of(
        "name", "method.name",
        "qualifiedName", "method.qualifiedName",
        "kind", "method.kind"
    );

    private final Driver driver;

    public ScanMethodRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public Page<ScanMethod> findMethods(
        final Scan.ScanId scanId,
        final ScanMethodFilter filter,
        final PageQuery page
    ) {
        final var parameters = new HashMap<String, Object>();
        parameters.put("scanId", scanId.id().toString());
        parameters.put("search", filter.search());
        parameters.put("module", filter.module());
        parameters.put("kind", filter.kind());
        parameters.put("className", filter.className());

        return GraphPages.page(
            driver,
            """
                MATCH (method:Method {scanId: $scanId})
                WHERE ($search IS NULL
                       OR toLower(method.name) CONTAINS toLower($search)
                       OR toLower(method.qualifiedName) CONTAINS toLower($search))
                  AND ($kind IS NULL OR method.kind = toUpper($kind))
                OPTIONAL MATCH (class:Class)-[:HAS_METHOD]->(method)
                WITH method, class
                WHERE $className IS NULL OR class.qualifiedName = $className
                // A method's file declares its class; a function's declares it
                OPTIONAL MATCH (classFile:File)-[:DECLARES]->(class)
                OPTIONAL MATCH (functionFile:File)-[:DECLARES]->(method)
                WITH method, class, coalesce(classFile, functionFile) AS file
                OPTIONAL MATCH (module:Module)-[:HAS_FILE]->(file)
                WITH method, class, min(file.path) AS file, min(module.path) AS module
                WHERE $module IS NULL OR module = $module
                """,
            SORTS,
            "method.id",
            """
                method {.*} AS method,
                class.qualifiedName AS className,
                module,
                file
                """,
            parameters,
            page,
            record -> {
                final var method = record.get("method");

                return new ScanMethod(
                    string(method.get("id")),
                    string(method.get("name")),
                    string(method.get("qualifiedName")),
                    string(method.get("kind")),
                    strings(method.get("modifiers")),
                    string(method.get("returnType")),
                    strings(method.get("parameterNames")),
                    strings(method.get("parameterTypes")),
                    strings(method.get("annotations")),
                    string(record.get("className")),
                    string(record.get("module")),
                    string(record.get("file"))
                );
            }
        );
    }
}
