package dev.graphnous.security;

import dev.graphnous.application.context.RequestContextProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.UUID;

/**
 * Who may call the API (SecurityProperties): anyone, in one fixed
 * organization, or only the users of the Graphnous platform, each in the
 * organization of their access token. The server knows nothing more of the
 * platform than where it publishes its keys.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfiguration {

    /**
     * Security off: everything open, as before there was any.
     */
    @Configuration
    @ConditionalOnProperty(name = "graphnous.security.enabled", havingValue = "false", matchIfMissing = true)
    static class Disabled {

        @Bean
        SecurityFilterChain openSecurityFilterChain(final HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

            return http.build();
        }

        @Bean
        RequestContextProvider requestContextProvider(final SecurityProperties properties) {
            return new DefaultRequestContextProvider(properties.defaultOrganizationId());
        }
    }

    /**
     * Security on: every request needs an access token of the platform, for
     * this API and for one organization.
     */
    @Configuration
    @ConditionalOnProperty(name = "graphnous.security.enabled", havingValue = "true")
    static class Enabled {

        @Bean
        SecurityFilterChain resourceServerSecurityFilterChain(final HttpSecurity http) throws Exception {
            http
                // Access tokens in a header, not cookies: nothing to forge
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/error", "/actuator/health").permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));

            return http.build();
        }

        /**
         * The platform's keys, fetched from its discovery document on the
         * first request.
         */
        @Bean
        JwtDecoder jwtDecoder(final SecurityProperties properties) {
            return new SupplierJwtDecoder(() -> {
                final var decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuerUri());
                decoder.setJwtValidator(validator(properties));
                return decoder;
            });
        }

        @Bean
        RequestContextProvider requestContextProvider() {
            return new JwtRequestContextProvider();
        }
    }

    /**
     * Issued by the platform, not expired, for this API, and for an
     * organization.
     */
    static OAuth2TokenValidator<Jwt> validator(final SecurityProperties properties) {
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuerUri()),
            new JwtClaimValidator<Collection<String>>("aud", audience -> audience != null && audience.contains(properties.audience())),
            SecurityConfiguration::hasOrganization
        );
    }

    private static OAuth2TokenValidatorResult hasOrganization(final Jwt jwt) {
        try {
            UUID.fromString(jwt.getClaimAsString(JwtRequestContextProvider.ORGANIZATION_CLAIM));
            UUID.fromString(jwt.getSubject());
            return OAuth2TokenValidatorResult.success();
        } catch (final RuntimeException exception) {
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "The token is not for an organization", null)
            );
        }
    }
}
