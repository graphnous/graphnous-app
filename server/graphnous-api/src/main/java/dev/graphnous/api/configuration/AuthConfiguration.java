package dev.graphnous.api.configuration;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.authorization.NoOpAuthorizationService;
import dev.graphnous.entitlement.NoOpEntitlementService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfiguration {

    @Bean
    AuthorizationService authorizationService() {
        return new NoOpAuthorizationService();
    }

    @Bean
    EntitlementService entitlementService() {
        return new NoOpEntitlementService();
    }

}
