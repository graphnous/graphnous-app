package dev.graphnous.application.scan.usages;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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

    @Test
    void findsTheUsagesOfANodeOfAScanTheUserMayRead() {
        final var found = new ScanUsages.Found(new ScanUsages.Node("class", "Class", "Order", "com.example.Order"), List.of(), false);

        when(repository.findUsages(scanId, "class", 1, ScanUsageService.MAX_USAGES)).thenReturn(Optional.of(found));

        // Only what uses it itself, by default
        assertThat(service().getUsages(context, scanId, " class ", null)).isSameAs(found);

        verify(scanService).getScan(context, scanId);
    }

    @Test
    void followsAsFarAsAsked() {
        final var found = new ScanUsages.Found(new ScanUsages.Node("class", "Class", "Order", null), List.of(), false);

        when(repository.findUsages(scanId, "class", 3, ScanUsageService.MAX_USAGES)).thenReturn(Optional.of(found));

        assertThat(service().getUsages(context, scanId, "class", 3)).isSameAs(found);
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
