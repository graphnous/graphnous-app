package dev.graphnous.persistence.scan.files;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.files.ScanFile;
import dev.graphnous.application.scan.files.ScanFileFilter;
import dev.graphnous.application.scan.files.ScanFileRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.GraphPages;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

import static dev.graphnous.persistence.scan.GraphPages.number;
import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads the files below a scan's modules, as ScanResultRepositoryImpl
 * stores them.
 */
@Repository
public class ScanFileRepositoryImpl implements ScanFileRepository {

    private static final Map<String, String> SORTS = Map.of(
        "path", "file.path",
        "language", "file.language",
        "size", "file.size"
    );

    private final Driver driver;

    public ScanFileRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public Page<ScanFile> findFiles(
        final Scan.ScanId scanId,
        final ScanFileFilter filter,
        final PageQuery page
    ) {
        final var parameters = new HashMap<String, Object>();
        parameters.put("scanId", scanId.id().toString());
        parameters.put("search", filter.search());
        parameters.put("module", filter.module());
        parameters.put("language", filter.language());
        parameters.put("sourceSet", filter.sourceSet());

        return GraphPages.page(
            driver,
            """
                MATCH (module:Module {scanId: $scanId})-[:HAS_FILE]->(file:File)
                WHERE ($module IS NULL OR module.path = $module)
                  AND ($search IS NULL OR toLower(file.path) CONTAINS toLower($search))
                  AND ($language IS NULL OR toLower(file.language) = toLower($language))
                  AND ($sourceSet IS NULL OR file.sourceSet = toUpper($sourceSet))
                """,
            SORTS,
            "file.id",
            """
                file.id AS id,
                [(target:ScanTarget)-[:HAS_MODULE]->(module) | target.path][0] AS target,
                module.path AS module,
                file.path AS path,
                file.language AS language,
                file.sourceSet AS sourceSet,
                file.size AS size,
                file.checksum AS checksum,
                COUNT { (file)-[:DECLARES]->(:Class) } AS classes,
                COUNT { (file)-[:DECLARES]->(:Method) } AS functions
                """,
            parameters,
            page,
            record -> new ScanFile(
                string(record.get("id")),
                string(record.get("target")),
                string(record.get("module")),
                string(record.get("path")),
                string(record.get("language")),
                string(record.get("sourceSet")),
                number(record.get("size")),
                string(record.get("checksum")),
                record.get("classes").asLong(),
                record.get("functions").asLong()
            )
        );
    }
}
