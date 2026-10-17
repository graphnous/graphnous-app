package dev.graphnous.api.configuration;

import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.api.event.SpringEventPublisher;
import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.notification.NotificationRepository;
import dev.graphnous.application.notification.ScanNotifier;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.project.scanner.ScanCanceller;
import dev.graphnous.application.scan.ScanDeleter;
import dev.graphnous.application.scan.ScanRecovery;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.application.scan.ScanRetention;
import dev.graphnous.application.scan.ScanLanguages;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanStepRepository;
import dev.graphnous.application.scan.classes.ScanClassRepository;
import dev.graphnous.application.scan.classes.ScanClassService;
import dev.graphnous.application.scan.dependencies.ScanDependencyRepository;
import dev.graphnous.application.scan.dependencies.ScanDependencyService;
import dev.graphnous.application.scan.exports.ScanPackageExportRepository;
import dev.graphnous.application.scan.exports.ScanPackageExportService;
import dev.graphnous.application.scan.files.ScanFileRepository;
import dev.graphnous.application.scan.files.ScanFileService;
import dev.graphnous.application.scan.graph.ScanGraphRepository;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.links.HttpLinkRepository;
import dev.graphnous.application.scan.links.HttpLinkService;
import dev.graphnous.application.scan.methods.ScanMethodRepository;
import dev.graphnous.application.scan.methods.ScanMethodService;
import dev.graphnous.application.scan.modules.ScanModuleRepository;
import dev.graphnous.application.scan.modules.ScanModuleService;
import dev.graphnous.application.scan.packages.ScanPackageRepository;
import dev.graphnous.application.scan.packages.ScanPackageService;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultArchive;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.application.scan.source.ScanSourceRepository;
import dev.graphnous.application.scan.source.ScanSourceService;
import dev.graphnous.application.scan.stats.ScanStatRepository;
import dev.graphnous.application.scan.stats.ScanStatService;
import dev.graphnous.application.scan.usages.ScanUsageRepository;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.system.SystemRepository;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.enhancer.EnhancerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling
public class ScanConfiguration {

    @Bean
    ScanSteps scanSteps(final ScanStepRepository scanStepRepository) {
        return new ScanSteps(scanStepRepository, Clock.systemUTC());
    }

    @Bean
    ScanRecovery scanRecovery(
        final ScanRepository scanRepository,
        final ScanLogService scanLogService,
        final ScanCanceller scanCanceller,
        final ScanSteps scanSteps,
        final ScanNotifier scanNotifier,
        @Value("${graphnous.scans.timeout}") final Duration timeout
    ) {
        return new ScanRecovery(
            scanRepository,
            scanLogService,
            scanCanceller,
            scanSteps,
            scanNotifier,
            timeout,
            Clock.systemUTC()
        );
    }

    @Bean
    ScanDeleter scanDeleter(
        final ScanRepository scanRepository,
        final ScanLogRepository scanLogRepository,
        final ScanResultRepository scanResultRepository,
        final ScanResultArchive scanResultArchive,
        final ScanStepRepository scanStepRepository,
        final ScanStatRepository scanStatRepository,
        final NotificationRepository notificationRepository
    ) {
        return new ScanDeleter(
            scanRepository,
            scanLogRepository,
            scanResultRepository,
            scanResultArchive,
            scanStepRepository,
            scanStatRepository,
            notificationRepository
        );
    }

    @Bean
    ScanGraphService scanGraphService(
        final ScanService scanService,
        final ScanGraphRepository scanGraphRepository
    ) {
        return new ScanGraphService(scanService, scanGraphRepository);
    }

    @Bean
    ScanModuleService scanModuleService(
        final ScanService scanService,
        final ScanModuleRepository scanModuleRepository
    ) {
        return new ScanModuleService(scanService, scanModuleRepository);
    }

    @Bean
    ScanPackageService scanPackageService(
        final ScanService scanService,
        final ScanPackageRepository scanPackageRepository
    ) {
        return new ScanPackageService(scanService, scanPackageRepository);
    }

    @Bean
    ScanPackageExportService scanPackageExportService(
        final ScanService scanService,
        final ScanPackageExportRepository scanPackageExportRepository
    ) {
        return new ScanPackageExportService(scanService, scanPackageExportRepository);
    }

    @Bean
    ScanFileService scanFileService(
        final ScanService scanService,
        final ScanFileRepository scanFileRepository
    ) {
        return new ScanFileService(scanService, scanFileRepository);
    }

    @Bean
    ScanClassService scanClassService(
        final ScanService scanService,
        final ScanClassRepository scanClassRepository
    ) {
        return new ScanClassService(scanService, scanClassRepository);
    }

    @Bean
    ScanMethodService scanMethodService(
        final ScanService scanService,
        final ScanMethodRepository scanMethodRepository
    ) {
        return new ScanMethodService(scanService, scanMethodRepository);
    }

    @Bean
    ScanDependencyService scanDependencyService(
        final ScanService scanService,
        final ScanDependencyRepository scanDependencyRepository
    ) {
        return new ScanDependencyService(scanService, scanDependencyRepository);
    }

    @Bean
    ScanUsageService scanUsageService(
        final ScanService scanService,
        final ScanUsageRepository scanUsageRepository
    ) {
        return new ScanUsageService(scanService, scanUsageRepository);
    }

    @Bean
    ScanSourceService scanSourceService(
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final ScanSourceRepository scanSourceRepository
    ) {
        return new ScanSourceService(systemService, projectService, scanService, scanSourceRepository);
    }

    @Bean
    ImpactAnalysisService impactAnalysisService(
        final ScanService scanService,
        final ProjectService projectService,
        final ScanGraphService scanGraphService,
        final ScanUsageService scanUsageService,
        final HttpLinkRepository httpLinkRepository
    ) {
        return new ImpactAnalysisService(scanService, projectService, scanGraphService, scanUsageService, httpLinkRepository);
    }

    @Bean
    HttpLinkService httpLinkService(
        final ScanService scanService,
        final ScanRepository scanRepository,
        final ProjectRepository projectRepository,
        final HttpLinkRepository httpLinkRepository
    ) {
        return new HttpLinkService(scanService, scanRepository, projectRepository, httpLinkRepository);
    }

    @Bean
    ScanStatService scanStatService(
        final ScanStatRepository scanStatRepository,
        final ScanRepository scanRepository,
        final ProjectRepository projectRepository,
        final ProjectService projectService,
        final SystemService systemService,
        final AuthorizationService authorizationService
    ) {
        return new ScanStatService(
            scanStatRepository, scanRepository, projectRepository, projectService, systemService, authorizationService
        );
    }

    @Bean
    ScanService scanService(
        final SpringEventPublisher eventPublisher,
        final ScanRepository scanRepository,
        final ScanDeleter scanDeleter,
        final ScanSteps scanSteps,
        final ScanRetention scanRetention,
        final ProjectService projectService,
        final SystemService systemService,
        final AuthorizationService authorizationService,
        final EntitlementService entitlementService,
        final EnhancerRegistry enhancerRegistry,
        final ScanLanguages scanLanguages
    ) {
        return new ScanService(
            eventPublisher,
            scanRepository,
            scanDeleter,
            scanSteps,
            scanRetention,
            projectService,
            systemService,
            authorizationService,
            entitlementService,
            enhancerRegistry,
            scanLanguages
        );
    }

}
