package dev.graphnous.entitlement;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.entitlement.Entitlement;
import dev.graphnous.application.entitlement.EntitlementService;

public class NoOpEntitlementService implements EntitlementService {
    @Override
    public void require(OrganizationContext context, Entitlement entitlement) {
        // NoOp
    }

    @Override
    public void requireWithinLimit(OrganizationContext context, Entitlement entitlement, int currentCount) {
        // NoOp
    }

}
