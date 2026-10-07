package dev.graphnous.api.configuration;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.chat.ChatThreadRepository;
import dev.graphnous.application.notification.NotificationRepository;
import dev.graphnous.application.project.ProjectDeleter;
import dev.graphnous.application.system.SystemRepository;
import dev.graphnous.application.system.SystemService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SystemConfiguration {

    @Bean
    SystemService systemService(
        final SystemRepository systemRepository,
        final AuthorizationService authorizationService,
        final EntitlementService entitlementService,
        final ProjectDeleter projectDeleter,
        final NotificationRepository notificationRepository,
        final ChatThreadRepository chatThreadRepository
    ) {
        return new SystemService(
            systemRepository,
            authorizationService,
            entitlementService,
            projectDeleter,
            notificationRepository,
            chatThreadRepository
        );
    }

}