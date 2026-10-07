package dev.graphnous.application.project.scanner;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.enhancer.EnhancementRepository;
import dev.graphnous.application.event.EventPublisher;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.notification.ScanNotifier;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.InMemoryScanStepRepository;
import dev.graphnous.application.scan.ScanCompletedEvent;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;
import dev.graphnous.domain.system.System;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.EnhancerProvider;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;
import dev.graphnous.scanner.ScanFailure;
import dev.graphnous.scanner.ScanReport;
import dev.graphnous.scanner.plan.ScanPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.COMPLETED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.FAILED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepStatus.SKIPPED;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.CHECKOUT;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.ENHANCE_RESULTS;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.ENHANCE_SCAN;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.PLAN;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.SCAN;
import static dev.graphnous.domain.scan.ScanStep.ScanStepType.STORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectScannerTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ScanLogService scanLogService;

    @Mock
    private SourceCheckout sourceCheckout;

    @Mock
    private RepositoryScanner repositoryScanner;

    @Mock
    private ScanResultRepository scanResultRepository;

    @Mock
    private EnhancerProvider enhancerProvider;

    @Mock
    private EnhancementRepository enhancementRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ScanNotifier scanNotifier;

    private final InMemoryScanStepRepository steps = new InMemoryScanStepRepository();

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Project project = new Project(
        Project.ProjectId.generate(),
        "project",
        null,
        "https://example.com/project.git",
        null,
        new System.SystemId(UUID.randomUUID()),
        Instant.now(),
        Instant.now()
    );

    private final Scan scan = scan(Scan.ScanStatus.PENDING);

    private final ScanPlan plan = new ScanPlan(List.of(), List.of());

    @BeforeEach
    void setUp() {
        new ScanSteps(steps, Clock.systemUTC()).create(scan.id());

        lenient().when(projectService.getProject(context, project.id())).thenReturn(project);
        lenient().when(sourceCheckout.checkout(eq(scan.id()), anyString(), any(), any()))
            .thenReturn(Path.of("/checkouts/scan"));
        lenient().when(repositoryScanner.plan(any(), any())).thenReturn(plan);
    }

    @Test
    void completesEveryStepOfASuccessfulScan() {
        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), eq(Path.of("/checkouts/scan")), eq(plan), any()))
            .thenReturn(new ScanReport(List.of(), List.of()));

        start();

        verify(scanResultRepository).save(scan.id(), List.of());
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(COMPLETED, COMPLETED, COMPLETED, COMPLETED, COMPLETED, SKIPPED);
        assertThat(steps.step(scan.id(), CHECKOUT).startedAt()).isNotNull();

        final var order = inOrder(scanNotifier);
        order.verify(scanNotifier).scanStarted(scan);
        order.verify(scanNotifier).scanCompleted(scan);
        verify(scanNotifier, never()).scanFailed(any(), any());
    }

    @Test
    void announcesACompletedScanWithItsResults() {
        final var result = scanResult();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any()))
            .thenReturn(new ScanReport(List.of(result), List.of()));

        start();

        final var event = completedEvent();

        assertThat(event.getScanId()).isEqualTo(scan.id());
        assertThat(event.getProjectId()).isEqualTo(project.id());
        assertThat(event.getResults()).containsExactly(result);
    }

    @Test
    void failsTheCheckoutStepWhenCheckingOutFails() {
        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(sourceCheckout.checkout(eq(scan.id()), anyString(), any(), any()))
            .thenThrow(new IllegalStateException("Repository not found"));

        start();

        assertThat(steps.step(scan.id(), CHECKOUT).error()).isEqualTo("Repository not found");
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(FAILED, SKIPPED, SKIPPED, SKIPPED, SKIPPED, SKIPPED);
        verify(repositoryScanner, never()).plan(any(), any());
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.FAILED);
        verify(scanNotifier).scanFailed(scan, "Repository not found");
        verify(scanNotifier, never()).scanCompleted(any());
    }

    @Test
    void failsThePlanStepWhenPlanningFails() {
        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.plan(any(), any())).thenThrow(new IllegalStateException("Unreadable pom.xml"));

        start();

        assertThat(steps.step(scan.id(), PLAN).error()).isEqualTo("Unreadable pom.xml");
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(COMPLETED, FAILED, SKIPPED, SKIPPED, SKIPPED, SKIPPED);
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.FAILED);
    }

    @Test
    void failsTheScanStepWhenNoTargetCouldBeScanned() {
        final var result = scanResult();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenReturn(new ScanReport(
            List.of(),
            List.of(new ScanFailure(result.getTarget(), new IllegalStateException("Scanner failed with exit code 1")))
        ));

        start();

        assertThat(steps.step(scan.id(), SCAN).error())
            .isEqualTo("No target could be scanned: JAVA backend: Scanner failed with exit code 1");
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(COMPLETED, COMPLETED, FAILED, SKIPPED, SKIPPED, SKIPPED);
        verify(scanResultRepository, never()).save(any(), any());
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.FAILED);
    }

    @Test
    void completesTheScanStepWhenSomeTargetsCouldBeScanned() {
        final var result = scanResult();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenReturn(new ScanReport(
            List.of(result),
            List.of(new ScanFailure(new ScanTarget(), new IllegalStateException("Scanner failed")))
        ));

        start();

        assertThat(steps.step(scan.id(), SCAN).status()).isEqualTo(COMPLETED);
        verify(scanResultRepository).save(scan.id(), List.of(result));
        verify(scanLogService).log(scan.id(), ScanLogLevel.WARN, "Scanned 1 of 2 targets");
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);
    }

    @Test
    void failsTheStoreStepWhenStoringFails() {
        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenReturn(new ScanReport(List.of(), List.of()));
        doThrow(new IllegalStateException("Neo4j unavailable")).when(scanResultRepository).save(any(), any());

        start();

        assertThat(steps.step(scan.id(), STORE).error()).isEqualTo("Neo4j unavailable");
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(COMPLETED, COMPLETED, COMPLETED, FAILED, SKIPPED, SKIPPED);
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.FAILED);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void discardsTheResultsOfAScanThatWasFailedWhileItRan() {
        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.FAILED));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenReturn(new ScanReport(List.of(), List.of()));

        start();

        verify(scanResultRepository, never()).save(any(), any());
        verify(scanService, never()).updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);
        verify(eventPublisher, never()).publish(any());
        verify(scanLogService).log(eq(scan.id()), eq(ScanLogLevel.WARN), startsWith("Scan finished after it was marked FAILED"));
        verify(sourceCheckout).remove(eq(scan.id()), any());
    }

    @Test
    void leavesAScanThatWasDeletedWhileItRan() {
        when(scanService.getScan(context, scan.id()))
            .thenReturn(scan)
            .thenThrow(new NotFoundException(scan.id().id().toString()));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenReturn(new ScanReport(List.of(), List.of()));

        start();

        verify(scanResultRepository, never()).save(any(), any());
        verify(scanService, never()).updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);
        verify(eventPublisher, never()).publish(any());
        // Its logs were deleted with it
        verify(scanLogService, never()).log(eq(scan.id()), eq(ScanLogLevel.WARN), startsWith("Scan finished"));
    }

    @Test
    void failsAScanThatBreaksWhileStillActive() {
        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenThrow(new IllegalStateException("Scanner crashed"));

        start();

        verify(scanLogService).log(scan.id(), ScanLogLevel.ERROR, "Scan failed: Scanner crashed");
        assertThat(steps.step(scan.id(), SCAN).error()).isEqualTo("Scanner crashed");
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.FAILED);
    }

    @Test
    void leavesTheStatusOfAScanThatBreaksAfterItWasFailed() {
        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.FAILED));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenThrow(new IllegalStateException("Container stopped"));

        start();

        verify(scanService, never()).updateStatus(context, scan.id(), Scan.ScanStatus.FAILED);
        verify(scanLogService, never()).log(eq(scan.id()), eq(ScanLogLevel.ERROR), anyString());
        // Whatever failed it already notified of that
        verify(scanNotifier, never()).scanFailed(any(), any());
    }

    @Test
    void storesTheEnhancementsAfterTheResults() {
        final var result = scanResult();
        final var enhancement = enhancement();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenReturn(new ScanReport(List.of(result), List.of()));
        when(enhancerProvider.enhanceScan(result)).thenReturn(List.of(enhancement));

        start();

        final var order = inOrder(enhancerProvider, scanResultRepository, enhancementRepository);
        order.verify(enhancerProvider).enhanceScan(result);
        order.verify(scanResultRepository).save(scan.id(), List.of(result));
        order.verify(enhancementRepository).save(scan.id(), List.of(enhancement));

        verify(scanLogService).log(scan.id(), ScanLogLevel.INFO, "Enhanced the results with spring 1.0.0: 2 node(s) and 1 relationship(s)");
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).status()).isEqualTo(COMPLETED);
        // Nothing enhances the scan as a whole yet
        assertThat(steps.step(scan.id(), ENHANCE_SCAN).status()).isEqualTo(SKIPPED);
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);
    }

    @Test
    void storesTheResultsWhenEnhancingFails() {
        final var result = scanResult();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenReturn(new ScanReport(List.of(result), List.of()));
        when(enhancerProvider.enhanceScan(result)).thenThrow(new IllegalStateException("Enhancer broke"));

        start();

        verify(scanResultRepository).save(scan.id(), List.of(result));
        verify(enhancementRepository, never()).save(any(), any());
        verify(scanLogService).log(scan.id(), ScanLogLevel.WARN, "Enhancing failed: Enhancer broke");
        assertThat(steps.step(scan.id(), ENHANCE_RESULTS).error()).isEqualTo("Enhancer broke");
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(COMPLETED, COMPLETED, COMPLETED, COMPLETED, FAILED, SKIPPED);
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);
    }

    @Test
    void keepsTheStoredResultsWhenStoringTheEnhancementsFails() {
        final var result = scanResult();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(repositoryScanner.scan(eq(scan.id()), any(), any(), any())).thenReturn(new ScanReport(List.of(result), List.of()));
        when(enhancerProvider.enhanceScan(result)).thenReturn(List.of(enhancement()));
        doThrow(new IllegalArgumentException("Invalid label Spring Endpoint")).when(enhancementRepository).save(any(), any());

        start();

        verify(scanResultRepository).save(scan.id(), List.of(result));
        verify(scanResultRepository, never()).delete(any());
        verify(scanLogService).log(scan.id(), ScanLogLevel.WARN, "Enhancing failed: Invalid label Spring Endpoint");
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(COMPLETED, COMPLETED, COMPLETED, COMPLETED, FAILED, SKIPPED);
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);
    }

    @Test
    void enhancesUploadedResults() {
        final var result = scanResult();
        final var enhancement = enhancement();
        skipScanning();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        when(enhancerProvider.enhanceScan(result)).thenReturn(List.of(enhancement));

        scanner().storeUploaded(scan.id(), List.of(result), context);

        final var order = inOrder(scanResultRepository, enhancementRepository);
        order.verify(scanResultRepository).save(scan.id(), List.of(result));
        order.verify(enhancementRepository).save(scan.id(), List.of(enhancement));
    }

    @Test
    void storesUploadedResultsWithoutCheckingOut() {
        final var result = scanResult();
        skipScanning();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));

        scanner().storeUploaded(scan.id(), List.of(result), context);

        verify(scanResultRepository).save(scan.id(), List.of(result));
        verify(sourceCheckout, never()).checkout(any(), any(), any(), any());
        verify(repositoryScanner, never()).plan(any(), any());
        verify(scanLogService).log(scan.id(), ScanLogLevel.INFO, "Storing 1 uploaded scan result(s)");
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(SKIPPED, SKIPPED, SKIPPED, COMPLETED, COMPLETED, SKIPPED);
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);
        assertThat(completedEvent().getResults()).containsExactly(result);
        verify(scanNotifier).scanStarted(scan);
        verify(scanNotifier).scanCompleted(scan);
    }

    @Test
    void failsAnUploadedScanWhenStoringFails() {
        skipScanning();

        when(scanService.getScan(context, scan.id())).thenReturn(scan, scan(Scan.ScanStatus.RUNNING));
        doThrow(new IllegalStateException("Neo4j unavailable")).when(scanResultRepository).save(any(), any());

        scanner().storeUploaded(scan.id(), List.of(scanResult()), context);

        assertThat(steps.step(scan.id(), STORE).error()).isEqualTo("Neo4j unavailable");
        assertThat(steps.statuses(scan.id()).values())
            .containsExactly(SKIPPED, SKIPPED, SKIPPED, FAILED, SKIPPED, SKIPPED);
        verify(scanService).updateStatus(context, scan.id(), Scan.ScanStatus.FAILED);
        verify(eventPublisher, never()).publish(any());
        verify(scanNotifier).scanFailed(scan, "Neo4j unavailable");
    }

    /**
     * As the upload leaves the scan: its first steps skipped.
     */
    private void skipScanning() {
        final var scanSteps = new ScanSteps(steps, Clock.systemUTC());

        scanSteps.skip(scan.id(), CHECKOUT);
        scanSteps.skip(scan.id(), PLAN);
        scanSteps.skip(scan.id(), SCAN);
    }

    private static ScanResult scanResult() {
        final var target = new ScanTarget();
        target.setPath("backend");
        target.setLanguage(ScanTarget.Language.JAVA);

        final var result = new ScanResult();
        result.setTarget(target);

        return result;
    }

    private static Enhancements enhancement() {
        return new Enhancements("spring", "1.0.0", List.of(new Enhancement(
            List.of(
                new Node("backend|.|class:com.acme.Orders|controller", "backend|.|class:com.acme.Orders", List.of("Controller"), Map.of()),
                new Node("backend|.|class:com.acme.Orders|endpoint:GET /orders", "backend|.|class:com.acme.Orders", List.of("Endpoint"), Map.of())
            ),
            List.of(new Relationship("backend|.|class:com.acme.Orders|controller", "backend|.|class:com.acme.Orders|endpoint:GET /orders", "HAS_ENDPOINT"))
        )));
    }

    private ScanCompletedEvent completedEvent() {
        final var event = ArgumentCaptor.forClass(ScanCompletedEvent.class);

        verify(eventPublisher).publish(event.capture());

        return event.getValue();
    }

    private void start() {
        scanner().startScan(new StartScanCommand(scan.id()), context);
    }

    private ProjectScanner scanner() {
        return new ProjectScanner(
            scanService,
            projectService,
            scanLogService,
            // Runs the scan on the calling thread
            Runnable::run,
            sourceCheckout,
            repositoryScanner,
            scanResultRepository,
            enhancerProvider,
            enhancementRepository,
            new ScanSteps(steps, Clock.systemUTC()),
            eventPublisher,
            scanNotifier
        );
    }

    private Scan scan(final Scan.ScanStatus status) {
        final var id = this.scan == null ? Scan.ScanId.generate() : this.scan.id();

        return new Scan(
            id,
            project.id(),
            status,
            new Scan.SourceRevision("abc123", "main"),
            Instant.now(),
            Instant.now(),
            null
        );
    }
}
