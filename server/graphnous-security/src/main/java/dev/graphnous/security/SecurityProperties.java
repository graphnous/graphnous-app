package dev.graphnous.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.UUID;

/**
 * Whether the API checks who calls it.
 *
 * @param enabled               off: anyone may call the API, in one fixed
 *                              organization, as in the open-source
 *                              container. On: only with an access token of
 *                              the Graphnous platform, in its organization
 * @param issuerUri             the platform, which issues the access tokens
 *                              and publishes its keys; read on the first
 *                              request, so it need not be up when the
 *                              server starts
 * @param audience              who the access tokens must be for
 * @param defaultOrganizationId the organization of every request when
 *                              security is off
 */
@ConfigurationProperties("graphnous.security")
public record SecurityProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("http://localhost:1338") String issuerUri,
    @DefaultValue("graphnous-api") String audience,
    @DefaultValue("00000000-0000-0000-0000-000000000000") UUID defaultOrganizationId
) { }
