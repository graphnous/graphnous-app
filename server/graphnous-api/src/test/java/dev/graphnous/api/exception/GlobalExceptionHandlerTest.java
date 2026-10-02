package dev.graphnous.api.exception;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.EntitlementException;
import dev.graphnous.application.exception.ErrorReporter;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private ErrorReporter errorReporter;

    @Mock
    private RequestContextProvider contextProvider;

    private final MockHttpServletRequest request =
        new MockHttpServletRequest("DELETE", "/api/v1/scans/1");

    private final NotFoundException notFound = new NotFoundException("Scan not found");

    @Test
    void reportsTheCallersOrganization() {
        final var organizationId = new OrganizationId(UUID.randomUUID());

        when(contextProvider.get()).thenReturn(
            new RequestContext(
                new UserContext(UUID.randomUUID()),
                new OrganizationContext(organizationId)
            )
        );

        final var response = handler().handleNotFound(notFound, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reportedContext().organizationId()).isEqualTo(organizationId);
    }

    @Test
    void reportsWithoutAnOrganizationWhenThereIsNoContext() {
        when(contextProvider.get()).thenReturn(null);

        final var response = handler().handleNotFound(notFound, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reportedContext().organizationId()).isNull();
        assertThat(reportedContext().path()).isEqualTo("/api/v1/scans/1");
    }

    @Test
    void reportsWithoutAnOrganizationWhenTheContextCannotBeResolved() {
        when(contextProvider.get()).thenThrow(new IllegalStateException("No authenticated user"));

        final var response = handler().handleNotFound(notFound, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reportedContext().organizationId()).isNull();
    }

    @Test
    void keepsTheResponseWhenReportingFails() {
        doThrow(new IllegalStateException("Reporter unavailable"))
            .when(errorReporter).report(any(), any());

        final var response = handler().handleNotFound(notFound, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ApiError("NOT_FOUND", "Scan not found"));
    }

    @Test
    void answersPlanLimitsWithTheirOwnCode() {
        final var response = handler().handleEntitlement(
            new EntitlementException("The plan allows 3 systems"),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo(new ApiError("PLAN_LIMIT", "The plan allows 3 systems"));
    }

    @Test
    void explainsInvalidContent() {
        final var response = handler().handleInvalidContent(
            new ValidationException("More than one scan result is for target backend"),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
            .isEqualTo(new ApiError("VALIDATION_ERROR", "More than one scan result is for target backend"));
    }

    private ErrorReporter.ErrorContext reportedContext() {
        final var context = ArgumentCaptor.forClass(ErrorReporter.ErrorContext.class);

        verify(errorReporter).report(any(), context.capture());

        return context.getValue();
    }

    private GlobalExceptionHandler handler() {
        return new GlobalExceptionHandler(errorReporter, contextProvider);
    }
}
