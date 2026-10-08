package dev.graphnous.api.persistence;

import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.chat.ChatThreadRepository;
import dev.graphnous.application.notification.NotificationRepository;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.application.scan.ScanStepRepository;
import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.application.scan.stats.ScanStatRepository;
import dev.graphnous.application.system.SystemRepository;
import dev.graphnous.domain.chat.ChatThread;
import dev.graphnous.domain.notification.Notification;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;
import dev.graphnous.domain.scan.log.ScanLog;
import dev.graphnous.domain.scan.stats.ScanStats;
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
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The repositories against their real stores: PostgreSQL, with the Flyway
 * migrations, and Neo4j, each in a container. Skipped when Docker is not
 * available.
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

    // The schema comes from the Flyway migrations, which Hibernate validates
    // the entities against
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

    @DynamicPropertySource
    static void databases(final DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", NEO4J::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> NEO4J_PASSWORD);

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
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
    private ScanStatRepository scanStatRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ChatThreadRepository chatThreadRepository;

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

    private static final Map<String, List<String>> LANGUAGES = Map.of(
        "JAVA", List.of("backend/src/main/java/Order.java", "backend/src/main/java/Customer.java"),
        "TYPESCRIPT", List.of("web/src/index.ts")
    );

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
    void findsTheFinishedScansCreatedBeforeACutoff() {
        final var backend = project(system("Shop"), "backend");
        final var cutoff = Instant.now().minus(Duration.ofDays(30));

        final var completed = scan(backend, Scan.ScanStatus.COMPLETED, cutoff.minusSeconds(60));
        final var failed = scan(backend, Scan.ScanStatus.FAILED, cutoff.minusSeconds(60));
        scan(backend, Scan.ScanStatus.RUNNING, cutoff.minusSeconds(60));
        scan(backend, Scan.ScanStatus.COMPLETED, cutoff.plusSeconds(60));

        assertThat(scanRepository.findFinishedCreatedBefore(cutoff))
            .contains(completed, failed)
            .allSatisfy(scan -> {
                assertThat(scan.status().isEndState()).isTrue();
                assertThat(scan.createdAt()).isBefore(cutoff);
            });
    }

    @Test
    void findsTheOldestFinishedScansOfAProject() {
        final var shop = system("Shop");
        final var backend = project(shop, "backend");
        final var now = Instant.now();

        scan(backend, Scan.ScanStatus.RUNNING, now.minusSeconds(400));
        final var oldest = scan(backend, Scan.ScanStatus.FAILED, now.minusSeconds(300));
        final var older = scan(backend, Scan.ScanStatus.COMPLETED, now.minusSeconds(200));
        scan(backend, Scan.ScanStatus.COMPLETED, now.minusSeconds(100));
        scan(project(shop, "frontend"), Scan.ScanStatus.COMPLETED, now.minusSeconds(500));

        assertThat(scanRepository.findOldestFinished(backend.id(), 2)).containsExactly(oldest.id(), older.id());
        assertThat(scanRepository.findOldestFinished(backend.id(), 0)).isEmpty();
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

    // Scan stats

    @Test
    void findsTheStatsOfAScan() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.COMPLETED);

        final var stats = scanStatRepository.save(
            new ScanStats(ScanStats.ScanStatId.generate(), scan.id(), backend.id(), 2, LANGUAGES, 8, 31)
        );

        assertThat(scanStatRepository.findByScanId(scan.id())).contains(stats);
        assertThat(scanStatRepository.findByScanId(scan(backend, Scan.ScanStatus.COMPLETED).id())).isEmpty();
    }

    @Test
    void updatesTheStatsOfAScan() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.COMPLETED);
        final var id = ScanStats.ScanStatId.generate();

        scanStatRepository.save(new ScanStats(id, scan.id(), backend.id(), 1, Map.of("JAVA", List.of("Order.java")), 1, 1));
        scanStatRepository.save(new ScanStats(id, scan.id(), backend.id(), 2, LANGUAGES, 8, 31));

        assertThat(scanStatRepository.findByScanId(scan.id()))
            .contains(new ScanStats(id, scan.id(), backend.id(), 2, LANGUAGES, 8, 31));
    }

    @Test
    void deletesTheStatsOfOneScan() {
        final var backend = project(system("Shop"), "backend");
        final var scan = scan(backend, Scan.ScanStatus.COMPLETED);
        final var other = scan(backend, Scan.ScanStatus.COMPLETED);

        scanStatRepository.save(new ScanStats(ScanStats.ScanStatId.generate(), scan.id(), backend.id(), 1, Map.of("JAVA", List.of("Order.java")), 1, 1));
        scanStatRepository.save(new ScanStats(ScanStats.ScanStatId.generate(), other.id(), backend.id(), 1, Map.of("JAVA", List.of("Order.java")), 1, 1));

        scanStatRepository.deleteByScanId(scan.id());

        assertThat(scanStatRepository.findByScanId(scan.id())).isEmpty();
        assertThat(scanStatRepository.findByScanId(other.id())).isPresent();
    }

    private System system(final String name) {
        final var now = Instant.now();

        return systemRepository.save(
            organization,
            new System(System.SystemId.generate(), name, null, now, now)
        );
    }

    // Notifications

    @Test
    void pagesTheNotificationsOfASystemProjectAndScan() {
        final var shop = system("Shop");
        final var backend = project(shop, "backend");
        final var scan = scan(backend, Scan.ScanStatus.COMPLETED);

        final var ofSystem = notification(shop, null, null, Instant.now().minusSeconds(120));
        final var ofProject = notification(shop, backend, null, Instant.now().minusSeconds(60));
        final var ofScan = notification(shop, backend, scan, Instant.now());

        final var byDate = query(0, 10, "createdAt", Sort.Direction.ASC);

        assertThat(notificationRepository.findAll(shop.id(), byDate).content())
            .extracting(Notification::id)
            .containsExactly(ofSystem.id(), ofProject.id(), ofScan.id());
        assertThat(notificationRepository.findAll(backend.id(), byDate).content())
            .extracting(Notification::id)
            .containsExactly(ofProject.id(), ofScan.id());
        assertThat(notificationRepository.findAll(scan.id(), byDate).content())
            .extracting(Notification::id)
            .containsExactly(ofScan.id());

        assertThat(notificationRepository.findById(ofScan.id())).isEqualTo(ofScan);
    }

    @Test
    void storesWhetherANotificationIsRead() {
        final var shop = system("Shop");
        final var notification = notification(shop, null, null, Instant.now());

        notificationRepository.save(notification.withRead(true));

        assertThat(notificationRepository.findById(notification.id()).read()).isTrue();
    }

    @Test
    void deletesTheNotificationsOfAScanProjectOrSystem() {
        final var shop = system("Shop");
        final var backend = project(shop, "backend");
        final var scan = scan(backend, Scan.ScanStatus.COMPLETED);

        final var ofSystem = notification(shop, null, null, Instant.now());
        final var ofProject = notification(shop, backend, null, Instant.now());
        final var ofScan = notification(shop, backend, scan, Instant.now());

        notificationRepository.deleteByScanId(scan.id());

        assertThatThrownBy(() -> notificationRepository.findById(ofScan.id())).isInstanceOf(NotFoundException.class);
        assertThat(notificationRepository.findById(ofProject.id())).isEqualTo(ofProject);

        notificationRepository.deleteByProjectId(backend.id());

        assertThatThrownBy(() -> notificationRepository.findById(ofProject.id())).isInstanceOf(NotFoundException.class);
        assertThat(notificationRepository.findById(ofSystem.id())).isEqualTo(ofSystem);

        notificationRepository.deleteBySystemId(shop.id());

        assertThatThrownBy(() -> notificationRepository.findById(ofSystem.id())).isInstanceOf(NotFoundException.class);
    }

    // Chat threads

    @Test
    void keepsTheConversationOfAThread() {
        final var shop = system("Shop");
        final var thread = chatThreadRepository.save(ChatThread.start(shop.id(), UUID.randomUUID(), now()));

        final var messages = "[{\"id\":\"1\",\"role\":\"user\",\"content\":\"%s\"}]".formatted("x".repeat(5000));
        chatThreadRepository.save(thread.withMessages(messages, "Question", now()));

        assertThat(chatThreadRepository.findById(thread.id())).hasValueSatisfying(found -> {
            assertThat(found.messages()).isEqualTo(messages);
            assertThat(found.title()).isEqualTo("Question");
        });
    }

    @Test
    void pagesTheThreadsOfAUserAboutASystem() {
        final var shop = system("Shop");
        final var user = UUID.randomUUID();

        final var older = chatThreadRepository.save(ChatThread.start(shop.id(), user, now().minusSeconds(60)));
        final var newer = chatThreadRepository.save(ChatThread.start(shop.id(), user, now()));
        chatThreadRepository.save(ChatThread.start(shop.id(), UUID.randomUUID(), now()));
        final var anonymous = chatThreadRepository.save(ChatThread.start(shop.id(), null, now()));

        final var byLastUse = new PageQuery(0, 10, new Sort("updatedAt", Sort.Direction.DESC));

        assertThat(chatThreadRepository.findAll(shop.id(), user, byLastUse).content())
            .extracting(ChatThread::id)
            .containsExactly(newer.id(), older.id());
        assertThat(chatThreadRepository.findAll(shop.id(), null, byLastUse).content())
            .extracting(ChatThread::id)
            .containsExactly(anonymous.id());
    }

    @Test
    void deletesTheThreadsOfASystem() {
        final var shop = system("Shop");
        final var other = system("Other");

        final var ofShop = chatThreadRepository.save(ChatThread.start(shop.id(), null, now()));
        final var ofOther = chatThreadRepository.save(ChatThread.start(other.id(), null, now()));

        chatThreadRepository.deleteBySystemId(shop.id());

        assertThat(chatThreadRepository.findById(ofShop.id())).isEmpty();
        assertThat(chatThreadRepository.findById(ofOther.id())).isPresent();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private Notification notification(final System system, final Project project, final Scan scan, final Instant createdAt) {
        return notificationRepository.save(new Notification(
            Notification.NotificationId.generate(),
            system.id(),
            project == null ? null : project.id(),
            scan == null ? null : scan.id(),
            "Title",
            "Content",
            false,
            // Rounded as the database stores it, so saved and loaded notifications compare equal
            createdAt.truncatedTo(ChronoUnit.MICROS)
        ));
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
