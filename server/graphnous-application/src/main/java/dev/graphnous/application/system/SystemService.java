package dev.graphnous.application.system;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.project.ProjectDeleter;
import dev.graphnous.domain.system.System;
import dev.graphnous.application.entitlement.Entitlement;
import dev.graphnous.application.entitlement.EntitlementService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class SystemService {

    private final SystemRepository systemRepository;
    private final AuthorizationService authorizationService;
    private final EntitlementService entitlementService;

    private final ProjectDeleter projectDeleter;

    private static final Logger log = LoggerFactory.getLogger(SystemService.class);

    public SystemService(
        final SystemRepository systemRepository,
        final AuthorizationService authorizationService,
        final EntitlementService entitlementService,
        final ProjectDeleter projectDeleter
    ) {
        this.systemRepository = systemRepository;

        this.authorizationService = authorizationService;
        this.entitlementService = entitlementService;

        this.projectDeleter = projectDeleter;
    }

    public Page<System> getSystems(
        final RequestContext context,
        final PageQuery pageQuery
    ) {
        authorizationService.authorize(
            context,
            Permission.SYSTEM_READ
        );

        log.debug(
            "Getting systems organizationId={} page={} size={}",
            context.organization().id(),
            pageQuery.page(),
            pageQuery.size()
        );

        return systemRepository.findAll(
            context.organization().id(),
            pageQuery
        );
    }

    public System getSystem(
        RequestContext context,
        System.SystemId id
    ) {
        authorizationService.authorize(
            context,
            Permission.SYSTEM_READ
        );

        log.debug(
            "Getting system organizationId={} id={}",
            context.organization().id(),
            id.id()
        );

        return systemRepository.findById(
            context.organization().id(),
            id
        );
    }

    public System create(
        RequestContext context,
        CreateSystemCommand command
    ) {
        log.info(
            "Creating system organizationId={} name={}",
            context.organization().id(),
            command.name()
        );

        this.authorizationService.authorize(context, Permission.SYSTEM_CREATE);
        this.entitlementService.require(context.organization(), Entitlement.SYSTEMS);

        this.entitlementService.requireWithinLimit(
            context.organization(),
            Entitlement.SYSTEMS,
            this.systemRepository.count(context.organization().id())
        );

        final var system = this.systemRepository.save(
            context.organization().id(),
            new System(System.SystemId.generate(), command.name(), command.description(), Instant.now(), Instant.now())
        );

        log.info(
            "Created system organizationId={} systemId={}",
            context.organization().id(),
            system.id()
        );

        return system;
    }

    public System update(
        RequestContext context,
        UpdateSystemCommand command
    ) {
        log.info(
            "Updating system id={} organizationId={} name={}",
            command.id().id(),
            context.organization().id(),
            command.name()
        );

        this.authorizationService.authorize(context, Permission.SYSTEM_UPDATE);
        this.entitlementService.require(context.organization(), Entitlement.SYSTEMS);

        // Only an existing system of the caller's organization
        final var current = this.getSystem(context, command.id());

        final var system = this.systemRepository.save(
            context.organization().id(),
            new System(current.id(), command.name(), command.description(), current.createdAt(), Instant.now())
        );

        log.info(
            "Updated system organizationId={} systemId={}",
            context.organization().id(),
            system.id()
        );

        return system;
    }

    public void delete(
        RequestContext context,
        System.SystemId id
    ) {
        authorizationService.authorize(
            context,
            Permission.SYSTEM_DELETE
        );

        log.debug(
            "Deleting system organizationId={} id={}",
            context.organization().id(),
            id.id()
        );

        // Only a system of the caller's organization, before anything of it is deleted
        this.getSystem(context, id);

        projectDeleter.deleteProjects(id);

        systemRepository.delete(
            context.organization().id(),
            id
        );
    }
}
