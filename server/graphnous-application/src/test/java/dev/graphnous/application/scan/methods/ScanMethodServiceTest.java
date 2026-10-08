package dev.graphnous.application.scan.methods;

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
class ScanMethodServiceTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ScanMethodRepository repository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    private final PageQuery page = new PageQuery(0, 20, new Sort("qualifiedName", Sort.Direction.ASC));

    @Test
    void listsMethodsOfAScanTheUserMayRead() {
        final var found = new Page<ScanMethod>(List.of(), 0, 20, 0, 0);

        when(repository.findMethods(scanId, new ScanMethodFilter("total", null, "function", "com.example.Order"), page)).thenReturn(found);

        assertThat(service().getMethods(context, scanId, new ScanMethodFilter(" total ", null, "function", " com.example.Order "), page)).isSameAs(found);

        verify(scanService).getScan(context, scanId);
    }

    @Test
    void doesNotListMethodsOfAScanTheUserMayNotRead() {
        when(scanService.getScan(context, scanId)).thenThrow(new AuthorizationException("Not allowed"));

        assertThatThrownBy(() -> service().getMethods(context, scanId, ScanMethodFilter.none(), page))
            .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void refusesToSortByAnythingElse() {
        final var unsorted = new PageQuery(0, 20, new Sort("size", Sort.Direction.ASC));

        assertThatThrownBy(() -> service().getMethods(context, scanId, ScanMethodFilter.none(), unsorted))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("qualifiedName");

        verifyNoInteractions(repository);
    }

    private ScanMethodService service() {
        return new ScanMethodService(scanService, repository);
    }
}
