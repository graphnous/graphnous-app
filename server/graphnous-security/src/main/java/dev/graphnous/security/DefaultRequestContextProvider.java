package dev.graphnous.security;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.organization.OrganizationId;

import java.util.UUID;

/**
 * Without security: no user, and the same organization for every request.
 */
public class DefaultRequestContextProvider implements RequestContextProvider {

    private final RequestContext context;

    public DefaultRequestContextProvider(final UUID organizationId) {
        this.context = new RequestContext(new UserContext(null), new OrganizationContext(new OrganizationId(organizationId)));
    }

    @Override
    public RequestContext get() {
        return this.context;
    }
}
