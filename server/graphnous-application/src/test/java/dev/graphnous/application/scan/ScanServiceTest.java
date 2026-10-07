package dev.graphnous.application.scan;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.entitlement.Entitlement;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.event.EventPublisher;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.GraphnousException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanDeleter scanDeleter;

    @Mock
    private ScanSteps scanSteps;

    @Mock
    private ScanRetention scanRetention;

    @Mock
    private ProjectService projectService;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private EntitlementService entitlementService;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    @ParameterizedTest
    @EnumSource(value = Scan.ScanStatus.class, names = {"COMPLETED", "FAILED"})
    void deletesAFinishedScan(final Scan.ScanStatus status) {
        final var scan = scan(status);

        when(scanRepository.findById(scan.id())).thenReturn(scan);

        service().delete(context, new DeleteScanCommand(scan.id()));

        verify(authorizationService).authorize(context, Permission.SCAN_DELETE);
        // Checks the scan's project is accessible to the caller
        verify(projectService).getProject(context, scan.projectId());
        verify(scanDeleter).deleteScan(scan.id());
    }

    @ParameterizedTest
    @EnumSource(value = Scan.ScanStatus.class, names = {"PENDING", "QUEUED", "RUNNING"})
    void refusesToDeleteAnActiveScan(final Scan.ScanStatus status) {
        final var scan = scan(status);

        when(scanRepository.findById(scan.id())).thenReturn(scan);

        assertThrows(
            ConflictException.class,
            () -> service().delete(context, new DeleteScanCommand(scan.id()))
        );

        verify(scanDeleter, never()).deleteScan(any());
    }

    @Test
    void refusesToDeleteWithoutPermission() {
        final var scanId = Scan.ScanId.generate();

        doThrow(new AuthorizationException("Not allowed"))
            .when(authorizationService).authorize(context, Permission.SCAN_DELETE);

        assertThrows(
            AuthorizationException.class,
            () -> service().delete(context, new DeleteScanCommand(scanId))
        );

        verify(scanDeleter, never()).deleteScan(any());
    }

    @Test
    void createsAPendingScanAndStartsIt() {
        final var projectId = Project.ProjectId.generate();

        when(scanRepository.count(projectId)).thenReturn(3);
        when(scanRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        final var scan = service().create(context, new CreateScanCommand(projectId, "abc123", "main"));

        assertThat(scan.status()).isEqualTo(Scan.ScanStatus.PENDING);
        assertThat(scan.projectId()).isEqualTo(projectId);
        assertThat(scan.revision()).isEqualTo(new Scan.SourceRevision("abc123", "main"));

        verify(authorizationService).authorize(context, Permission.SCAN_CREATE);
        verify(entitlementService).requireWithinLimit(context.organization(), Entitlement.SCANS, 3);
        verify(projectService).getProject(context, projectId);

        // Old scans make way before the limit is checked
        final var order = inOrder(scanRetention, entitlementService);
        order.verify(scanRetention).makeRoomFor(projectId);
        order.verify(entitlementService).requireWithinLimit(context.organization(), Entitlement.SCANS, 3);

        // Every step of the scan is there, pending, from the start
        verify(scanSteps).create(scan.id());

        final var event = ArgumentCaptor.forClass(StartScanEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().getScanId()).isEqualTo(scan.id());
    }

    @Test
    void doesNotCreateAScanForAProjectOfAnotherOrganization() {
        final var projectId = Project.ProjectId.generate();

        when(projectService.getProject(context, projectId)).thenThrow(new NotFoundException("Not found"));

        assertThrows(
            NotFoundException.class,
            () -> service().create(context, new CreateScanCommand(projectId, "abc123", "main"))
        );

        verify(scanRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void readsAScanThroughItsProject() {
        final var scan = scan(Scan.ScanStatus.RUNNING);

        when(scanRepository.findById(scan.id())).thenReturn(scan);

        assertThat(service().getScan(context, scan.id())).isEqualTo(scan);

        verify(authorizationService).authorize(context, Permission.SCAN_READ);
        verify(projectService).getProject(context, scan.projectId());
    }

    @Test
    void listsTheScansOfAProjectOfTheCaller() {
        final var projectId = Project.ProjectId.generate();

        when(projectService.getProject(context, projectId)).thenThrow(new NotFoundException("Not found"));

        assertThrows(NotFoundException.class, () -> service().getScans(context, PageQuery.of(0, 20), projectId));

        verify(scanRepository, never()).findAll(any(), any());
    }

    @Test
    void movesAScanToItsNextStatus() {
        final var scan = scan(Scan.ScanStatus.QUEUED);

        when(scanRepository.findById(scan.id())).thenReturn(scan);

        service().updateStatus(context, scan.id(), Scan.ScanStatus.RUNNING);

        final var saved = ArgumentCaptor.forClass(Scan.class);
        verify(scanRepository).save(saved.capture());

        assertThat(saved.getValue().status()).isEqualTo(Scan.ScanStatus.RUNNING);
        assertThat(saved.getValue().createdAt()).isEqualTo(scan.createdAt());
        // Moving to RUNNING records when the scan started
        assertThat(saved.getValue().startedAt()).isEqualTo(saved.getValue().updatedAt());
        assertThat(saved.getValue().updatedAt()).isAfterOrEqualTo(scan.updatedAt());
    }

    @Test
    void refusesTheStatusAScanAlreadyHas() {
        final var scan = scan(Scan.ScanStatus.RUNNING);

        when(scanRepository.findById(scan.id())).thenReturn(scan);

        assertThrows(
            GraphnousException.class,
            () -> service().updateStatus(context, scan.id(), Scan.ScanStatus.RUNNING)
        );

        verify(scanRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = Scan.ScanStatus.class, names = {"COMPLETED", "FAILED"})
    void neverChangesAFinishedScan(final Scan.ScanStatus status) {
        final var scan = scan(status);

        when(scanRepository.findById(scan.id())).thenReturn(scan);

        final var next = status == Scan.ScanStatus.COMPLETED ? Scan.ScanStatus.FAILED : Scan.ScanStatus.COMPLETED;

        assertThrows(GraphnousException.class, () -> service().updateStatus(context, scan.id(), next));

        verify(scanRepository, never()).save(any());
    }

    @Test
    void startsAScanInItsProjectsSnapshot() {
        final var scan = scan(Scan.ScanStatus.QUEUED);
        final var project = new Project(
            scan.projectId(), "backend", null, "https://example.com/shop.git", null,
            new System.SystemId(UUID.randomUUID()), Instant.now(), Instant.now()
        );
        final var snapshotId = UUID.randomUUID();

        when(scanRepository.findById(scan.id())).thenReturn(scan);
        when(projectService.getProject(context, scan.projectId())).thenReturn(project);
        when(projectService.getSnapshotId(project.id())).thenReturn(snapshotId);

        service().startScan(context, scan.id());

        verify(scanRepository).startScan(snapshotId, scan.id());
    }

    @Test
    void readsTheStepsOfAScanTheCallerMayRead() {
        final var scan = scan(Scan.ScanStatus.RUNNING);
        final var steps = List.of(ScanStep.pending(scan.id(), ScanStep.ScanStepType.CHECKOUT));

        when(scanRepository.findById(scan.id())).thenReturn(scan);
        when(scanSteps.steps(scan.id())).thenReturn(steps);

        assertThat(service().getSteps(context, scan.id())).isEqualTo(steps);
        verify(projectService).getProject(context, scan.projectId());
    }

    @Test
    void doesNotReadTheStepsOfAScanOfAnotherOrganization() {
        final var scan = scan(Scan.ScanStatus.RUNNING);

        when(scanRepository.findById(scan.id())).thenReturn(scan);
        when(projectService.getProject(context, scan.projectId())).thenThrow(new NotFoundException("Not found"));

        assertThrows(NotFoundException.class, () -> service().getSteps(context, scan.id()));

        verify(scanSteps, never()).steps(any());
    }

    @Test
    void createsAScanFromUploadedResultsWithoutItsFirstSteps() {
        final var projectId = Project.ProjectId.generate();
        final var results = List.of(result("backend"), result("frontend"));

        when(scanRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        final var scan = service().upload(context, new UploadScanCommand(projectId, "abc123", "main", results));

        assertThat(scan.status()).isEqualTo(Scan.ScanStatus.PENDING);
        assertThat(scan.revision()).isEqualTo(new Scan.SourceRevision("abc123", "main"));

        verify(projectService).getProject(context, projectId);
        verify(scanSteps).create(scan.id());
        verify(scanSteps).skip(scan.id(), ScanStep.ScanStepType.CHECKOUT);
        verify(scanSteps).skip(scan.id(), ScanStep.ScanStepType.PLAN);
        verify(scanSteps).skip(scan.id(), ScanStep.ScanStepType.SCAN);
        verify(scanSteps, never()).skip(scan.id(), ScanStep.ScanStepType.STORE);

        final var event = ArgumentCaptor.forClass(UploadedScanEvent.class);
        verify(eventPublisher).publish(event.capture());
        assertThat(event.getValue().getScanId()).isEqualTo(scan.id());
        assertThat(event.getValue().getResults()).isEqualTo(results);
    }

    @Test
    void refusesAnUploadWithoutResults() {
        assertUploadRefused(List.of(), "Upload at least one scan result");
    }

    @Test
    void refusesAnUploadedResultWithoutATarget() {
        final var withoutTarget = new ScanResult();

        assertUploadRefused(List.of(result("backend"), withoutTarget), "Scan result 2 has no target with a path and a language");
    }

    @Test
    void refusesTwoUploadedResultsForTheSameTarget() {
        assertUploadRefused(List.of(result("backend"), result("backend")), "More than one scan result is for target backend");
    }

    @Test
    void doesNotUploadToAProjectOfAnotherOrganization() {
        final var projectId = Project.ProjectId.generate();

        when(projectService.getProject(context, projectId)).thenThrow(new NotFoundException("Not found"));

        assertThrows(
            NotFoundException.class,
            () -> service().upload(context, new UploadScanCommand(projectId, "abc123", "main", List.of(result("backend"))))
        );

        verify(scanRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    private void assertUploadRefused(final List<ScanResult> results, final String message) {
        final var command = new UploadScanCommand(Project.ProjectId.generate(), "abc123", "main", results);

        assertThatThrownBy(() -> service().upload(context, command))
            .isInstanceOf(ValidationException.class)
            .hasMessage(message);

        verify(scanRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    private static ScanResult result(final String path) {
        final var target = new ScanTarget();
        target.setPath(path);
        target.setLanguage(ScanTarget.Language.JAVA);

        final var result = new ScanResult();
        result.setTarget(target);

        return result;
    }

    private Scan scan(final Scan.ScanStatus status) {
        return new Scan(
            Scan.ScanId.generate(),
            Project.ProjectId.generate(),
            status,
            new Scan.SourceRevision("abc123", "main"),
            Instant.now(),
            Instant.now(),
            null
        );
    }

    private ScanService service() {
        return new ScanService(
            eventPublisher,
            scanRepository,
            scanDeleter,
            scanSteps,
            scanRetention,
            projectService,
            authorizationService,
            entitlementService
        );
    }
}
