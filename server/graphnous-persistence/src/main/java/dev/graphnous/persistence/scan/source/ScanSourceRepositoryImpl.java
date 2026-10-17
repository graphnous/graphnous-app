package dev.graphnous.persistence.scan.source;

import dev.graphnous.application.scan.source.ScanSource;
import dev.graphnous.application.scan.source.ScanSourceRepository;
import dev.graphnous.domain.scan.Scan;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads the files below a scan's modules, with what they declare, as
 * ScanResultRepositoryImpl stores them: a file declares its classes, nested
 * ones included, and its functions and variables; the classes have the
 * methods and fields.
 */
@Repository
public class ScanSourceRepositoryImpl implements ScanSourceRepository {

    private final Driver driver;

    public ScanSourceRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public List<ScanSource.File> findFilesEndingPath(final Scan.ScanId scanId, final String path) {
        try (final var session = driver.session()) {
            return session.executeRead(tx -> tx.run(
                """
                    MATCH (module:Module {scanId: $scanId})-[:HAS_FILE]->(file:File)
                    WHERE $path ENDS WITH file.path
                    RETURN
                        file {.id, .path, .language, .sourceSet} AS file,
                        [(target:ScanTarget)-[:HAS_MODULE]->(module) | target.path][0] AS target,
                        module.path AS module,
                        [(file)-[:DECLARES]->(class:Class) | {
                            id: class.id, type: 'CLASS', kind: class.kind, name: class.name,
                            qualifiedName: class.qualifiedName, className: null
                        }]
                        + [(file)-[:DECLARES]->(class:Class)-[:HAS_METHOD]->(method:Method) | {
                            id: method.id, type: 'METHOD', kind: method.kind, name: method.name,
                            qualifiedName: method.qualifiedName, className: class.qualifiedName
                        }]
                        + [(file)-[:DECLARES]->(class:Class)-[:HAS_FIELD]->(field:Field) | {
                            id: field.id, type: 'FIELD', kind: null, name: field.name,
                            qualifiedName: field.qualifiedName, className: class.qualifiedName
                        }]
                        + [(file)-[:DECLARES]->(function:Method) | {
                            id: function.id, type: 'METHOD', kind: function.kind, name: function.name,
                            qualifiedName: function.qualifiedName, className: null
                        }]
                        + [(file)-[:DECLARES]->(variable:Field) | {
                            id: variable.id, type: 'FIELD', kind: null, name: variable.name,
                            qualifiedName: variable.qualifiedName, className: null
                        }] AS declarations
                    ORDER BY file.id
                    """,
                Map.of("scanId", scanId.id().toString(), "path", path)
            ).list(record -> {
                final var file = record.get("file");

                return new ScanSource.File(
                    string(file.get("id")),
                    string(record.get("target")),
                    string(record.get("module")),
                    string(file.get("path")),
                    string(file.get("language")),
                    string(file.get("sourceSet")),
                    record.get("declarations").asList(ScanSourceRepositoryImpl::declaration)
                );
            }));
        }
    }

    private static ScanSource.Declaration declaration(final Value value) {
        return new ScanSource.Declaration(
            string(value.get("id")),
            string(value.get("type")),
            string(value.get("kind")),
            string(value.get("name")),
            string(value.get("qualifiedName")),
            string(value.get("className"))
        );
    }
}
