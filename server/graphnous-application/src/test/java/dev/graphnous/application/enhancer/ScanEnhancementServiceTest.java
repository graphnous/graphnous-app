package dev.graphnous.application.enhancer;

import dev.graphnous.application.event.EventPublisher;
import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.scanner.ScanExecutor;
import dev.graphnous.application.scan.InMemoryScanStepRepository;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultArchive;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.EnhancerProvider;
import dev.graphnous.enhancer.EnhancerRegistry;
import dev.graphnous.enhancer.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.COMPLETED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.FAILED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.RUNNING;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.SKIPPED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.ENHANCE_RESULTS;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.ENHANCE_SCAN;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.STORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanEnhancementServiceTest {

    @Mock
    private ScanService scanService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ScanResultArchive scanResultArchive;

    @Mock
    private ScanResultRepository scanResultRepository;

    @Mock
    private EnhancementRepository enhancementRepository;

    @Mock
    private ScanLogService scanLogService;

    @Mock
    private EventPublisher eventPublisher;

    private final InMemoryScanStepRepository steps = new InMemoryScanStepRepository();
    private final ScanSteps scanSteps = new ScanSteps(steps, Clock.systemUTC());

    /**
     * The background tasks, to run when the test says so.
     */
    private final List<Runnable> tasks = new ArrayList<>();

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Scan scan = scan(Scan.ScanStatus.COMPLETED);

    private final ScanResult result = scanResult();

    @BeforeEach
    void setUp() {
        // As a completed scan leaves its steps
        scanSteps.create(scan.id());
        scanSteps.start(scan.id(), STORE);
        scanSteps.complete(scan.id(), STORE);
        scanSteps.start(scan.id(), ENHANCE_RESULTS);
        scanSteps.complete(scan.id(), ENHANCE_RESULTS);
        scanSteps.skip(scan.id(), ENHANCE_SCAN);

        lenient().when(scanService.getScan(context, scan.id())).thenReturn(scan);
        lenient().when(scanResultArchive.exists(scan.id())).thenReturn(true);
        lenient().when(scanResultArchive.load(scan.id())).thenReturn(List.of(result));
    }

    @Test
    void replacesWhatTheEnhancersAddedInTheBackground() {
        final var service = service(tasks::add, enhancer("spring"), enhancer("jpa"));

        assertThat(service.enhance(context, scan.id(), null, false)).isEqualTo(scan);

        verify(authorizationService).authorize(context, Permission.SCAN_CREATE);
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(RUNNING);
        verifyNoInteractions(enhancementRepository);

        tasks.forEach(Runnable::run);

        verify(enhancementRepository).replace(scan.id(), List.of(enhancements("spring"), enhancements("jpa")));
        verify(scanLogService).log(scan.id(), ScanLogLevel.INFO, "Enhanced the results again with spring 1.0.0: 1 node(s) and 0 relationship(s)");
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);
        assertThat(steps.step(scan.id(), ENHANCE_SCAN).status()).isEqualTo(SKIPPED);
    }

    @Test
    void runsOnlyTheEnhancersAskedFor() {
        service(Runnable::run, enhancer("spring"), enhancer("jpa")).enhance(context, scan.id(), Set.of("jpa"), false);

        verify(enhancementRepository).replace(scan.id(), List.of(enhancements("jpa")));
    }

    @Test
    void runsNoEnhancerWhenNoneIsChosen() {
        service(Runnable::run, enhancer("spring"), enhancer("jpa")).enhance(context, scan.id(), Set.of(), false);

        // Nothing to replace, so what the enhancers added before stays
        verify(enhancementRepository).replace(scan.id(), List.of());
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);
    }

    @Test
    void refusesAnEnhancerThatIsNotInstalled() {
        final var service = service(Runnable::run, enhancer("spring"));

        assertThatThrownBy(() -> service.enhance(context, scan.id(), Set.of("spring", "react"), false))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("react");

        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);
        verifyNoInteractions(enhancementRepository);
    }

    @Test
    void refusesAScanThatIsNotCompleted() {
        when(scanService.getScan(context, scan.id())).thenReturn(scan(Scan.ScanStatus.RUNNING));

        assertThatThrownBy(() -> service(Runnable::run, enhancer("spring")).enhance(context, scan.id(), null, false))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("RUNNING");

        verifyNoInteractions(enhancementRepository);
    }

    @Test
    void refusesAScanThatKeptNoResults() {
        when(scanResultArchive.exists(scan.id())).thenReturn(false);

        assertThatThrownBy(() -> service(Runnable::run, enhancer("spring")).enhance(context, scan.id(), null, false))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("kept no results");

        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);
    }

    @Test
    void refusesAScanThatIsAlreadyBeingEnhanced() {
        final var service = service(tasks::add, enhancer("spring"));

        service.enhance(context, scan.id(), null, false);

        assertThatThrownBy(() -> service.enhance(context, scan.id(), null, false))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already");

        // Once it is done, it can be enhanced again
        tasks.forEach(Runnable::run);
        service.enhance(context, scan.id(), null, false);

        assertThat(tasks).hasSize(2);
    }

    @Test
    void doesNotEnhanceAScanTheUserMayNotChange() {
        doThrow(new AuthorizationException("Not allowed")).when(authorizationService).authorize(context, Permission.SCAN_CREATE);

        assertThatThrownBy(() -> service(Runnable::run, enhancer("spring")).enhance(context, scan.id(), null, false))
            .isInstanceOf(AuthorizationException.class);

        verify(scanService, never()).getScan(any(), any());
    }

    @Test
    void failsTheStepWhenStoringFails() {
        doThrow(new IllegalStateException("Neo4j unavailable")).when(enhancementRepository).replace(any(), any());

        final var service = service(Runnable::run, enhancer("spring"));

        service.enhance(context, scan.id(), null, false);

        final var step = steps.step(scan.id(), ENHANCE_RESULTS);

        assertThat(step.status()).isEqualTo(FAILED);
        assertThat(step.error()).isEqualTo("Neo4j unavailable");
        verify(scanLogService).log(scan.id(), ScanLogLevel.WARN, "Enhancing again failed: Neo4j unavailable");

        // And can be tried again
        service.enhance(context, scan.id(), null, false);
    }

    @Test
    void runsAStepAgainThatFailedBefore() {
        doThrow(new IllegalStateException("Neo4j unavailable")).when(enhancementRepository).replace(any(), any());

        final var service = service(Runnable::run, enhancer("spring"));
        service.enhance(context, scan.id(), null, false);

        lenient().doNothing().when(enhancementRepository).replace(any(), any());
        service.enhance(context, scan.id(), null, false);

        final var step = steps.step(scan.id(), ENHANCE_RESULTS);

        assertThat(step.status()).isEqualTo(COMPLETED);
        assertThat(step.error()).isNull();
    }

    @Test
    void leavesTheStoredResultsAloneUnlessAskedToStoreThemAgain() {
        service(Runnable::run, enhancer("spring")).enhance(context, scan.id(), null, false);

        verifyNoInteractions(scanResultRepository);
    }

    @Test
    void storesTheKeptResultsAgainBeforeEnhancing() {
        final var service = service(tasks::add, enhancer("spring"));

        service.enhance(context, scan.id(), null, true);

        // Enhancing waits for the results
        assertThat(steps.step(scan.id(), STORE).status()).isEqualTo(RUNNING);
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);

        tasks.forEach(Runnable::run);

        final var order = inOrder(scanResultRepository, enhancementRepository);
        order.verify(scanResultRepository).save(scan.id(), List.of(result));
        order.verify(enhancementRepository).replace(scan.id(), List.of(enhancements("spring")));

        verify(scanLogService).log(scan.id(), ScanLogLevel.INFO, "Stored the results again");
        assertThat(steps.step(scan.id(), STORE).status()).isEqualTo(COMPLETED);
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);
    }

    @Test
    void doesNotEnhanceWhenStoringTheResultsAgainFails() {
        doThrow(new IllegalStateException("Neo4j unavailable")).when(scanResultRepository).save(any(), any());

        final var service = service(Runnable::run, enhancer("spring"));

        service.enhance(context, scan.id(), null, true);

        final var step = steps.step(scan.id(), STORE);

        assertThat(step.status()).isEqualTo(FAILED);
        assertThat(step.error()).isEqualTo("Neo4j unavailable");
        verify(scanLogService).log(scan.id(), ScanLogLevel.WARN, "Storing the results again failed: Neo4j unavailable");

        // What the enhancers added is still there, with the results
        verifyNoInteractions(enhancementRepository);
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);

        // And can be tried again
        service.enhance(context, scan.id(), null, true);
    }

    @Test
    void saysSoWhenNoEnhancerApplies() {
        service(Runnable::run).enhance(context, scan.id(), null, false);

        verify(enhancementRepository).replace(scan.id(), List.of());
        verify(scanLogService).log(eq(scan.id()), eq(ScanLogLevel.INFO), anyString());
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);
    }

    private ScanEnhancementService service(
        final ScanExecutor executor,
        final Enhancer... enhancers
    ) {
        final var registry = new EnhancerRegistry(List.of(enhancers));

        return new ScanEnhancementService(
            scanService,
            authorizationService,
            scanResultArchive,
            scanResultRepository,
            registry,
            new EnhancerProvider(registry),
            enhancementRepository,
            scanSteps,
            scanLogService,
            executor,
            eventPublisher
        );
    }

    private static Enhancer enhancer(final String name) {
        return new Enhancer() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public Enhancements enhance(final ScanResult scanResult) {
                return enhancements(name);
            }

            @Override
            public ScanTarget.Language forLanguage() {
                return ScanTarget.Language.JAVA;
            }
        };
    }

    private static Enhancements enhancements(final String name) {
        return new Enhancements(name, "1.0.0", List.of(new Enhancement(
            List.of(new Node("backend|.|class:com.acme.Orders|" + name, "backend|.|class:com.acme.Orders", List.of("Added"), Map.of())),
            List.of()
        )));
    }

    private static ScanResult scanResult() {
        final var target = new ScanTarget();
        target.setPath("backend");
        target.setLanguage(ScanTarget.Language.JAVA);

        final var result = new ScanResult();
        result.setTarget(target);

        return result;
    }

    private Scan scan(final Scan.ScanStatus status) {
        final var id = this.scan == null ? Scan.ScanId.generate() : this.scan.id();
        final var now = Instant.now();

        return new Scan(id, Project.ProjectId.generate(), status, new Scan.SourceRevision("abc123", "main"), now, now, now);
    }
}
