package dev.graphnous.persistence.scan.graph;

import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.graph.ScanGraphRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.result.EnhancementRepositoryImpl;
import dev.graphnous.persistence.scan.result.ScanResultRepositoryImpl;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Reads the graph that ScanResultRepositoryImpl stores below a scan. A
 * class, function or variable belongs to its module through its file, its
 * package or both, so its module is found through either.
 */
@Repository
public class ScanGraphRepositoryImpl implements ScanGraphRepository {

    /**
     * The labels of the nodes below a scan, which carry its scanId and are
     * found by their id.
     */
    private static final List<String> SCAN_LABELS = List.of(
        "ScanTarget", "Module", "File", "Package", "Class", "Method", "Field", "Annotation"
    );

    /**
     * The labels a focus is looked up by: those of the nodes below a scan,
     * the enhancers that ran on it, and what they added.
     */
    private static final List<String> FOCUS_LABELS = Stream.concat(
        SCAN_LABELS.stream(),
        Stream.of(EnhancementRepositoryImpl.ENHANCEMENT, EnhancementRepositoryImpl.ENHANCED)
    ).toList();

    /**
     * The labels of the nodes two scans are compared by: what the scanners
     * found, without the annotations, which their owners list, and without
     * what enhancers added.
     */
    private static final List<String> COMPARED_LABELS = List.of(
        "ScanTarget", "Module", "Package", "File", "Class", "Method", "Field", "Endpoint"
    );

    /**
     * What a dependency's id starts with: dependencies are shared between
     * scans, so they have no id of their own and are known by their
     * coordinates.
     */
    static final String DEPENDENCY_PREFIX = "dependency:";

    /**
     * Properties that only link a node to its scan, which say nothing about
     * the node itself.
     */
    private static final Set<String> HIDDEN_PROPERTIES = Set.of("id", "scanId", "targetId");

    private final Driver driver;

    public ScanGraphRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public ScanGraph.Overview getOverview(final Scan.ScanId scanId) {
        final var targets = read("""
                MATCH (:Scan {id: $scanId})-[:HAS_TARGET]->(target:ScanTarget)
                OPTIONAL MATCH (target)-[:HAS_MODULE]->(module:Module)
                WITH target, module
                ORDER BY module.path
                WITH target, collect(CASE WHEN module IS NULL THEN null ELSE module {
                    .name,
                    .path,
                    files: COUNT { (module)-[:HAS_FILE]->() },
                    packages: COUNT { (module)-[:HAS_PACKAGE]->() },
                    classes: COUNT {
                        MATCH (module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(class:Class)
                        RETURN DISTINCT class
                    },
                    methods: COUNT {
                        MATCH (module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(:Class)-[:HAS_METHOD]->(method)
                        RETURN method
                        UNION
                        MATCH (module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(method:Method)
                        RETURN method
                    },
                    dependencies: COUNT { (module)-[:DEPENDS_ON]->() }
                } END) AS modules
                RETURN target.path AS path,
                       target.language AS language,
                       target.languageVersion AS languageVersion,
                       target.buildSystem AS buildSystem,
                       modules
                ORDER BY path
                """,
            Map.of("scanId", id(scanId)),
            record -> new ScanGraph.Target(
                string(record.get("path")),
                string(record.get("language")),
                string(record.get("languageVersion")),
                string(record.get("buildSystem")),
                record.get("modules").asList(module -> new ScanGraph.Module(
                    string(module.get("name")),
                    string(module.get("path")),
                    module.get("files").asLong(),
                    module.get("packages").asLong(),
                    module.get("classes").asLong(),
                    module.get("methods").asLong(),
                    module.get("dependencies").asLong()
                ))
            )
        );

        return new ScanGraph.Overview(targets);
    }

