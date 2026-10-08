package dev.graphnous.persistence.scan.modules;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.modules.ScanModule;
import dev.graphnous.application.scan.modules.ScanModuleFilter;
import dev.graphnous.application.scan.modules.ScanModuleRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.GraphPages;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads the modules below a scan's targets, as ScanResultRepositoryImpl
 * stores them, counting what each holds as the scan's overview does (see
 * ScanGraphRepositoryImpl).
 */
@Repository
public class ScanModuleRepositoryImpl implements ScanModuleRepository {

    private static final Map<String, String> SORTS = Map.of(
        "path", "module.path",
        "name", "module.name"
    );

    private final Driver driver;

    public ScanModuleRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public Page<ScanModule> findModules(
        final Scan.ScanId scanId,
        final ScanModuleFilter filter,
        final PageQuery page
    ) {
        final var parameters = new HashMap<String, Object>();
        parameters.put("scanId", scanId.id().toString());
        parameters.put("search", filter.search());
        parameters.put("target", filter.target());

        return GraphPages.page(
            driver,
            """
                MATCH (target:ScanTarget {scanId: $scanId})-[:HAS_MODULE]->(module:Module)
                WHERE ($target IS NULL OR target.path = $target)
                  AND ($search IS NULL
                       OR toLower(module.path) CONTAINS toLower($search)
                       OR toLower(module.name) CONTAINS toLower($search))
                """,
            SORTS,
            "module.id",
            """
                module.id AS id,
                target.path AS target,
                module.name AS name,
                module.path AS path,
                COUNT { (module)-[:HAS_FILE]->() } AS files,
                COUNT { (module)-[:HAS_PACKAGE]->() } AS packages,
                COUNT {
                    MATCH (module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(class:Class)
                    RETURN DISTINCT class
                } AS classes,
                COUNT {
                    MATCH (module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(:Class)-[:HAS_METHOD]->(method)
                    RETURN method
                    UNION
                    MATCH (module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(method:Method)
                    RETURN method
                } AS methods,
                COUNT { (module)-[:DEPENDS_ON]->() } AS dependencies
                """,
            parameters,
            page,
            record -> new ScanModule(
                string(record.get("id")),
                string(record.get("target")),
                string(record.get("name")),
                string(record.get("path")),
                record.get("files").asLong(),
                record.get("packages").asLong(),
                record.get("classes").asLong(),
                record.get("methods").asLong(),
                record.get("dependencies").asLong()
            )
        );
    }
}
