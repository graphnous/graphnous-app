package dev.graphnous.persistence.scan.packages;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.packages.ScanPackage;
import dev.graphnous.application.scan.packages.ScanPackageFilter;
import dev.graphnous.application.scan.packages.ScanPackageRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.GraphPages;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads the packages below a scan's modules, as ScanResultRepositoryImpl
 * stores them: a package contains the classes, functions and variables of
 * the files that name it.
 */
@Repository
public class ScanPackageRepositoryImpl implements ScanPackageRepository {

    private static final Map<String, String> SORTS = Map.of(
        "qualifiedName", "coalesce(package.qualifiedName, package.name)",
        "name", "package.name"
    );

    private final Driver driver;

    public ScanPackageRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public Page<ScanPackage> findPackages(
        final Scan.ScanId scanId,
        final ScanPackageFilter filter,
        final PageQuery page
    ) {
        final var parameters = new HashMap<String, Object>();
        parameters.put("scanId", scanId.id().toString());
        parameters.put("search", filter.search());
        parameters.put("module", filter.module());

        return GraphPages.page(
            driver,
            """
                MATCH (module:Module {scanId: $scanId})-[:HAS_PACKAGE]->(package:Package)
                WHERE ($module IS NULL OR module.path = $module)
                  AND ($search IS NULL
                       OR toLower(package.name) CONTAINS toLower($search)
                       OR toLower(package.qualifiedName) CONTAINS toLower($search))
                """,
            SORTS,
            "package.id",
            """
                package.id AS id,
                module.path AS module,
                package.name AS name,
                package.qualifiedName AS qualifiedName,
                COUNT { (package)-[:CONTAINS]->(:Class) } AS classes,
                COUNT { (package)-[:CONTAINS]->(:Method) } AS functions,
                COUNT { (package)-[:CONTAINS]->(:Field) } AS variables
                """,
            parameters,
            page,
            record -> new ScanPackage(
                string(record.get("id")),
                string(record.get("module")),
                string(record.get("name")),
                string(record.get("qualifiedName")),
                record.get("classes").asLong(),
                record.get("functions").asLong(),
                record.get("variables").asLong()
            )
        );
    }
}
