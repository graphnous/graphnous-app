package dev.graphnous.persistence.scan.result;

import dev.graphnous.application.scan.links.HttpLinks;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.Node;
import dev.graphnous.persistence.scan.links.HttpLinkRepositoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The links HttpLinkRepositoryImpl stores between the HTTP calls and the
 * endpoints EnhancementRepositoryImpl stores. Runs against Neo4j in a
 * container; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class HttpLinkStorageTest {

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static Driver driver;

    private EnhancementRepositoryImpl enhancements;
    private HttpLinkRepositoryImpl repository;

    private final Scan frontend = scan();
    private final Scan backend = scan();

    @BeforeAll
    static void connect() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    static void disconnect() {
        driver.close();
    }

    @BeforeEach
    void store() {
        query("MATCH (n) DETACH DELETE n");

        final var results = new ScanResultRepositoryImpl(driver);
        enhancements = new EnhancementRepositoryImpl(driver);
        repository = new HttpLinkRepositoryImpl(driver);

        for (final var scan : List.of(frontend, backend)) {
            query("CREATE (:Scan {id: '" + scan.id().id() + "'})");
            results.save(scan.id(), List.of(ScanResults.orders()));
        }

        enhancements.save(frontend.id(), List.of(new Enhancements("fetch-browser", "1.1.0", List.of(new Enhancement(
            List.of(new Node("web|call", null, List.of("HttpCall"), Map.of("client", "fetch", "httpMethod", "GET", "url", "/orders"))),
            List.of()
        )))));
        enhancements.save(backend.id(), List.of(new Enhancements("spring", "1.0.0", List.of(new Enhancement(
            List.of(new Node("api|endpoint", null, List.of("Endpoint"), Map.of("httpMethod", "GET", "url", "/orders"))),
            List.of()
        )))));
    }

    @Test
    void storesTheLinksOfAScanAndReadsThemBack() {
        final var link = link();

        repository.replaceLinks(frontend.id(), List.of(link), "2");

        assertThat(repository.findStoredLinks(List.of(frontend, backend))).containsExactly(link);
        assertThat(repository.findLinkedWith(List.of(frontend, backend))).isEqualTo(Map.of(frontend.id(), "2"));
        assertThat(count("""
            MATCH (:HttpCall)-[link:REQUESTS {match: 'PATH', matcher: '2', crossesProjects: true}]->(:Endpoint)
            RETURN count(link)
            """)).isEqualTo(1);
    }

    @Test
    void readsOnlyTheLinksBetweenTheScansAskedFor() {
        repository.replaceLinks(frontend.id(), List.of(link()), "2");

        assertThat(repository.findStoredLinks(List.of(frontend))).isEmpty();
    }

    @Test
    void replacesTheLinksFromItsCallsAndToItsEndpoints() {
        repository.replaceLinks(frontend.id(), List.of(link()), "2");

        // As when the backend is found to have none: the frontend's link to it goes
        repository.replaceLinks(backend.id(), List.of(), "2");

        assertThat(repository.findStoredLinks(List.of(frontend, backend))).isEmpty();
        assertThat(repository.findLinkedWith(List.of(frontend, backend)))
            .isEqualTo(Map.of(frontend.id(), "2", backend.id(), "2"));
    }

    @Test
    void forgetsTheLinksOfAScanEnhancedAgain() {
        repository.replaceLinks(frontend.id(), List.of(link()), "2");

        enhancements.replace(frontend.id(), List.of(new Enhancements("fetch-browser", "1.1.0", List.of(new Enhancement(
            List.of(new Node("web|call", null, List.of("HttpCall"), Map.of("client", "fetch", "url", "/orders"))),
            List.of()
        )))));

        // Gone with the call's node, and to be found again
        assertThat(repository.findStoredLinks(List.of(frontend, backend))).isEmpty();
        assertThat(repository.findLinkedWith(List.of(frontend, backend))).isEmpty();
    }

    private HttpLinks.Link link() {
        return new HttpLinks.Link(
            new HttpLinks.Call(frontend.id().id() + "|web|call", frontend.id(), frontend.projectId(), "GET", "/orders", "fetch"),
            new HttpLinks.Endpoint(backend.id().id() + "|api|endpoint", backend.id(), backend.projectId(), "GET", "/orders"),
            HttpLinks.Match.PATH
        );
    }

    private static Scan scan() {
        final var now = Instant.now();

        return new Scan(
            Scan.ScanId.generate(), Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("abc123", "main"), now, now, now
        );
    }

    private static long count(final String cypher) {
        try (final var session = driver.session()) {
            return session.run(cypher).single().get(0).asLong();
        }
    }

    private static void query(final String cypher) {
        try (final var session = driver.session()) {
            session.run(cypher).consume();
        }
    }
}
