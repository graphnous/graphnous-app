package dev.graphnous.api.configuration;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.enhancer.EnhancementRepository;
import dev.graphnous.application.enhancer.ScanEnhancementService;
import dev.graphnous.application.project.scanner.ScanExecutor;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultArchive;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.EnhancerProvider;
import dev.graphnous.enhancer.EnhancerRegistry;
import dev.graphnous.enhancer.angular.AngularEnhancer;
import dev.graphnous.enhancer.fetch.FetchBrowserEnhancer;
import dev.graphnous.enhancer.nextjs.NextJsEnhancer;
import dev.graphnous.enhancer.react.ReactEnhancer;
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

    @Bean
    public AngularEnhancer angularEnhancer() {
        return new AngularEnhancer();
    }

    @Bean
    public ReactEnhancer reactEnhancer() {
        return new ReactEnhancer();
    }

    @Bean
    public NextJsEnhancer nextJsEnhancer() {
        return new NextJsEnhancer();
    }

    @Bean
    public FetchBrowserEnhancer fetchBrowserEnhancer() {
        return new FetchBrowserEnhancer();
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

    @Bean
    public ScanEnhancementService scanEnhancementService(
        final ScanService scanService,
        final AuthorizationService authorizationService,
        final ScanResultArchive scanResultArchive,
        final ScanResultRepository scanResultRepository,
        final EnhancerRegistry enhancerRegistry,
        final EnhancerProvider enhancerProvider,
        final EnhancementRepository enhancementRepository,
        final ScanSteps scanSteps,
        final ScanLogService scanLogService,
        final ScanExecutor scanExecutor
    ) {
        return new ScanEnhancementService(
            scanService,
            authorizationService,
            scanResultArchive,
            scanResultRepository,
            enhancerRegistry,
            enhancerProvider,
            enhancementRepository,
            scanSteps,
            scanLogService,
            scanExecutor
        );
    }
}
