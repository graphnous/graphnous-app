package dev.graphnous.api.exception;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.EntitlementException;
import dev.graphnous.application.exception.ErrorReporter;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorReporter errorReporter;

    private final RequestContextProvider contextProvider;

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public GlobalExceptionHandler(
        final ErrorReporter errorReporter,
        final RequestContextProvider contextProvider
    ) {
        this.errorReporter = errorReporter;

        this.contextProvider = contextProvider;
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(
        final NotFoundException exception,
        final HttpServletRequest request
    ) {
        this.report(exception, request);

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ApiError(
                "NOT_FOUND",
                exception.getMessage()
            ));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(
        final ConflictException exception,
        final HttpServletRequest request
    ) {
        this.report(exception, request);

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(new ApiError(
                "CONFLICT",
                exception.getMessage()
            ));
    }

    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<ApiError> handleAuthorization(
        final AuthorizationException exception,
        final HttpServletRequest request
    ) {
        this.report(exception, request);

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(new ApiError(
                "FORBIDDEN",
                exception.getMessage()
            ));
    }

    /**
     * The organization's plan does not include the feature, or its limit is
     * reached. Forbidden like a missing permission, with its own code so
     * clients can tell the two apart.
     */
    @ExceptionHandler(EntitlementException.class)
    ResponseEntity<ApiError> handleEntitlement(
        final EntitlementException exception,
        final HttpServletRequest request
    ) {
        this.report(exception, request);

        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(new ApiError(
                "PLAN_LIMIT",
                exception.getMessage()
            ));
    }

    /**
     * Content the server cannot use, with a message that says what to fix.
     */
    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> handleInvalidContent(
        final ValidationException exception,
        final HttpServletRequest request
    ) {
        this.report(exception, request);

        return ResponseEntity
            .badRequest()
            .body(new ApiError(
                "VALIDATION_ERROR",
                exception.getMessage()
            ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
        final MethodArgumentNotValidException exception,
        final HttpServletRequest request
    ) {
        this.report(exception, request);

        return ResponseEntity
            .badRequest()
            .body(new ApiError(
                "VALIDATION_ERROR",
                "Request validation failed"
            ));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiError> handleMalformedRequest(
        final Exception exception,
        final HttpServletRequest request
    ) {
        this.report(exception, request);

        return ResponseEntity
            .badRequest()
            .body(new ApiError(
                "MALFORMED_REQUEST",
                "The request could not be read"
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(
        final Exception exception,
        final HttpServletRequest request
    ) {
        this.report(exception, request);

        // Spring MVC's own errors, such as an unknown path or an unsupported
        // method, keep their status instead of becoming a server error
        if (exception instanceof ErrorResponse errorResponse) {
            final var status = HttpStatus.valueOf(errorResponse.getStatusCode().value());

            return ResponseEntity
                .status(status)
                .body(new ApiError(
                    status.name(),
                    status.getReasonPhrase()
                ));
        }

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError(
                "INTERNAL_ERROR",
                "An unexpected error occurred"
            ));
    }

    /**
     * Reports the error without ever failing itself: the client gets the
     * response for the original error even when the caller's organization
     * cannot be resolved or the reporter breaks.
     */
    private void report(
        final Throwable exception,
        final HttpServletRequest request
    ) {
        try {
            final var context = new ErrorReporter.ErrorContext(
                this.organizationId(),
                request.getRequestId(),
                request.getMethod(),
                request.getRequestURI()
            );

            this.errorReporter.report(exception, context);
        } catch (final RuntimeException reportFailure) {
            log.warn(
                "Reporting error failed method={} path={}",
                request.getMethod(),
                request.getRequestURI(),
                reportFailure
            );
        }
    }

    /**
     * The caller's organization, or null when there is none, such as for a
     * request that failed before its context could be established.
     */
    private OrganizationId organizationId() {
        try {
            final var context = this.contextProvider.get();

            if (context == null || context.organization() == null) {
                return null;
            }

            return context.organization().id();
        } catch (final RuntimeException contextFailure) {
            return null;
        }
    }

}
