package dev.graphnous.application.entitlement;

import dev.graphnous.application.context.OrganizationContext;

public interface EntitlementService {

    void require(
        OrganizationContext context,
        Entitlement entitlement
    );

    void requireWithinLimit(
        OrganizationContext context,
        Entitlement entitlement,
        int currentCount
    );
}
