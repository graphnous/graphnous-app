package dev.graphnous.api.configuration;

import dev.graphnous.api.event.SpringEventPublisher;
import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.project.scanner.ScanCanceller;
import dev.graphnous.application.scan.ScanDeleter;
import dev.graphnous.application.scan.ScanRecovery;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanStepRepository;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultRepository;
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
        @Value("${graphnous.scans.timeout}") final Duration timeout
    ) {
        return new ScanRecovery(
            scanRepository,
            scanLogService,
            scanCanceller,
            scanSteps,
            timeout,
            Clock.systemUTC()
        );
    }

    @Bean
    ScanDeleter scanDeleter(
        final ScanRepository scanRepository,
        final ScanLogRepository scanLogRepository,
        final ScanResultRepository scanResultRepository,
        final ScanStepRepository scanStepRepository
    ) {
        return new ScanDeleter(
            scanRepository,
            scanLogRepository,
            scanResultRepository,
            scanStepRepository
        );
    }

    @Bean
    ScanService scanService(
        final SpringEventPublisher eventPublisher,
        final ScanRepository scanRepository,
        final ScanDeleter scanDeleter,
        final ScanSteps scanSteps,
        final ProjectService projectService,
        final AuthorizationService authorizationService,
        final EntitlementService entitlementService
    ) {
        return new ScanService(
            eventPublisher,
            scanRepository,
            scanDeleter,
            scanSteps,
            projectService,
            authorizationService,
            entitlementService
        );
    }

}
