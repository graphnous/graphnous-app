package dev.graphnous.persistence.scan.dependencies;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.dependencies.ScanDependency;
import dev.graphnous.application.scan.dependencies.ScanDependencyFilter;
import dev.graphnous.application.scan.dependencies.ScanDependencyRepository;
import dev.graphnous.application.scan.dependencies.SystemDependency;
import dev.graphnous.application.scan.dependencies.SystemLibrary;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.GraphPages;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private static final Map<String, String> LIBRARY_SORTS = Map.of(
        "name", "name",
        "versions", "size(versions)"
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

    @Override
    public Page<SystemDependency> findDependencies(
        final List<Scan> scans,
        final ScanDependencyFilter filter,
        final PageQuery page
    ) {
        final var projects = scans.stream().collect(
            Collectors.toMap(scan -> scan.id().id().toString(), Scan::projectId)
        );

        final var parameters = new HashMap<String, Object>();
        parameters.put("scanIds", List.copyOf(projects.keySet()));
        parameters.put("search", filter.search());
        parameters.put("module", filter.module());
        parameters.put("scope", filter.scope());

        return GraphPages.page(
            driver,
            """
                MATCH (module:Module)-[dependsOn:DEPENDS_ON]->(dependency:Dependency)
                WHERE module.scanId IN $scanIds
                  AND ($module IS NULL OR module.path = $module)
                  AND ($search IS NULL OR toLower(dependency.name) CONTAINS toLower($search))
                  AND ($scope IS NULL OR toLower(dependsOn.scope) = toLower($scope))
                """,
            SORTS,
            "module.scanId, module.path, dependency.name",
            """
                module.scanId AS scanId,
                dependency.coordinates AS coordinates,
                module.path AS module,
                dependency.name AS name,
                dependency.version AS version,
                dependsOn.scope AS scope
                """,
            parameters,
            page,
            record -> {
                final var scanId = string(record.get("scanId"));

                return new SystemDependency(
                    projects.get(scanId),
                    new Scan.ScanId(UUID.fromString(scanId)),
                    new ScanDependency(
                        "dependency:" + string(record.get("coordinates")),
                        string(record.get("module")),
                        string(record.get("name")),
                        string(record.get("version")),
                        string(record.get("scope"))
                    )
                );
            }
        );
    }

    @Override
    public Page<SystemLibrary> findLibraries(
        final List<Scan> scans,
        final String search,
        final boolean driftOnly,
        final PageQuery page
    ) {
        final var projects = scans.stream().collect(
            Collectors.toMap(scan -> scan.id().id().toString(), Scan::projectId)
        );

        final var parameters = new HashMap<String, Object>();
        parameters.put("scanIds", List.copyOf(projects.keySet()));
        parameters.put("search", search);
        parameters.put("driftOnly", driftOnly);

        return GraphPages.page(
            driver,
            // Each version once, with the scans that use it, then each
            // library once, with its versions
            """
                MATCH (module:Module)-[:DEPENDS_ON]->(dependency:Dependency)
                WHERE module.scanId IN $scanIds
                  AND ($search IS NULL OR toLower(dependency.name) CONTAINS toLower($search))
                WITH dependency.name AS name, dependency.version AS version, collect(DISTINCT module.scanId) AS scans
                ORDER BY name, version
                WITH name, collect({version: version, scans: scans}) AS versions
                WHERE NOT $driftOnly OR size(versions) > 1
                """,
            LIBRARY_SORTS,
            "name",
            "name AS name, versions AS versions",
            parameters,
            page,
            record -> new SystemLibrary(
                string(record.get("name")),
                record.get("versions").asList(version -> new SystemLibrary.Version(
                    string(version.get("version")),
                    version.get("scans").asList(scan -> projects.get(scan.asString()))
                        .stream()
                        .distinct()
                        .toList()
                ))
            )
        );
    }
}
