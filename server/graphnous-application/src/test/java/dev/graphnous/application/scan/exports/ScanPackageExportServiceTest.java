package dev.graphnous.application.scan.exports;

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
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanPackageExportServiceTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ScanPackageExportRepository repository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    private final PageQuery page = new PageQuery(0, 20, new Sort("qualifiedName", Sort.Direction.ASC));

    @Test
    void listsPackageExportsOfAScanTheUserMayRead() {
        final var found = new Page<ScanPackageExport>(List.of(), 0, 20, 0, 0);

        when(repository.findPackageExports(scanId, new ScanPackageExportFilter("button", "@acme/theme"), page)).thenReturn(found);

        assertThat(service().getPackageExports(context, scanId, new ScanPackageExportFilter(" button ", " @acme/theme "), page)).isSameAs(found);

        verify(scanService).getScan(context, scanId);
    }

    @Test
    void doesNotListPackageExportsOfAScanTheUserMayNotRead() {
        when(scanService.getScan(context, scanId)).thenThrow(new AuthorizationException("Not allowed"));

        assertThatThrownBy(() -> service().getPackageExports(context, scanId, ScanPackageExportFilter.none(), page))
            .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void refusesToSortByAnythingElse() {
        final var unsorted = new PageQuery(0, 20, new Sort("path", Sort.Direction.ASC));

        assertThatThrownBy(() -> service().getPackageExports(context, scanId, ScanPackageExportFilter.none(), unsorted))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("qualifiedName");

        verifyNoInteractions(repository);
    }

    private ScanPackageExportService service() {
        return new ScanPackageExportService(scanService, repository);
    }
}
