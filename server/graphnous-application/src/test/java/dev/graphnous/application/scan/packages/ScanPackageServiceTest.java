package dev.graphnous.application.scan.packages;

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
class ScanPackageServiceTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ScanPackageRepository repository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    private final PageQuery page = new PageQuery(0, 20, new Sort("qualifiedName", Sort.Direction.ASC));

    @Test
    void listsPackagesOfAScanTheUserMayRead() {
        final var found = new Page<ScanPackage>(List.of(), 0, 20, 0, 0);

        when(repository.findPackages(scanId, new ScanPackageFilter("example", "orders"), page)).thenReturn(found);

        assertThat(service().getPackages(context, scanId, new ScanPackageFilter(" example ", " orders "), page)).isSameAs(found);

        verify(scanService).getScan(context, scanId);
    }

    @Test
    void doesNotListPackagesOfAScanTheUserMayNotRead() {
        when(scanService.getScan(context, scanId)).thenThrow(new AuthorizationException("Not allowed"));

        assertThatThrownBy(() -> service().getPackages(context, scanId, ScanPackageFilter.none(), page))
            .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void refusesToSortByAnythingElse() {
        final var unsorted = new PageQuery(0, 20, new Sort("path", Sort.Direction.ASC));

        assertThatThrownBy(() -> service().getPackages(context, scanId, ScanPackageFilter.none(), unsorted))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("qualifiedName");

        verifyNoInteractions(repository);
    }

    private ScanPackageService service() {
        return new ScanPackageService(scanService, repository);
    }
}
