package dev.graphnous.persistence.scan.links;

import dev.graphnous.application.scan.links.HttpLinkRepository;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.result.EnhancementRepositoryImpl;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads the HTTP calls and endpoints enhancers added to scans, as
 * EnhancementRepositoryImpl stores them: nodes labelled {@code HttpCall}
 * and {@code Endpoint}, whichever enhancer added them. Stores the links
 * between them as
 * <pre>
 * (HttpCall)-[:REQUESTS {match, matcher, crossesProjects}]->(Endpoint)
 * </pre>
 * and on each scan's node the version of the matching its links were
 * stored with.
 */
@Repository
public class HttpLinkRepositoryImpl implements HttpLinkRepository {

    /**
     * The relationship of a call to the endpoint it requests.
     */
    public static final String REQUESTS = "REQUESTS";

    /**
     * The property of a scan's node that holds the version of the
     * matching its links were stored with.
     */
    public static final String LINKED_WITH = "httpLinksMatcher";

    private final Driver driver;

    public HttpLinkRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public List<HttpLinks.Call> findCalls(final List<Scan> scans) {
        return find(scans, "HttpCall", "node.httpMethod AS httpMethod, node.url AS url, node.client AS client",
            (record, scan) -> new HttpLinks.Call(
                string(record.get("id")),
                scan.id(),
                scan.projectId(),
                string(record.get("httpMethod")),
                string(record.get("url")),
                string(record.get("client"))
            ));
    }

    @Override
    public List<HttpLinks.Endpoint> findEndpoints(final List<Scan> scans) {
        return find(scans, "Endpoint", "node.httpMethod AS httpMethod, node.url AS url",
            (record, scan) -> new HttpLinks.Endpoint(
                string(record.get("id")),
                scan.id(),
                scan.projectId(),
                string(record.get("httpMethod")),
                string(record.get("url"))
            ));
    }

    @Override
    public Map<String, HttpLinks.Source> findSources(final List<Scan> scans, final Collection<String> ids) {
        if (scans.isEmpty() || ids.isEmpty()) {
            return Map.of();
        }

        final var sources = new HashMap<String, HttpLinks.Source>();

        try (final var session = driver.session()) {
            session.executeRead(tx -> tx.run(
                """
                    UNWIND $ids AS id
                    MATCH (source)-[:ENHANCE]->(node:%s {id: id})
                    WHERE node.scanId IN $scanIds AND (source:Method OR source:Class)
                    OPTIONAL MATCH (owner:Class)-[:HAS_METHOD]->(source)
                    RETURN node.id AS id, source.id AS sourceId, labels(source)[0] AS type, source.name AS name,
                           source.qualifiedName AS qualifiedName, owner.qualifiedName AS className,
                           // A method's file declares its class; a function's or a class's declares it
                           head(COLLECT {
                               MATCH (file:File)-[:DECLARES]->(declared)
                               WHERE declared = source OR declared = owner
                               RETURN file.path
                           }) AS file,
                           coalesce(node.line, node.startLine) AS line
                    """.formatted(EnhancementRepositoryImpl.ENHANCED),
                Map.of("ids", List.copyOf(ids), "scanIds", scans.stream().map(scan -> scan.id().id().toString()).toList())
            ).list()).forEach(record -> sources.putIfAbsent(
                record.get("id").asString(),
                new HttpLinks.Source(
                    string(record.get("sourceId")),
                    string(record.get("type")),
                    string(record.get("name")),
                    string(record.get("qualifiedName")),
                    string(record.get("className")),
                    string(record.get("file")),
                    record.get("line").isNull() ? null : record.get("line").asInt()
                )
            ));
        }

        return sources;
    }

    @Override
    public List<HttpLinks.Link> findStoredLinks(final List<Scan> scans) {
        if (scans.isEmpty()) {
            return List.of();
        }

        final var byId = byId(scans);

        try (final var session = driver.session()) {
            return session.executeRead(tx -> tx.run(
                """
                    MATCH (call:%1$s:HttpCall)-[link:%2$s]->(endpoint:%1$s:Endpoint)
                    WHERE call.scanId IN $scanIds AND endpoint.scanId IN $scanIds
                    RETURN call.id AS callId, call.scanId AS callScanId, call.httpMethod AS callMethod,
                           call.url AS callUrl, call.client AS client,
                           endpoint.id AS endpointId, endpoint.scanId AS endpointScanId,
                           endpoint.httpMethod AS endpointMethod, endpoint.url AS endpointUrl,
                           link.match AS match
                    ORDER BY callId, endpointId
                    """.formatted(EnhancementRepositoryImpl.ENHANCED, REQUESTS),
                Map.of("scanIds", List.copyOf(byId.keySet()))
            ).list(record -> {
                final var callScan = byId.get(record.get("callScanId").asString());
                final var endpointScan = byId.get(record.get("endpointScanId").asString());

                return new HttpLinks.Link(
                    new HttpLinks.Call(
                        string(record.get("callId")),
                        callScan.id(),
                        callScan.projectId(),
                        string(record.get("callMethod")),
                        string(record.get("callUrl")),
                        string(record.get("client"))
                    ),
                    new HttpLinks.Endpoint(
                        string(record.get("endpointId")),
                        endpointScan.id(),
                        endpointScan.projectId(),
                        string(record.get("endpointMethod")),
                        string(record.get("endpointUrl"))
                    ),
                    HttpLinks.Match.valueOf(record.get("match").asString())
                );
            }));
        }
    }