    /**
     * Walks out from the focus a hop at a time, so a node with many
     * neighbours, such as a large package, fills the limit rather than the
     * memory: following every path at once would multiply them.
     */
    @Override
    public Optional<ScanGraph.Neighbourhood> findNeighbourhood(
        final Scan.ScanId scanId,
        final String focus,
        final int depth,
        final int limit,
        final Set<String> layers
    ) {
        final var id = id(scanId);

        // What bodies use shows only when asked for; it is no layer of nodes
        final var usages = layers != null && layers.contains(ScanGraph.Layer.USAGES);
        final var nodeLayers = layers == null
            ? null
            : layers.stream().filter(layer -> !layer.equals(ScanGraph.Layer.USAGES)).toList();

        // Asked for alone, it is drawn on every layer
        final var shown = nodeLayers == null || (usages && nodeLayers.isEmpty()) ? null : nodeLayers;

        // The relationships left out of the graph
        final List<String> hidden = usages ? List.of() : ScanResultRepositoryImpl.USAGE_TYPES;

        try (final var session = driver.session()) {
            return session.executeRead(tx -> {
                final var start = focus == null ? findScan(tx, id) : findNode(tx, id, focus);

                if (start.isEmpty()) {
                    // A scan without results has nothing to show, rather than nothing to find
                    return focus == null
                        ? Optional.of(new ScanGraph.Neighbourhood(id, depth, List.of(), List.of(), false, layers(tx, id)))
                        : Optional.empty();
                }

                // The nodes reached, by element id, with their hops from the focus
                final var reached = new LinkedHashMap<String, Integer>();
                reached.put(start.get(), 0);

                var frontier = List.of(start.get());
                var truncated = false;

                for (int hop = 1; hop <= depth && !frontier.isEmpty() && !truncated; hop++) {
                    final var room = limit - reached.size();

                    // One more than fits, to know whether there were more
                    final var next = neighbours(tx, id, frontier, List.copyOf(reached.keySet()), shown, hidden, room + 1);

                    truncated = next.size() > room;
                    frontier = next.subList(0, Math.min(next.size(), room));

                    for (final var node : frontier) {
                        reached.put(node, hop);
                    }
                }

                final var elementIds = List.copyOf(reached.keySet());
                final var nodes = nodes(tx, elementIds, reached);

                final var ids = new HashMap<String, String>();
                nodes.forEach((elementId, node) -> ids.put(elementId, node.id()));

                return Optional.of(new ScanGraph.Neighbourhood(
                    ids.get(start.get()),
                    depth,
                    List.copyOf(nodes.values()),
                    edges(tx, elementIds, ids, hidden),
                    truncated,
                    layers(tx, id)
                ));
            });
        }
    }

    private static Optional<String> findScan(final TransactionContext tx, final String scanId) {
        return first(tx.run(
            "MATCH (n:Scan {id: $scanId}) RETURN elementId(n) AS id",
            Map.of("scanId", scanId)
        ).list(record -> record.get("id").asString()));
    }

    /**
     * The node of the scan with this id: the scan itself, one of its
     * dependencies, a node below it, an enhancer that ran on it or a node
     * an enhancer added. Each label is looked up through its
     * own index, as an id does not say which label it is of.
     */
    private static Optional<String> findNode(
        final TransactionContext tx,
        final String scanId,
        final String focus
    ) {
        if (focus.equals(scanId)) {
            return findScan(tx, scanId);
        }

        if (focus.startsWith(DEPENDENCY_PREFIX)) {
            return first(tx.run("""
                    MATCH (n:Dependency {coordinates: $coordinates})
                    WHERE EXISTS { (n)<-[:DEPENDS_ON]-(:Module {scanId: $scanId}) }
                    RETURN elementId(n) AS id
                    """,
                Map.of("scanId", scanId, "coordinates", focus.substring(DEPENDENCY_PREFIX.length()))
            ).list(record -> record.get("id").asString()));
        }

        final var query = String.join("\nUNION\n", FOCUS_LABELS.stream()
            .map(label -> "MATCH (n:" + label + " {id: $focus}) WHERE n.scanId = $scanId RETURN elementId(n) AS id")
            .toList());

        return first(tx.run(query, Map.of("scanId", scanId, "focus", focus))
            .list(record -> record.get("id").asString()));
    }

