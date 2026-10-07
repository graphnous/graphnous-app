package dev.graphnous.api.configuration;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.notification.NotificationRepository;
import dev.graphnous.application.project.ProjectDeleter;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanDeleter;
import dev.graphnous.application.system.SystemService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectConfiguration {

    @Bean
    ProjectService projectService(
        final ProjectRepository projectRepository,
        final AuthorizationService authorizationService,
        final EntitlementService entitlementService,
        final SystemService systemService,
        final ProjectDeleter projectDeleter
    ) {
        return new ProjectService(
            projectRepository,
            authorizationService,
            entitlementService,
            systemService,
            projectDeleter
        );
    }

    @Bean
    ProjectDeleter projectDeleter(
        final ProjectRepository projectRepository,
        final ScanDeleter scanDeleter,
        final NotificationRepository notificationRepository
    ) {
        return new ProjectDeleter(
            projectRepository,
            scanDeleter,
            notificationRepository
        );
    }

}