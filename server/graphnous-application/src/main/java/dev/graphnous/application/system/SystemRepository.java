package dev.graphnous.application.system;

import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.system.System;

public interface SystemRepository {

    /**
     * Stores the system in the organization; a system keeps the
     * organization it was created in.
     */
    System save(
        final OrganizationId organizationId,
        final System system
    );

    Page<System> findAll(
        final OrganizationId organizationId,
        final PageQuery pageQuery
    );

    int count(
        final OrganizationId organizationId
    );

    System findById(
        final OrganizationId organizationId,
        final System.SystemId id
    );

    void delete(
        final OrganizationId organizationId,
        final System.SystemId id
    );
}
