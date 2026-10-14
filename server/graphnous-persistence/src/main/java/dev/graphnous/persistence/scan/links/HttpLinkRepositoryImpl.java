package dev.graphnous.persistence.scan.links;

import dev.graphnous.application.scan.links.HttpLinkRepository;
import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.result.EnhancementRepositoryImpl;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads the HTTP calls and endpoints enhancers added to scans, as
 * EnhancementRepositoryImpl stores them: nodes labelled {@code HttpCall}
 * and {@code Endpoint}, whichever enhancer added them.
 */
@Repository
public class HttpLinkRepositoryImpl implements HttpLinkRepository {

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
