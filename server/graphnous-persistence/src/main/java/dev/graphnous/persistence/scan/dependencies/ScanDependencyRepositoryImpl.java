package dev.graphnous.persistence.scan.dependencies;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.dependencies.ScanDependency;
import dev.graphnous.application.scan.dependencies.ScanDependencyFilter;
import dev.graphnous.application.scan.dependencies.ScanDependencyRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.GraphPages;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads what the modules of a scan depend on, as ScanResultRepositoryImpl
 * stores it: libraries are shared between scans, with the scope on each
 * module's relationship to them.
 */
@Repository
public class ScanDependencyRepositoryImpl implements ScanDependencyRepository {

    private static final Map<String, String> SORTS = Map.of(
        "name", "dependency.name",
        "module", "module.path",
        "scope", "dependsOn.scope"
    );

    private final Driver driver;

    public ScanDependencyRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public Page<ScanDependency> findDependencies(
        final Scan.ScanId scanId,
        final ScanDependencyFilter filter,
        final PageQuery page
    ) {
        final var parameters = new HashMap<String, Object>();
        parameters.put("scanId", scanId.id().toString());
        parameters.put("search", filter.search());
        parameters.put("module", filter.module());
        parameters.put("scope", filter.scope());

        return GraphPages.page(
            driver,
            """
                MATCH (module:Module {scanId: $scanId})-[dependsOn:DEPENDS_ON]->(dependency:Dependency)
                WHERE ($module IS NULL OR module.path = $module)
                  AND ($search IS NULL OR toLower(dependency.name) CONTAINS toLower($search))
                  AND ($scope IS NULL OR toLower(dependsOn.scope) = toLower($scope))
                """,
            SORTS,
            "module.path, dependency.name",
            """
                dependency.coordinates AS coordinates,
                module.path AS module,
                dependency.name AS name,
                dependency.version AS version,
                dependsOn.scope AS scope
                """,
            parameters,
            page,
            record -> new ScanDependency(
                // As the graph knows a dependency, see ScanGraphRepositoryImpl
                "dependency:" + string(record.get("coordinates")),
                string(record.get("module")),
                string(record.get("name")),
                string(record.get("version")),
                string(record.get("scope"))
            )
        );
    }
}
