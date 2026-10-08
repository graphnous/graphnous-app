package dev.graphnous.api.configuration;

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
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanStepRepository;
import dev.graphnous.application.scan.classes.ScanClassRepository;
import dev.graphnous.application.scan.classes.ScanClassService;
import dev.graphnous.application.scan.dependencies.ScanDependencyRepository;
import dev.graphnous.application.scan.dependencies.ScanDependencyService;
import dev.graphnous.application.scan.files.ScanFileRepository;
import dev.graphnous.application.scan.files.ScanFileService;
import dev.graphnous.application.scan.graph.ScanGraphRepository;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.application.scan.methods.ScanMethodRepository;
import dev.graphnous.application.scan.methods.ScanMethodService;
import dev.graphnous.application.scan.modules.ScanModuleRepository;
import dev.graphnous.application.scan.modules.ScanModuleService;
import dev.graphnous.application.scan.packages.ScanPackageRepository;
import dev.graphnous.application.scan.packages.ScanPackageService;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.application.scan.stats.ScanStatRepository;
import dev.graphnous.application.scan.stats.ScanStatService;
import dev.graphnous.application.system.SystemRepository;
import dev.graphnous.application.system.SystemService;
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
        final ScanStepRepository scanStepRepository,
        final ScanStatRepository scanStatRepository,
        final NotificationRepository notificationRepository
    ) {
        return new ScanDeleter(
            scanRepository,
            scanLogRepository,
            scanResultRepository,
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
    ScanStatService scanStatService(final ScanStatRepository scanStatRepository) {
        return new ScanStatService(scanStatRepository);
    }

    @Bean
    ScanService scanService(
        final SpringEventPublisher eventPublisher,
        final ScanRepository scanRepository,
        final ScanDeleter scanDeleter,
        final ScanSteps scanSteps,
        final ScanRetention scanRetention,
        final ProjectService projectService,
        final AuthorizationService authorizationService,
        final EntitlementService entitlementService
    ) {
        return new ScanService(
            eventPublisher,
            scanRepository,
            scanDeleter,
            scanSteps,
            scanRetention,
            projectService,
            authorizationService,
            entitlementService
        );
    }

}
