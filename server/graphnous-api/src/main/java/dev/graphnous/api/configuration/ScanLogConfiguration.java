package dev.graphnous.api.configuration;

import dev.graphnous.application.project.scanner.logs.ScanLogPublisher;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.application.scan.log.ScanLogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScanLogConfiguration {

    @Bean
    public ScanLogService scanLogService(
        final ScanLogRepository scanLogRepository,
        final ScanLogPublisher scanLogPublisher,
        final ScanService scanService
    ) {
        return new ScanLogService(
            scanLogRepository,
            scanLogPublisher,
            scanService
        );
    }
}
