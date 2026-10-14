package dev.graphnous.application.scan.usages;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanUsageServiceTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ScanUsageRepository repository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    private final Project.ProjectId projectId = Project.ProjectId.generate();

    @Test
    void findsTheUsagesOfANodeOfAScanTheUserMayRead() {
        final var found = new ScanUsages.Found(new ScanUsages.Node("class", "Class", "Order", "com.example.Order"), List.of(), false);

        when(scanService.getScan(context, scanId)).thenReturn(scan(scanId, projectId));
        when(repository.findUsages(scanId, "class", 1, ScanUsageService.MAX_USAGES)).thenReturn(Optional.of(found));

        // Only what uses it itself, by default
        assertThat(service().getUsages(context, scanId, " class ", null))
            .isEqualTo(found.withProjects(Map.of(scanId, projectId)));

        verify(scanService).getScan(context, scanId);
    }

    @Test
    void followsAsFarAsAsked() {
        final var found = new ScanUsages.Found(new ScanUsages.Node("class", "Class", "Order", null), List.of(), false);

        when(scanService.getScan(context, scanId)).thenReturn(scan(scanId, projectId));
        when(repository.findUsages(scanId, "class", 3, ScanUsageService.MAX_USAGES)).thenReturn(Optional.of(found));

        assertThat(service().getUsages(context, scanId, "class", 3).usages()).isEqualTo(found.usages());
    }

    @Test
    void namesTheProjectOfAUsageOverHttpWhenTheUserMayReadItsScan() {
        final var webScan = Scan.ScanId.generate();
        final var web = Project.ProjectId.generate();
        final var list = new ScanUsages.Node("list", "Method", "list", "com.example.Orders.list");
        final var usage = new ScanUsages.Usage(
            "REQUESTS", new ScanUsages.Node("route", "Method", "GET", "route:GET"), null, "route.ts", 9,
            list, "Orders", 1, null, webScan, new ScanUsages.Request("GET", "/api/orders", "GET", "/api/orders")
        );

        when(scanService.getScan(context, scanId)).thenReturn(scan(scanId, projectId));
        when(scanService.getScan(context, webScan)).thenReturn(scan(webScan, web));
        when(repository.findUsages(scanId, "list", 1, ScanUsageService.MAX_USAGES))
            .thenReturn(Optional.of(new ScanUsages.Found(list, List.of(usage), false)));

        final var found = service().getUsages(context, scanId, "list", null);

        assertThat(found.projects()).isEqualTo(Map.of(scanId, projectId, webScan, web));
        verify(scanService).getScan(context, webScan);
    }

    @Test
    void findsNothingWhenTheUserMayNotReadTheScanOfAUsageOverHttp() {
        final var webScan = Scan.ScanId.generate();
        final var list = new ScanUsages.Node("list", "Method", "list", null);
        final var usage = new ScanUsages.Usage(
            "REQUESTS", new ScanUsages.Node("route", "Method", "GET", null), null, null, 9,
            list, null, 1, null, webScan, new ScanUsages.Request(null, "/api/orders", "GET", "/api/orders")
        );

        when(scanService.getScan(context, scanId)).thenReturn(scan(scanId, projectId));
        when(scanService.getScan(context, webScan)).thenThrow(new AuthorizationException("Not allowed"));
        when(repository.findUsages(scanId, "list", 1, ScanUsageService.MAX_USAGES))
            .thenReturn(Optional.of(new ScanUsages.Found(list, List.of(usage), false)));

        assertThrows(AuthorizationException.class, () -> service().getUsages(context, scanId, "list", null));
    }

    private static Scan scan(final Scan.ScanId id, final Project.ProjectId project) {
        final var now = java.time.Instant.now();

        return new Scan(id, project, Scan.ScanStatus.COMPLETED, new Scan.SourceRevision("abc", "main"), now, now, now);
    }

    @Test
    void refusesWhatItCannotFollow() {
        for (final var depth : List.of(0, ScanUsageService.MAX_DEPTH + 1)) {
            assertThatThrownBy(() -> service().getUsages(context, scanId, "class", depth))
                .isInstanceOf(ValidationException.class);
        }

        assertThatThrownBy(() -> service().getUsages(context, scanId, " ", 1)).isInstanceOf(ValidationException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void doesNotFindANodeTheScanDoesNotHave() {
        when(repository.findUsages(scanId, "missing", 1, ScanUsageService.MAX_USAGES)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getUsages(context, scanId, "missing", 1))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("missing");
    }

    @Test
    void findsNothingOfAScanTheUserMayNotRead() {
        when(scanService.getScan(context, scanId)).thenThrow(new AuthorizationException("Not allowed"));

        assertThatThrownBy(() -> service().getUsages(context, scanId, "class", 1)).isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(repository);
    }

    private ScanUsageService service() {
        return new ScanUsageService(scanService, repository);
    }
}
