package dev.graphnous.persistence.scan.result;

import dev.graphnous.application.enhancer.EnhancementRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.enhancer.Enhancements;
import org.neo4j.driver.Driver;
import org.neo4j.driver.TransactionContext;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stores what enhancers added to a scan in the graph of its stored results,
 * in a transaction of its own:
 * <pre>
 * (Scan)-[:HAS_ENHANCEMENT]->(Enhancement {name, version})-[:ADDED]->(ENHANCED)
 * (source)-[:ENHANCE]->(ENHANCED)
 * </pre>
 * <ul>
 *     <li>each enhancer that ran becomes an {@code Enhancement} node with
 *     its name and version;</li>
 *     <li>each node it added becomes an {@code ENHANCED} node with the
 *     enhancer's labels and its metadata as properties, enhancing the node
 *     of the scan it names as its source;</li>
 *     <li>each relationship connects the nodes with its ids, whether the
 *     scan result or an enhancer added them.</li>
 * </ul>
 * A source or relationship end the scan does not have is left out. Every
 * node carries the {@code scanId}, so it is deleted with the results.
 * <p>
 * Enhancers identify nodes within the scan, e.g.
 * {@code backend|orders|class:com.example.Order}; stored ids extend the
 * scan's id, as those of {@link ScanResultGraph} do. Nodes and relationships
 * are merged, so what several results or enhancers add is added once.
 */
@Repository
public class EnhancementRepositoryImpl implements EnhancementRepository {

    static final String ENHANCEMENT = "Enhancement";
    /**
     * The label every node an enhancer adds has, besides its own.
     */
    public static final String ENHANCED = "ENHANCED";

    private static final int BATCH_SIZE = 2_000;

    /**
     * Labels and relationship types are part of the query, as Cypher has no
     * parameters for them.
     */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Relationships connect the nodes of one scan only.
     */
    private static final String SCAN_NODE = String.join("|", ScanResultRepositoryImpl.SCAN_LABELS);

    private final Driver driver;

    public EnhancementRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public void save(
        final Scan.ScanId scanId,
        final List<Enhancements> enhancements
    ) {
        if (enhancements.isEmpty()) {
            return;
        }

        final var id = scanId.id().toString();

        final var enhancers = new LinkedHashMap<String, Map<String, Object>>();
        final var nodes = new LinkedHashMap<String, List<Map<String, Object>>>();
        final var relationships = new LinkedHashMap<String, List<Map<String, Object>>>();

        for (final var enhancer : enhancements) {
            final var enhancementId = id + "|enhancement:" + enhancer.name() + "@" + enhancer.version();

            enhancers.put(enhancementId, Map.of(
                "id", enhancementId,
                "name", enhancer.name(),
                "version", enhancer.version()
            ));

            for (final var enhancement : enhancer.enhancements()) {
                for (final var node : enhancement.nodes()) {
                    final var labels = node.labels()
                        .stream()
                        .distinct()
                        .map(label -> ":" + name(label, "label"))
                        .collect(Collectors.joining());

                    final var row = new HashMap<String, Object>();
                    row.put("id", id + "|" + node.id());
                    row.put("sourceId", node.sourceId() == null ? null : id + "|" + node.sourceId());
                    row.put("enhancementId", enhancementId);
                    row.put("properties", node.metadata());

                    nodes.computeIfAbsent(labels, key -> new ArrayList<>()).add(row);
                }

                for (final var relationship : enhancement.relationships()) {
                    relationships.computeIfAbsent(name(relationship.type(), "relationship type"), key -> new ArrayList<>()).add(Map.of(
                        "sourceId", id + "|" + relationship.sourceId(),
                        "targetId", id + "|" + relationship.targetId()
                    ));
                }
            }
        }

        try (final var session = driver.session()) {
            session.executeWriteWithoutResult(tx -> {
                write(tx, id, List.copyOf(enhancers.values()), """
                    MATCH (scan:Scan {id: $scanId})
                    UNWIND $rows AS row
                    MERGE (enhancement:%s {id: row.id})
                    SET enhancement.name = row.name, enhancement.version = row.version, enhancement.scanId = $scanId
                    MERGE (scan)-[:HAS_ENHANCEMENT]->(enhancement)
                    """.formatted(ENHANCEMENT));

                nodes.forEach((labels, rows) -> write(tx, id, rows, """
                    UNWIND $rows AS row
                    MATCH (enhancement:%1$s {id: row.enhancementId})
                    MERGE (node:%2$s {id: row.id})
                    SET %3$snode += row.properties, node.scanId = $scanId
                    MERGE (enhancement)-[:ADDED]->(node)
                    WITH node, row
                    MATCH (source:%4$s {id: row.sourceId})
                    MERGE (source)-[:ENHANCE]->(node)
                    """.formatted(ENHANCEMENT, ENHANCED, labels.isEmpty() ? "" : "node" + labels + ", ", SCAN_NODE)));

                relationships.forEach((type, rows) -> write(tx, id, rows, """
                    UNWIND $rows AS row
                    MATCH (source:%1$s {id: row.sourceId})
                    MATCH (target:%1$s {id: row.targetId})
                    MERGE (source)-[:%2$s]->(target)
                    """.formatted(SCAN_NODE, type)));
            });
        }
    }

    private static String name(
        final String name,
        final String what
    ) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid " + what + " " + name + "; expected letters, digits and _");
        }

        return "`" + name + "`";
    }

    private static void write(
        final TransactionContext tx,
        final String scanId,
        final List<Map<String, Object>> rows,
        final String query
    ) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            final var batch = rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()));

            tx.run(query, Map.of("scanId", scanId, "rows", batch)).consume();
        }
    }
}
