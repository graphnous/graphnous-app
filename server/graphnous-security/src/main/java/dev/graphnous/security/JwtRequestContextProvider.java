package dev.graphnous.security;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.organization.OrganizationId;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

/**
 * With security: the user and organization of the request's access token,
 * its subject and org_id, which the token's validation made sure of. An API
 * key's token, marked by its api_key claim, is for the organization without
 * a user: its subject is the key. A request without a token, such as one
 * refused before it got here, has neither.
 */
public class JwtRequestContextProvider implements RequestContextProvider {

    static final String ORGANIZATION_CLAIM = "org_id";

    static final String API_KEY_CLAIM = "api_key";

    @Override
    public RequestContext get() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            return new RequestContext(new UserContext(null), new OrganizationContext(new OrganizationId(null)));
        }

        final Jwt jwt = authentication.getToken();

        return new RequestContext(
            new UserContext(jwt.hasClaim(API_KEY_CLAIM) ? null : UUID.fromString(jwt.getSubject())),
            new OrganizationContext(new OrganizationId(UUID.fromString(jwt.getClaimAsString(ORGANIZATION_CLAIM))))
        );
    }
}