    /**
     * The nodes next to the frontier that were not reached yet: nodes of the
     * scan, the scan itself and the dependencies of its modules, but not the
     * modules of other scans that share those. At most limit, by id, so the
     * same request gets the same nodes.
     * <p>
     * Only nodes of the layers shown, when some are hidden: an enhancer and
     * what it added are of its layer, everything else is of the scan
     * result's.
     *
     * @param layers the ids of the layers shown; every layer when null
     * @param hidden the types of the relationships not followed
     */
    private static List<String> neighbours(
        final TransactionContext tx,
        final String scanId,
        final List<String> frontier,
        final List<String> reached,
        final List<String> layers,
        final List<String> hidden,
        final int limit
    ) {
        final var parameters = new HashMap<String, Object>();
        parameters.put("scanId", scanId);
        parameters.put("frontier", frontier);
        parameters.put("reached", reached);
        parameters.put("layers", layers);
        parameters.put("result", ScanGraph.Layer.RESULT);
        parameters.put("limit", limit);
        parameters.put("usages", hidden);

        // Not along what bodies use, unless asked for: a method would bring
        // in everything it calls
        return tx.run("""
                MATCH (n) WHERE elementId(n) IN $frontier
                MATCH (n)-[r]-(m)
                WHERE NOT type(r) IN $usages
                  AND (m.scanId = $scanId OR (m:Scan AND m.id = $scanId) OR m:Dependency)
                  AND NOT elementId(m) IN $reached
                  AND ($layers IS NULL OR CASE
                    WHEN m:%1$s THEN (m.name + '@' + m.version) IN $layers
                    WHEN m:%2$s THEN EXISTS {
                      MATCH (enhancement:%1$s)-[:ADDED]->(m)
                      WHERE (enhancement.name + '@' + enhancement.version) IN $layers
                    }
                    ELSE $result IN $layers
                  END)
                WITH DISTINCT m
                ORDER BY coalesce(m.id, m.coordinates)
                LIMIT $limit
                RETURN elementId(m) AS id
                """.formatted(EnhancementRepositoryImpl.ENHANCEMENT, EnhancementRepositoryImpl.ENHANCED),
            parameters
        ).list(record -> record.get("id").asString());
    }

    /**
     * Every layer of the scan's graph: what the scan found, then what each
     * enhancer that ran on it added, by name, then what its bodies use, if
     * it kept that.
     */
    private static List<ScanGraph.Layer> layers(
        final TransactionContext tx,
        final String scanId
    ) {
        final var layers = new ArrayList<ScanGraph.Layer>();
        layers.add(new ScanGraph.Layer(ScanGraph.Layer.RESULT, "Scan result"));

        tx.run("""
                MATCH (:Scan {id: $scanId})-[:HAS_ENHANCEMENT]->(enhancement:%s)
                RETURN enhancement.name AS name, enhancement.version AS version
                ORDER BY name, version
                """.formatted(EnhancementRepositoryImpl.ENHANCEMENT),
            Map.of("scanId", scanId)
        ).list().forEach(record -> {
            final var name = record.get("name").asString();
            final var version = record.get("version").asString();

            layers.add(new ScanGraph.Layer(name + "@" + version, name + " " + version));
        });

        // Only for a scan that kept what its bodies use
        final var usages = tx.run("""
                RETURN EXISTS {
                    MATCH (:Method {scanId: $scanId})-[usage]->()
                    WHERE type(usage) IN $types
                } AS usages
                """,
            Map.of("scanId", scanId, "types", ScanResultRepositoryImpl.USAGE_TYPES)
        ).single().get("usages").asBoolean();

        if (usages) {
            layers.add(new ScanGraph.Layer(ScanGraph.Layer.USAGES, "Usages"));
        }

        return List.copyOf(layers);
    }

    /**
     * The nodes, nearest first, as they were reached.
     */
    private static Map<String, ScanGraph.Node> nodes(
        final TransactionContext tx,
        final List<String> elementIds,
        final Map<String, Integer> depths
    ) {
        final var found = new HashMap<String, ScanGraph.Node>();

        // A node an enhancer added is of its own label, not the one they all have
        tx.run("""
                MATCH (n) WHERE elementId(n) IN $ids
                RETURN elementId(n) AS elementId,
                       [label IN labels(n) WHERE label <> $enhanced][0] AS type,
                       properties(n) AS properties
                """,
            Map.of("ids", elementIds, "enhanced", EnhancementRepositoryImpl.ENHANCED)
        ).list().forEach(record -> {
            final var elementId = record.get("elementId").asString();
            final var type = record.get("type").asString();
            final var properties = record.get("properties").asMap();

            found.put(elementId, new ScanGraph.Node(
                nodeId(type, properties),
                type,
                nodeName(type, properties),
                depths.get(elementId),
                visible(properties)
            ));
        });

        final var nodes = new LinkedHashMap<String, ScanGraph.Node>();
        elementIds.forEach(elementId -> nodes.put(elementId, found.get(elementId)));

        return nodes;
    }

