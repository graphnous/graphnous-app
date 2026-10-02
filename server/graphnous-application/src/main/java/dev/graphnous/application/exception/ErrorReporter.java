package dev.graphnous.application.exception;

import dev.graphnous.application.organization.OrganizationId;

public interface ErrorReporter {

    void report(
        final Throwable exception,
        final ErrorContext context
    );

    /**
     * @param organizationId the caller's organization, or null when the
     *                       request has none
     */
    record ErrorContext(
        OrganizationId organizationId,
        String requestId,
        String method,
        String path
    ) { }

}
