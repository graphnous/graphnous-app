package dev.graphnous.api.configuration;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.notification.NotificationRepository;
import dev.graphnous.application.notification.NotificationService;
import dev.graphnous.application.notification.ScanNotifier;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.system.SystemService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class NotificationConfiguration {

    @Bean
    NotificationService notificationService(
        final NotificationRepository notificationRepository,
        final AuthorizationService authorizationService,
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService
    ) {
        return new NotificationService(
            notificationRepository,
            authorizationService,
            systemService,
            projectService,
            scanService,
            Clock.systemUTC()
        );
    }

    @Bean
    ScanNotifier scanNotifier(
        final NotificationRepository notificationRepository,
        final ProjectRepository projectRepository
    ) {
        return new ScanNotifier(notificationRepository, projectRepository, Clock.systemUTC());
    }

}
