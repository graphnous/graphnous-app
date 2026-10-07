package dev.graphnous.application.system;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.entitlement.Entitlement;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.EntitlementException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.chat.ChatThreadRepository;
import dev.graphnous.application.notification.NotificationRepository;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.ProjectDeleter;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock
    private SystemRepository systemRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private ProjectDeleter projectDeleter;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ChatThreadRepository chatThreadRepository;

    private final OrganizationId organizationId = new OrganizationId(UUID.randomUUID());

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(organizationId)
    );

    @Test
    void createsASystemInTheCallersOrganization() {
        when(systemRepository.count(organizationId)).thenReturn(2);
        when(systemRepository.save(eq(organizationId), any())).thenAnswer(call -> call.getArgument(1));

        final var system = service().create(context, new CreateSystemCommand("Shop", "The web shop"));

        assertThat(system.name()).isEqualTo("Shop");
        assertThat(system.description()).isEqualTo("The web shop");

        verify(authorizationService).authorize(context, Permission.SYSTEM_CREATE);
        verify(entitlementService).require(context.organization(), Entitlement.SYSTEMS);
        // The limit is checked against the systems the organization already has
        verify(entitlementService).requireWithinLimit(context.organization(), Entitlement.SYSTEMS, 2);
    }

    @Test
    void doesNotCreateASystemBeyondTheLimit() {
        doThrow(new EntitlementException("Limit reached"))
            .when(entitlementService).requireWithinLimit(any(), eq(Entitlement.SYSTEMS), any(Integer.class));

        assertThrows(
            EntitlementException.class,
            () -> service().create(context, new CreateSystemCommand("Shop", null))
        );

        verify(systemRepository, never()).save(any(), any());
    }

    @Test
    void doesNotCreateASystemWithoutPermission() {
        doThrow(new AuthorizationException("Not allowed"))
            .when(authorizationService).authorize(context, Permission.SYSTEM_CREATE);

        assertThrows(
            AuthorizationException.class,
            () -> service().create(context, new CreateSystemCommand("Shop", null))
        );

        verify(systemRepository, never()).save(any(), any());
    }

    @Test
    void readsASystemOfTheCallersOrganization() {
        final var system = system(Instant.now());

        when(systemRepository.findById(organizationId, system.id())).thenReturn(system);

        assertThat(service().getSystem(context, system.id())).isEqualTo(system);
        verify(authorizationService).authorize(context, Permission.SYSTEM_READ);
    }

    @Test
    void updatesASystemKeepingWhenItWasCreated() {
        final var created = Instant.parse("2026-01-01T00:00:00Z");
        final var current = system(created);

        when(systemRepository.findById(organizationId, current.id())).thenReturn(current);
        when(systemRepository.save(eq(organizationId), any())).thenAnswer(call -> call.getArgument(1));

        service().update(context, new UpdateSystemCommand(current.id(), "Renamed", "New description"));

        final var saved = ArgumentCaptor.forClass(System.class);
        verify(systemRepository).save(eq(organizationId), saved.capture());

        assertThat(saved.getValue().id()).isEqualTo(current.id());
        assertThat(saved.getValue().name()).isEqualTo("Renamed");
        assertThat(saved.getValue().createdAt()).isEqualTo(created);
        assertThat(saved.getValue().updatedAt()).isAfter(created);
        verify(authorizationService).authorize(context, Permission.SYSTEM_UPDATE);
    }

    @Test
    void doesNotUpdateAnUnknownSystem() {
        final var id = System.SystemId.generate();

        // Also the answer for a system of another organization
        when(systemRepository.findById(organizationId, id)).thenThrow(new NotFoundException("Not found"));

        assertThrows(
            NotFoundException.class,
            () -> service().update(context, new UpdateSystemCommand(id, "Renamed", null))
        );

        verify(systemRepository, never()).save(any(), any());
    }

    @Test
    void deletesTheProjectsAndNotificationsBeforeTheSystem() {
        final var system = system(Instant.now());

        when(systemRepository.findById(organizationId, system.id())).thenReturn(system);

        service().delete(context, system.id());

        final InOrder order = inOrder(authorizationService, projectDeleter, notificationRepository, systemRepository);
        order.verify(authorizationService).authorize(context, Permission.SYSTEM_DELETE);
        order.verify(projectDeleter).deleteProjects(system.id());
        order.verify(notificationRepository).deleteBySystemId(system.id());
        verify(chatThreadRepository).deleteBySystemId(system.id());
        order.verify(systemRepository).delete(organizationId, system.id());
    }

    @Test
    void deletesNothingOfASystemOfAnotherOrganization() {
        final var id = System.SystemId.generate();

        when(systemRepository.findById(organizationId, id)).thenThrow(new NotFoundException("Not found"));

        assertThrows(NotFoundException.class, () -> service().delete(context, id));

        verify(projectDeleter, never()).deleteProjects(any());
        verify(notificationRepository, never()).deleteBySystemId(any());
        verify(systemRepository, never()).delete(any(), any());
    }

    @Test
    void doesNotDeleteWithoutPermission() {
        doThrow(new AuthorizationException("Not allowed"))
            .when(authorizationService).authorize(context, Permission.SYSTEM_DELETE);

        assertThrows(AuthorizationException.class, () -> service().delete(context, System.SystemId.generate()));

        verify(projectDeleter, never()).deleteProjects(any());
        verify(systemRepository, never()).delete(any(), any());
    }

    private static System system(final Instant createdAt) {
        return new System(System.SystemId.generate(), "Shop", null, createdAt, createdAt);
    }

    private SystemService service() {
        return new SystemService(systemRepository, authorizationService, entitlementService, projectDeleter, notificationRepository, chatThreadRepository);
    }
}
