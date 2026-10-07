package dev.graphnous.api.configuration;

import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.EnhancerProvider;
import dev.graphnous.enhancer.EnhancerRegistry;
import dev.graphnous.enhancer.spring.SpringEnhancer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class EnhancerConfiguration {

    @Bean
    public SpringEnhancer springEnhancer() {
        return new SpringEnhancer();
    }

    /**
     * Every {@link Enhancer} bean.
     */
    @Bean
    public EnhancerRegistry enhancerRegistry(final List<Enhancer> enhancers) {
        return new EnhancerRegistry(enhancers);
    }

    @Bean
    public EnhancerProvider enhancerProvider(final EnhancerRegistry enhancerRegistry) {
        return new EnhancerProvider(enhancerRegistry);
    }
}
