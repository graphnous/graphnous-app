package dev.graphnous.application.scan.dependencies;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanDependencyServiceTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ScanDependencyRepository repository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    private final PageQuery page = new PageQuery(0, 20, new Sort("name", Sort.Direction.ASC));

    @Test
    void listsDependenciesOfAScanTheUserMayRead() {
        final var found = new Page<ScanDependency>(List.of(), 0, 20, 0, 0);

        when(repository.findDependencies(scanId, new ScanDependencyFilter("slf4j", "orders", null), page)).thenReturn(found);

        assertThat(service().getDependencies(context, scanId, new ScanDependencyFilter(" slf4j ", "orders", ""), page)).isSameAs(found);

        verify(scanService).getScan(context, scanId);
    }

    @Test
    void doesNotListDependenciesOfAScanTheUserMayNotRead() {
        when(scanService.getScan(context, scanId)).thenThrow(new AuthorizationException("Not allowed"));

        assertThatThrownBy(() -> service().getDependencies(context, scanId, ScanDependencyFilter.none(), page))
            .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void refusesToSortByAnythingElse() {
        final var unsorted = new PageQuery(0, 20, new Sort("createdAt", Sort.Direction.ASC));

        assertThatThrownBy(() -> service().getDependencies(context, scanId, ScanDependencyFilter.none(), unsorted))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("name");

        verifyNoInteractions(repository);
    }

    @Test
    void listsDependenciesOfTheLatestCompletedScansOfASystem() {
        final var systemId = System.SystemId.generate();
        final var now = Instant.now();
        final var scans = List.of(new Scan(
            scanId, Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("abc123", "main"), now, now, now
        ));
        final var found = new Page<SystemDependency>(List.of(), 0, 20, 0, 0);

        when(scanService.getNewestCompletedScans(context, systemId)).thenReturn(scans);
        when(repository.findDependencies(scans, new ScanDependencyFilter("slf4j", null, null), page)).thenReturn(found);

        assertThat(service().getSystemDependencies(context, systemId, new ScanDependencyFilter("slf4j", null, null), page))
            .isSameAs(found);
    }

    @Test
    void doesNotListDependenciesOfASystemTheUserMayNotRead() {
        final var systemId = System.SystemId.generate();

        when(scanService.getNewestCompletedScans(context, systemId)).thenThrow(new AuthorizationException("Not allowed"));

        assertThatThrownBy(() -> service().getSystemDependencies(context, systemId, ScanDependencyFilter.none(), page))
            .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void listsTheLibrariesOfTheLatestCompletedScansOfASystem() {
        final var systemId = System.SystemId.generate();
        final var now = Instant.now();
        final var scans = List.of(new Scan(
            scanId, Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
            new Scan.SourceRevision("abc123", "main"), now, now, now
        ));
        final var byVersions = new PageQuery(0, 20, new Sort("versions", Sort.Direction.DESC));
        final var found = new Page<SystemLibrary>(List.of(), 0, 20, 0, 0);

        when(scanService.getNewestCompletedScans(context, systemId)).thenReturn(scans);
        when(repository.findLibraries(scans, "slf4j", true, byVersions)).thenReturn(found);

        assertThat(service().getSystemLibraries(context, systemId, " slf4j ", true, byVersions)).isSameAs(found);
    }

    @Test
    void refusesToSortLibrariesByAnythingElse() {
        final var systemId = System.SystemId.generate();
        final var byScope = new PageQuery(0, 20, new Sort("scope", Sort.Direction.ASC));

        assertThatThrownBy(() -> service().getSystemLibraries(context, systemId, null, false, byScope))
            .isInstanceOf(ValidationException.class);

        verifyNoInteractions(repository, scanService);
    }

    private ScanDependencyService service() {
        return new ScanDependencyService(scanService, repository);
    }
}
