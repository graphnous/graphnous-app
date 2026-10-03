package dev.graphnous.api.configuration;

import dev.graphnous.application.capability.CapabilityResolver;
import dev.graphnous.application.capability.CapabilityService;
import dev.graphnous.capability.LocalCapabilityResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CapabilityConfiguration {

    @Bean
    CapabilityResolver capabilityResolver() {
        return new LocalCapabilityResolver();
    }

    @Bean
    CapabilityService capabilityService(
        final CapabilityResolver capabilityResolver
    ) {
        return new CapabilityService(capabilityResolver);
    }

}