    /**
     * The relationships between the nodes, also those between two nodes at
     * the edge of the neighbourhood, such as a class extending another.
     */
    private static List<ScanGraph.Edge> edges(
        final TransactionContext tx,
        final List<String> elementIds,
        final Map<String, String> ids,
        final List<String> hidden
    ) {
        // Each kind of relationship between two nodes once: a method that
        // calls another in three places is one line, which says how many
        return tx.run("""
                MATCH (a)-[r]->(b)
                WHERE elementId(a) IN $ids AND elementId(b) IN $ids AND NOT type(r) IN $hidden
                WITH a, b, type(r) AS type, collect(r) AS relationships
                RETURN elementId(a) AS source, elementId(b) AS target, type,
                       properties(head(relationships)) AS properties, size(relationships) AS places
                """,
            Map.of("ids", elementIds, "hidden", hidden)
        ).list(record -> {
            final var type = record.get("type").asString();
            final var properties = new HashMap<>(record.get("properties").asMap());

            if (ScanResultRepositoryImpl.USAGE_TYPES.contains(type)) {
                // The line is that of one place only
                properties.remove("line");
                properties.put("places", record.get("places").asInt());
            }

            return new ScanGraph.Edge(
                ids.get(record.get("source").asString()),
                ids.get(record.get("target").asString()),
                type,
                properties
            );
        });
    }

    /**
     * The id a focus refers to the node by.
     */
    private static String nodeId(final String type, final Map<String, Object> properties) {
        return "Dependency".equals(type)
            ? DEPENDENCY_PREFIX + properties.get("coordinates")
            : (String) properties.get("id");
    }

    private static String nodeName(final String type, final Map<String, Object> properties) {
        final var name = switch (type) {
            case "Scan" -> "Scan";
            case "ScanTarget", "File" -> properties.get("path");
            case "Package" -> properties.getOrDefault("qualifiedName", properties.get("name"));
            case "Annotation" -> "@" + properties.get("name");
            // As an enhancer found it, such as GET /api/orders/{id}
            case "Endpoint" -> properties.get("url") == null
                ? null
                : (properties.get("httpMethod") == null ? "" : properties.get("httpMethod") + " ") + properties.get("url");
            default -> properties.get("name");
        };

        return name == null ? null : name.toString();
    }

    private static Map<String, Object> visible(final Map<String, Object> properties) {
        final var visible = new LinkedHashMap<String, Object>();

        properties.entrySet().stream()
            .filter(entry -> !HIDDEN_PROPERTIES.contains(entry.getKey()))
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> visible.put(entry.getKey(), entry.getValue()));

