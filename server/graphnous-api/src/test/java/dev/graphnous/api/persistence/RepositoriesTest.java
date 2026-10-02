package dev.graphnous.api.persistence;

import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.application.scan.ScanStepRepository;
import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.application.system.SystemRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;
import dev.graphnous.domain.scan.log.ScanLog;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The repositories against their real stores: the relational database
 * (in-memory H2, as configured) and Neo4j in a container. Skipped when
 * Docker is not available.
 * <p>
 * The repositories live in graphnous-persistence; they are tested here, where
 * the application wires them.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class RepositoriesTest {

    private static final String NEO4J_PASSWORD = "repositories-test";

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withAdminPassword(NEO4J_PASSWORD);

    @DynamicPropertySource
    static void neo4j(final DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> NEO4J_PASSWORD);
    }

    @Autowired
    private SystemRepository systemRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private ScanLogRepository scanLogRepository;

    @Autowired
    private ScanStepRepository scanStepRepository;

    @Autowired
    private Driver driver;

    // Each test works in its own organization, so tests do not see each other's data
    private final OrganizationId organization = new OrganizationId(UUID.randomUUID());

    // Systems

    @Test
    void findsASystemInItsOrganizationOnly() {
        final var system = system("Shop");

        assertThat(systemRepository.findById(organization, system.id()).name()).isEqualTo("Shop");

        final var otherOrganization = new OrganizationId(UUID.randomUUID());

        assertThatThrownBy(() -> systemRepository.findById(otherOrganization, system.id()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void pagesAndSortsSystems() {
        system("b");
        system("c");
        system("a");

        final var firstPage = systemRepository.findAll(organization, query(0, 2, "name", Sort.Direction.DESC));

        assertThat(firstPage.content()).extracting(System::name).containsExactly("c", "b");
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        assertThat(systemRepository.findAll(organization, query(1, 2, "name", Sort.Direction.DESC)).content())
            .extracting(System::name).containsExactly("a");
        assertThat(systemRepository.count(organization)).isEqualTo(3);
    }

    @Test
    void deletesASystem() {
        final var system = system("Shop");

        systemRepository.delete(organization, system.id());

        assertThatThrownBy(() -> systemRepository.findById(organization, system.id()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void storesASystemInTheGraph() {
        final var system = system("Shop");

        assertThat(count("MATCH (s:System {id: $id}) RETURN count(s)", system.id().id())).isEqualTo(1);
    }

    // Projects

    @Test
    void listsTheProjectsOfASystemOnly() {
        final var shop = system("Shop");
        final var other = system("Other");

        final var backend = project(shop, "backend");
        project(shop, "frontend");
        project(other, "unrelated");

        final var page = projectRepository.findAll(shop.id(), query(0, 10, "name", Sort.Direction.ASC));

        assertThat(page.content()).extracting(Project::name).containsExactly("backend", "frontend");
        assertThat(projectRepository.count(shop.id())).isEqualTo(2);
        assertThat(projectRepository.findIdsBySystemId(shop.id())).hasSize(2).contains(backend.id());
    }

    @Test
    void linksAProjectToItsSystemInTheGraph() {
        final var shop = system("Shop");
        final var backend = project(shop, "backend");

        assertThat(count(
            "MATCH (:System {id: $systemId})-[:HAS_PROJECT]->(p:Project {id: $id}) RETURN count(p)",
            backend.id().id(),
            Map.of("systemId", shop.id().id().toString())
        )).isEqualTo(1);
    }

    @Test
    void deletesAProjectOfItsOwnSystemOnly() {
        final var shop = system("Shop");
        final var other = system("Other");
        final var backend = project(shop, "backend");

        assertThatThrownBy(() -> projectRepository.delete(other.id(), backend.id()))
            .isInstanceOf(NotFoundException.class);

        projectRepository.delete(shop.id(), backend.id());

        assertThatThrownBy(() -> projectRepository.findById(backend.id()))
            .isInstanceOf(NotFoundException.class);
        assertThat(count("MATCH (p:Project {id: $id}) RETURN count(p)", backend.id().id())).isZero();
    }

    @Test
    void reusesTheSnapshotOfAProject() {
        final var backend = project(system("Shop"), "backend");

        final var first = projectRepository.getOrCreateSnapshot(backend.id());
        final var second = projectRepository.getOrCreateSnapshot(backend.id());

        assertThat(second).isEqualTo(first);
    }

    // Scans

    @Test
    void findsTheActiveScans() {
        final var backend = project(system("Shop"), "backend");

        final var pending = scan(backend, Scan.ScanStatus.PENDING);
        final var queued = scan(backend, Scan.ScanStatus.QUEUED);
        final var running = scan(backend, Scan.ScanStatus.RUNNING);
        scan(backend, Scan.ScanStatus.COMPLETED);
        scan(backend, Scan.ScanStatus.FAILED);

        assertThat(scanRepository.findActive())
            .extracting(Scan::id)
            .contains(pending.id(), queued.id(), running.id())
            .allSatisfy(id -> assertThat(scanRepository.findById(id).status().isEndState()).isFalse());
    }

    @Test
    void tellsWhetherAProjectHasActiveScans() {
        final var shop = system("Shop");
        final var idle = project(shop, "idle");
        final var busy = project(shop, "busy");

        scan(idle, Scan.ScanStatus.COMPLETED);
        scan(idle, Scan.ScanStatus.FAILED);
        scan(busy, Scan.ScanStatus.COMPLETED);
        scan(busy, Scan.ScanStatus.RUNNING);

        assertThat(scanRepository.hasActiveScans(idle.id())).isFalse();
        assertThat(scanRepository.hasActiveScans(busy.id())).isTrue();
    }

    @Test
    void pagesTheScansOfAProject() {
        final var shop = system("Shop");
        final var backend = project(shop, "backend");
        final var frontend = project(shop, "frontend");

        final var first = scan(backend, Scan.ScanStatus.COMPLETED, Instant.now().minusSeconds(60));
        final var second = scan(backend, Scan.ScanStatus.COMPLETED, Instant.now());
        scan(frontend, Scan.ScanStatus.COMPLETED);

        final var page = scanRepository.findAll(backend.id(), query(0, 10, "createdAt", Sort.Direction.DESC));

        assertThat(page.content()).extracting(Scan::id).containsExactly(second.id(), first.id());
        assertThat(scanRepository.count(backend.id())).isEqualTo(2);
        assertThat(scanRepository.findIdsByProjectId(backend.id())).containsExactlyInAnyOrder(first.id(), second.id());
    }

    @Test
    void keepsTheScanStatusInTheGraph() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.PENDING);

        scanRepository.startScan(projectRepository.getOrCreateSnapshot(backend.id()), scan.id());
        scanRepository.save(withStatus(scan, Scan.ScanStatus.RUNNING));

        assertThat(count(
            "MATCH (s:Scan {id: $id, status: 'RUNNING'}) RETURN count(s)",
            scan.id().id()
        )).isEqualTo(1);
    }

    @Test
    void storesWhenAScanStarted() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.QUEUED);

        final var running = withStatus(scan, Scan.ScanStatus.RUNNING);
        scanRepository.save(running);

        assertThat(scanRepository.findById(scan.id()).startedAt()).isEqualTo(running.startedAt()).isNotNull();
    }

    @Test
    void deletesAScanWithItsGraphNode() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.COMPLETED);
        scanRepository.startScan(projectRepository.getOrCreateSnapshot(backend.id()), scan.id());

        scanRepository.delete(scan.id());

        assertThatThrownBy(() -> scanRepository.findById(scan.id())).isInstanceOf(NotFoundException.class);
        assertThat(count("MATCH (s:Scan {id: $id}) RETURN count(s)", scan.id().id())).isZero();
    }

    // Scan logs

    @Test
    void readsTheLogsOfAScanInSequence() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.RUNNING);
        final var other = scan(backend, Scan.ScanStatus.RUNNING);

        log(scan, 2, "second");
        log(scan, 1, "first");
        log(scan, 3, "third");
        log(other, 1, "elsewhere");

        assertThat(scanLogRepository.findByScanId(scan.id()))
            .extracting(ScanLog::message).containsExactly("first", "second", "third");
        assertThat(scanLogRepository.findByScanIdAfter(scan.id(), 1))
            .extracting(ScanLog::message).containsExactly("second", "third");
        assertThat(scanLogRepository.findLatestSequence(scan.id())).hasValue(3);
    }

    @Test
    void hasNoLatestSequenceForAScanWithoutLogs() {
        final var scan = scan(project(system("Shop"), "backend"), Scan.ScanStatus.PENDING);

        assertThat(scanLogRepository.findLatestSequence(scan.id())).isEmpty();
    }

    @Test
    void deletesTheLogsOfOneScan() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.COMPLETED);
        final var other = scan(backend, Scan.ScanStatus.COMPLETED);

        log(scan, 1, "gone");
        log(other, 1, "kept");

        scanLogRepository.deleteByScanId(scan.id());

        assertThat(scanLogRepository.findByScanId(scan.id())).isEmpty();
        assertThat(scanLogRepository.findByScanId(other.id())).extracting(ScanLog::message).containsExactly("kept");
    }

    // Scan steps

    @Test
    void readsTheStepsOfAScanInOrder() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.RUNNING);
        final var other = scan(backend, Scan.ScanStatus.RUNNING);

        // Saved out of order, read in the order they run
        scanStepRepository.saveAll(List.of(
            ScanStep.pending(scan.id(), ScanStep.ScanStepType.SCAN),
            ScanStep.pending(scan.id(), ScanStep.ScanStepType.CHECKOUT),
            ScanStep.pending(scan.id(), ScanStep.ScanStepType.PLAN),
            ScanStep.pending(other.id(), ScanStep.ScanStepType.CHECKOUT)
        ));

        assertThat(scanStepRepository.findByScanId(scan.id()))
            .extracting(ScanStep::type)
            .containsExactly(ScanStep.ScanStepType.CHECKOUT, ScanStep.ScanStepType.PLAN, ScanStep.ScanStepType.SCAN);
    }

    @Test
    void storesTheOutcomeOfAStep() {
        final var scan = scan(project(system("Shop"), "backend"), Scan.ScanStatus.RUNNING);
        final var started = Instant.now().truncatedTo(ChronoUnit.MICROS);

        final var failed = ScanStep.pending(scan.id(), ScanStep.ScanStepType.CHECKOUT)
            .start(started)
            .fail(started.plusSeconds(3), "Repository not found");

        scanStepRepository.save(failed);

        assertThat(scanStepRepository.findByScanId(scan.id())).containsExactly(failed);
    }

    @Test
    void deletesTheStepsOfOneScan() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.COMPLETED);
        final var other = scan(backend, Scan.ScanStatus.COMPLETED);

        scanStepRepository.saveAll(List.of(
            ScanStep.pending(scan.id(), ScanStep.ScanStepType.CHECKOUT),
            ScanStep.pending(other.id(), ScanStep.ScanStepType.CHECKOUT)
        ));

        scanStepRepository.deleteByScanId(scan.id());

        assertThat(scanStepRepository.findByScanId(scan.id())).isEmpty();
        assertThat(scanStepRepository.findByScanId(other.id())).hasSize(1);
    }

    private System system(final String name) {
        final var now = Instant.now();

        return systemRepository.save(
            organization,
            new System(System.SystemId.generate(), name, null, now, now)
        );
    }

    private Project project(final System system, final String name) {
        final var now = Instant.now();

        return projectRepository.save(new Project(
            Project.ProjectId.generate(),
            name,
            null,
            "https://example.com/" + name + ".git",
            null,
            system.id(),
            now,
            now
        ));
    }

    private Scan scan(final Project project, final Scan.ScanStatus status) {
        return scan(project, status, Instant.now());
    }

    private Scan scan(final Project project, final Scan.ScanStatus status, final Instant createdAt) {
        // Rounded as the database stores it, so saved and loaded scans compare equal
        final var at = createdAt.truncatedTo(ChronoUnit.MICROS);

        return scanRepository.save(new Scan(
            Scan.ScanId.generate(),
            project.id(),
            status,
            new Scan.SourceRevision("main", "main"),
            at,
            at,
            null
        ));
    }

    private static Scan withStatus(final Scan scan, final Scan.ScanStatus status) {
        return scan.withStatus(status, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    private void log(final Scan scan, final long sequence, final String message) {
        scanLogRepository.save(new ScanLog(
            ScanLog.ScanLogId.generate(),
            scan.id(),
            sequence,
            Instant.now(),
            ScanLog.ScanLogLevel.INFO,
            message
        ));
    }

    private static PageQuery query(final int page, final int size, final String property, final Sort.Direction direction) {
        return new PageQuery(page, size, new Sort(property, direction));
    }

    private long count(final String query, final UUID id) {
        return count(query, id, Map.of());
    }

    private long count(final String query, final UUID id, final Map<String, Object> parameters) {
        try (final var session = driver.session()) {
            final var all = new HashMap<String, Object>(parameters);
            all.put("id", id.toString());

            return session.run(query, all).single().get(0).asLong();
        }
    }
}
