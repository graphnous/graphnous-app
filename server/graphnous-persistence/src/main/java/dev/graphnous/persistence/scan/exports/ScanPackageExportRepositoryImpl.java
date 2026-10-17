package dev.graphnous.persistence.scan.exports;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.exports.ScanPackageExport;
import dev.graphnous.application.scan.exports.ScanPackageExportFilter;
import dev.graphnous.application.scan.exports.ScanPackageExportRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.GraphPages;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads what a scan uses of packages, as ScanResultRepositoryImpl stores
 * it: a {@code PackageExport} for each export the code of a target uses,
 * which the methods and functions that use it call or instantiate.
 */
@Repository
public class ScanPackageExportRepositoryImpl implements ScanPackageExportRepository {

    private static final String USAGES = "COUNT { ()-[:CALLS|INSTANTIATES]->(export) }";

    private static final Map<String, String> SORTS = Map.of(
        "qualifiedName", "export.qualifiedName",
        "name", "export.name",
        "usages", USAGES
    );

    private final Driver driver;

    public ScanPackageExportRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public Page<ScanPackageExport> findPackageExports(
        final Scan.ScanId scanId,
        final ScanPackageExportFilter filter,
        final PageQuery page
    ) {
        final var parameters = new HashMap<String, Object>();
        parameters.put("scanId", scanId.id().toString());
        parameters.put("search", filter.search());
        parameters.put("package", filter.pkg());

        return GraphPages.page(
            driver,
            """
                MATCH (export:PackageExport {scanId: $scanId})
                WHERE ($package IS NULL OR export.package = $package)
                  AND ($search IS NULL OR toLower(export.qualifiedName) CONTAINS toLower($search))
                """,
            SORTS,
            "export.id",
            // A method's file is that of its class
            """
                export.id AS id,
                export.package AS package,
                export.module AS module,
                export.name AS name,
                export.qualifiedName AS qualifiedName,
                %s AS usages,
                size(COLLECT {
                    MATCH (user)-[:CALLS|INSTANTIATES]->(export)
                    OPTIONAL MATCH (owner:Class)-[:HAS_METHOD]->(user)
                    MATCH (file:File)-[:DECLARES]->(declared)
                    WHERE declared = user OR declared = owner
                    RETURN DISTINCT file.path
                }) AS files
                """.formatted(USAGES),
            parameters,
            page,
            record -> new ScanPackageExport(
                string(record.get("id")),
                string(record.get("package")),
                string(record.get("module")),
                string(record.get("name")),
                string(record.get("qualifiedName")),
                record.get("usages").asLong(),
                record.get("files").asLong()
            )
        );
    }
}