        return visible;
    }

    private static Optional<String> first(final List<String> ids) {
        return ids.stream().findFirst();
    }

    @Override
    public List<ScanGraph.ClassSummary> findClasses(
        final Scan.ScanId scanId,
        final String query,
        final int limit
    ) {
        return read("""
                MATCH (class:Class {scanId: $scanId})
                WHERE toLower(class.name) CONTAINS toLower($query)
                   OR toLower(class.qualifiedName) CONTAINS toLower($query)
                OPTIONAL MATCH (module:Module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(class)
                OPTIONAL MATCH (file:File)-[:DECLARES]->(class)
                WITH class, min(module.path) AS module, min(file.path) AS file
                RETURN class.name AS name,
                       class.qualifiedName AS qualifiedName,
                       class.kind AS kind,
                       module,
                       file
                ORDER BY qualifiedName, module
                LIMIT $limit
                """,
            Map.of("scanId", id(scanId), "query", query, "limit", limit),
            record -> new ScanGraph.ClassSummary(
                string(record.get("name")),
                string(record.get("qualifiedName")),
                string(record.get("kind")),
                string(record.get("module")),
                string(record.get("file"))
            )
        );
    }

    @Override
    public Optional<ScanGraph.ClassDetails> findClass(
        final Scan.ScanId scanId,
        final String qualifiedName
    ) {
        return read("""
                MATCH (class:Class {scanId: $scanId, qualifiedName: $qualifiedName})
                OPTIONAL MATCH (module:Module)-[:HAS_FILE|HAS_PACKAGE]->()-[:DECLARES|CONTAINS]->(class)
                WITH class, min(module.path) AS module
                ORDER BY module
                LIMIT 1
                RETURN class {.*} AS class,
                       module,
                       [(file:File)-[:DECLARES]->(class) | file.path][0] AS file,
                       [(package:Package)-[:CONTAINS]->(class) | package.qualifiedName][0] AS packageName,
                       COLLECT {
                           MATCH (subtype:Class)-[:EXTENDS|IMPLEMENTS]->(class)
                           RETURN DISTINCT subtype.qualifiedName ORDER BY subtype.qualifiedName
                       } AS subtypes,
                       COLLECT {
                           MATCH (class)-[:ANNOTATED_WITH]->(annotation:Annotation)
                           WITH annotation ORDER BY toInteger(last(split(annotation.id, ':')))
                           RETURN annotation {.name, .qualifiedName, .arguments}
                       } AS annotations,
                       COLLECT {
                           MATCH (class)-[:HAS_METHOD]->(method:Method)
                           WITH method ORDER BY method.name, method.qualifiedName
                           RETURN method {
                               .*,
                               annotationNodes: COLLECT {
                                   MATCH (method)-[annotated:ANNOTATED_WITH]->(annotation:Annotation)
                                   // The method's own annotations, then those of its parameters
                                   WITH annotated, annotation
                                   ORDER BY coalesce(annotated.parameterIndex, -1), toInteger(last(split(annotation.id, ':')))
                                   RETURN annotation {.name, .qualifiedName, .arguments, parameter: annotated.parameter}
                               }
                           }
                       } AS methods,
                       COLLECT {
                           MATCH (class)-[:HAS_FIELD]->(field:Field)
                           WITH field ORDER BY field.name
                           RETURN field {
                               .*,
                               annotationNodes: COLLECT {
                                   MATCH (field)-[:ANNOTATED_WITH]->(annotation:Annotation)
                                   WITH annotation ORDER BY toInteger(last(split(annotation.id, ':')))
                                   RETURN annotation {.name, .qualifiedName, .arguments}
                               }
                           }
                       } AS fields
                """,
            Map.of("scanId", id(scanId), "qualifiedName", qualifiedName),
            record -> {
                final var type = record.get("class");

                return new ScanGraph.ClassDetails(
                    string(type.get("name")),
                    string(type.get("qualifiedName")),
                    string(type.get("kind")),
                    strings(type.get("modifiers")),
                    strings(type.get("typeParameters")),
                    string(record.get("module")),
                    string(record.get("file")),
                    string(record.get("packageName")),
                    string(type.get("superClass")),
                    strings(type.get("interfaces")),
                    strings(record.get("subtypes")),
                    annotations(record.get("annotations")),
                    record.get("methods").asList(method -> new ScanGraph.Method(
                        string(method.get("name")),
                        string(method.get("kind")),
                        strings(method.get("modifiers")),
                        string(method.get("returnType")),
                        strings(method.get("parameterNames")),
                        strings(method.get("parameterTypes")),
                        annotations(method.get("annotationNodes"))
                    )),
                    record.get("fields").asList(field -> new ScanGraph.Field(
                        string(field.get("name")),
                        string(field.get("type")),
                        strings(field.get("modifiers")),
                        annotations(field.get("annotationNodes"))
                    ))
                );
            }
        ).stream().findFirst();
    }

    @Override
    public List<ScanGraph.AnnotatedElement> findAnnotated(
        final Scan.ScanId scanId,
        final String annotation,
        final int limit
    ) {
        return read("""
                MATCH (owner)-[annotated:ANNOTATED_WITH]->(annotation:Annotation {scanId: $scanId})
                WHERE toLower(annotation.name) = toLower($annotation)
                   OR toLower(annotation.qualifiedName) = toLower($annotation)
                OPTIONAL MATCH (class:Class)-[:HAS_METHOD|HAS_FIELD]->(owner)
                RETURN CASE
                           WHEN owner:Class THEN 'CLASS'
                           // Outside any class
                           WHEN owner:Method AND class IS NULL THEN 'FUNCTION'
                           WHEN owner:Method THEN 'METHOD'
                           WHEN class IS NULL THEN 'VARIABLE'
                           ELSE 'FIELD'
                       END AS kind,
                       CASE WHEN owner:Class THEN owner.qualifiedName ELSE class.qualifiedName END AS className,
                       CASE
                           WHEN owner:Class THEN null
                           WHEN class IS NULL THEN coalesce(owner.qualifiedName, owner.name)
                           ELSE owner.name
                       END AS member,
                       annotation {.name, .qualifiedName, .arguments, parameter: annotated.parameter} AS annotation
                ORDER BY className, member
                LIMIT $limit
                """,
            Map.of("scanId", id(scanId), "annotation", annotation, "limit", limit),
            record -> new ScanGraph.AnnotatedElement(
                string(record.get("kind")),
                string(record.get("className")),
                string(record.get("member")),
                annotation(record.get("annotation"))
            )
        );
    }

    @Override
    public List<ScanGraph.Dependency> findDependencies(final Scan.ScanId scanId) {
        return read("""
                MATCH (module:Module {scanId: $scanId})-[dependsOn:DEPENDS_ON]->(dependency:Dependency)
                RETURN module.path AS module,
                       dependency.name AS name,
                       dependency.version AS version,
                       dependsOn.scope AS scope
                ORDER BY module, name
                """,
            Map.of("scanId", id(scanId)),
            record -> new ScanGraph.Dependency(
                string(record.get("module")),
                string(record.get("name")),
                string(record.get("version")),
                string(record.get("scope"))
            )
        );
    }

    /**
     * Each label of the nodes a scan is compared by is read through its own
     * scanId index; the type is the label read, as an enhanced node has
     * more than one. A dependency is keyed by its module and name, so a new
     * version of it is a change rather than a removal and an addition; a
     * library a module declares more than once is one dependency, with
     * every scope and version it is declared with.
     */
    @Override
    public List<ScanGraph.Snapshot> findSnapshots(final Scan.ScanId scanId) {
        final var id = id(scanId);
        final var prefix = id + "|";

        final var query = String.join("\nUNION ALL\n", COMPARED_LABELS.stream()
            .map(label -> "MATCH (n:" + label + " {scanId: $scanId}) RETURN '" + label + "' AS type, properties(n) AS properties")
            .toList());

        final var snapshots = new ArrayList<ScanGraph.Snapshot>(read(
            query,
            Map.of("scanId", id),
            record -> {
                final var type = record.get("type").asString();
                final var properties = record.get("properties").asMap();

                return new ScanGraph.Snapshot(
                    key(prefix, (String) properties.get("id")),
                    type,
                    nodeName(type, properties),
                    visible(properties)
                );
            }
        ));

        // A module may declare a library more than once, such as for
        // development and as a peer, each with a version of its own. It is
        // one dependency with all of them, in one order: two with the same
        // key would have either compared, whichever was read first, and so
        // show as changed between scans of the same source
        snapshots.addAll(read("""
                MATCH (module:Module {scanId: $scanId})-[dependsOn:DEPENDS_ON]->(dependency:Dependency)
                WITH module, dependency.name AS name, dependsOn.scope AS scope, dependency.version AS version
                ORDER BY module.id, name, scope, version
                WITH module, name, collect(scope) AS scopes, collect(version) AS versions
                RETURN module.id AS module, name, scopes, versions
                """,
            Map.of("scanId", id),
            record -> {
                final var properties = new LinkedHashMap<String, Object>();
                properties.put("name", string(record.get("name")));
                properties.put("scope", joined(record.get("scopes")));
                properties.put("version", joined(record.get("versions")));
                properties.values().removeIf(Objects::isNull);

                return new ScanGraph.Snapshot(
                    key(prefix, record.get("module").asString()) + "|dependency:" + string(record.get("name")),
                    "Dependency",
                    string(record.get("name")),
                    properties
                );
            }
        ));

        return snapshots;
    }

    /**
     * The texts as one, each once, in their order: {@code dev, peer}; null
     * for none.
     */
    private static String joined(final Value values) {
        final var distinct = values.asList(Value::asString).stream().distinct().toList();

        return distinct.isEmpty() ? null : String.join(", ", distinct);
    }

    /**
     * The id without the scan's, which every id below a scan starts with.
     */
    private static String key(final String prefix, final String id) {
        return id.startsWith(prefix) ? id.substring(prefix.length()) : id;
    }

    private <T> List<T> read(
        final String query,
        final Map<String, Object> parameters,
        final Function<Record, T> mapper
    ) {
        try (final var session = driver.session()) {
            return session.executeRead(tx -> tx.run(query, parameters).list(mapper::apply));
        }
    }

    private static List<ScanGraph.Annotation> annotations(final Value value) {
        return value.isNull() ? List.of() : value.asList(ScanGraphRepositoryImpl::annotation);
    }

    private static ScanGraph.Annotation annotation(final Value value) {
        return new ScanGraph.Annotation(
            string(value.get("name")),
            string(value.get("qualifiedName")),
            string(value.get("arguments")),
            string(value.get("parameter"))
        );
    }

    private static List<String> strings(final Value value) {
        return value.isNull() ? List.of() : value.asList(Value::asString);
    }

    private static String string(final Value value) {
        return value.isNull() ? null : value.asString();
    }

    private static String id(final Scan.ScanId scanId) {
        return scanId.id().toString();
    }
}