    @Override
    public Map<Scan.ScanId, String> findLinkedWith(final List<Scan> scans) {
        if (scans.isEmpty()) {
            return Map.of();
        }

        final var byId = byId(scans);
        final var linkedWith = new HashMap<Scan.ScanId, String>();

        try (final var session = driver.session()) {
            session.executeRead(tx -> tx.run(
                """
                    MATCH (scan:Scan)
                    WHERE scan.id IN $scanIds AND scan.%s IS NOT NULL
                    RETURN scan.id AS id, scan.%1$s AS matcher
                    """.formatted(LINKED_WITH),
                Map.of("scanIds", List.copyOf(byId.keySet()))
            ).list()).forEach(record ->
                linkedWith.put(byId.get(record.get("id").asString()).id(), record.get("matcher").asString())
            );
        }

        return linkedWith;
    }

    @Override
    public void replaceLinks(
        final Scan.ScanId scanId,
        final List<HttpLinks.Link> links,
        final String matcherVersion
    ) {
        final var id = scanId.id().toString();
        final var rows = links.stream()
            .map(link -> Map.<String, Object>of(
                "call", link.call().id(),
                "endpoint", link.endpoint().id(),
                "match", link.match().name(),
                "crossesProjects", link.crossesProjects()
            ))
            .toList();

        try (final var session = driver.session()) {
            session.executeWriteWithoutResult(tx -> {
                // From its calls and to its endpoints, to whichever scan
                tx.run("""
                    MATCH (node:%1$s {scanId: $scanId})-[link:%2$s]-()
                    DELETE link
                    """.formatted(EnhancementRepositoryImpl.ENHANCED, REQUESTS), Map.of("scanId", id)).consume();

                tx.run("""
                    UNWIND $rows AS row
                    MATCH (call:%1$s:HttpCall {id: row.call})
                    MATCH (endpoint:%1$s:Endpoint {id: row.endpoint})
                    MERGE (call)-[link:%2$s]->(endpoint)
                    SET link.match = row.match, link.matcher = $matcher, link.crossesProjects = row.crossesProjects
                    """.formatted(EnhancementRepositoryImpl.ENHANCED, REQUESTS),
                    Map.of("rows", rows, "matcher", matcherVersion)).consume();

                tx.run("""
                    MATCH (scan:Scan {id: $scanId})
                    SET scan.%s = $matcher
                    """.formatted(LINKED_WITH), Map.of("scanId", id, "matcher", matcherVersion)).consume();
            });
        }
    }

    private static Map<String, Scan> byId(final List<Scan> scans) {
        return scans.stream()
            .collect(Collectors.toMap(scan -> scan.id().id().toString(), scan -> scan, (first, second) -> first));
    }

    /**
     * The nodes enhancers added to the scans with the label, by id so the
     * same scans give the same order.
     */
    private <T> List<T> find(
        final List<Scan> scans,
        final String label,
        final String returns,
        final BiFunction<Record, Scan, T> mapper
    ) {
        if (scans.isEmpty()) {
            return List.of();
        }

        final Map<String, Scan> byId = scans.stream()
            .collect(Collectors.toMap(scan -> scan.id().id().toString(), scan -> scan, (first, second) -> first));

        try (final var session = driver.session()) {
            return session.executeRead(tx -> tx.run(
                """
                    MATCH (node:%s:%s)
                    WHERE node.scanId IN $scanIds
                    RETURN node.id AS id, node.scanId AS scanId, %s
                    ORDER BY id
                    """.formatted(EnhancementRepositoryImpl.ENHANCED, label, returns),
                Map.of("scanIds", List.copyOf(byId.keySet()))
            ).list(record -> mapper.apply(record, byId.get(record.get("scanId").asString()))));
        }
    }
}
