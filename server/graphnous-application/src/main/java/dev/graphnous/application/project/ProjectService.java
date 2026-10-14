package dev.graphnous.application.project;

import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.scan.ScanLanguages;
import dev.graphnous.application.scan.result.ScanExcludes;
import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.entitlement.Entitlement;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AuthorizationService authorizationService;
    private final EntitlementService entitlementService;

    private final SystemService systemService;

    private final ProjectDeleter projectDeleter;

    private final ScanLanguages scanLanguages;

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    public ProjectService(
        final ProjectRepository projectRepository,
        final AuthorizationService authorizationService,
        final EntitlementService entitlementService,
        final SystemService systemService,
        final ProjectDeleter projectDeleter,
        final ScanLanguages scanLanguages
    ) {
        this.projectRepository = projectRepository;

        this.authorizationService = authorizationService;
        this.entitlementService = entitlementService;

        this.systemService = systemService;

        this.projectDeleter = projectDeleter;

        this.scanLanguages = scanLanguages;
    }

    public Page<Project> getProjects(
        final RequestContext context,
        final PageQuery pageQuery,
        final System.SystemId systemId
    ) {
        authorizationService.authorize(
            context,
            Permission.PROJECT_READ
        );

        log.debug(
            "Getting projects organizationId={} page={} size={}",
            context.organization().id(),
            pageQuery.page(),
            pageQuery.size()
        );

        // Only the projects of a system of the caller's organization
        systemService.getSystem(context, systemId);

        return projectRepository.findAll(
            systemId,
            pageQuery
        );
    }

    public Project getProject(
        final RequestContext context,
        final Project.ProjectId id
    ) {
        authorizationService.authorize(
            context,
            Permission.PROJECT_READ
        );

        log.debug(
            "Getting project organizationId={} id={}",
            context.organization().id(),
            id.id()
        );

        final var project = projectRepository.findById(
            id
        );

        systemService.getSystem(
            context,
            project.systemId()
        );

        return project;
    }

    public Project create(
        final RequestContext context,
        final CreateProjectCommand command
    ) {
        log.info(
            "Creating project organizationId={} name={}",
            context.organization().id(),
            command.name()
        );

        this.authorizationService.authorize(context, Permission.PROJECT_CREATE);
        this.entitlementService.require(context.organization(), Entitlement.PROJECTS);

        // Check Project -> System -> Organization

        this.entitlementService.requireWithinLimit(
            context.organization(),
            Entitlement.PROJECTS,
            this.projectRepository.count(command.systemId())
        );

        this.systemService.getSystem(
            context,
            command.systemId()
        );

        final var project = this.projectRepository.save(
            new Project(
                Project.ProjectId.generate(),
                command.name(),
                command.description(),
                command.gitUrl(),
                command.path(),
                command.systemId(),
                Instant.now(),
                Instant.now()
            )
        );

        log.info(
            "Created project organizationId={} projectId={}",
            context.organization().id(),
            project.id()
        );

        return project;
    }

    public Project update(
        final RequestContext context,
        final UpdateProjectCommand command
    ) {
        log.info(
            "Updating project id={} organizationId={} name={}",
            command.id().id(),
            context.organization().id(),
            command.name()
        );

        this.authorizationService.authorize(context, Permission.PROJECT_UPDATE);
        this.entitlementService.require(context.organization(), Entitlement.PROJECTS);

        // Checks access through the system the project belongs to, not the
        // one in the request: a project stays in its system
        final var currentProject = this.getProject(
            context,
            command.id()
        );

        final var project = this.projectRepository.save(
            new Project(
                command.id(),
                command.name(),
                command.description(),
                currentProject.gitUrl(),
                command.path(),
                currentProject.systemId(),
                currentProject.createdAt(),
                Instant.now()
            )
        );

        log.info(
            "Updated project organizationId={} projectId={}",
            context.organization().id(),
            project.id()
        );

        return project;
    }

    public void delete(
        final RequestContext context,
        final DeleteProjectCommand command
    ) {
        authorizationService.authorize(
            context,
            Permission.PROJECT_DELETE
        );

        log.debug(
            "Deleting Project organizationId={} projectId={}",
            context.organization().id(),
            command.projectId().id()
        );

        final var project = this.getProject(context, command.projectId());

        projectDeleter.deleteProject(
            project.systemId(),
            command.projectId()
        );
    }

    public UUID getSnapshotId(
        final Project.ProjectId projectId
    ) {
        return this.projectRepository.getOrCreateSnapshot(projectId);
    }

    /**
     * The patterns of the files the project leaves out of its scans, such
     * as {@code *.spec.ts}; a scan can be created with other ones.
     */
    public List<String> getScanExcludes(
        final RequestContext context,
        final Project.ProjectId id
    ) {
        // Only for a project the caller may read
        this.getProject(context, id);

        final var excludes = this.projectRepository.findScanExcludes(id);

        return excludes == null ? List.of() : excludes;
    }

    /**
     * Sets the patterns of the files the project leaves out of its scans
     * from now on; the scans it has stay as they are.
     *
     * @return the patterns as they are kept
     * @throws ValidationException when there are too many, or one is not
     *                             well formed
     */
    public List<String> updateScanExcludes(
        final RequestContext context,
        final Project.ProjectId id,
        final List<String> excludes
    ) {
        this.authorizationService.authorize(context, Permission.PROJECT_UPDATE);

        this.getProject(context, id);

        final var normalized = ScanExcludes.normalize(excludes);

        log.info(
            "Updating scan excludes of project id={} organizationId={} patterns={}",
            id.id(),
            context.organization().id(),
            normalized.size()
        );

        this.projectRepository.saveScanExcludes(id, normalized);

        return normalized;
    }

    /**
     * The languages the project scans, such as {@code JAVA}; none when it
     * scans every language its scans find. A scan can be created with
     * other ones.
     */
    public Set<String> getScanLanguages(
        final RequestContext context,
        final Project.ProjectId id
    ) {
        // Only for a project the caller may read
        this.getProject(context, id);

        return this.scanLanguages.current(this.projectRepository.findScanLanguages(id));
    }

    /**
     * Sets the languages the project scans from now on; the scans it has
     * stay as they are. None scans every language its scans find.
     *
     * @return the languages as they are kept
     * @throws ValidationException when this server has no scanner for one
     */
    public Set<String> updateScanLanguages(
        final RequestContext context,
        final Project.ProjectId id,
        final Collection<String> languages
    ) {
        this.authorizationService.authorize(context, Permission.PROJECT_UPDATE);

        this.getProject(context, id);

        final var normalized = this.scanLanguages.normalize(languages);

        log.info(
            "Updating scan languages of project id={} organizationId={} languages={}",
            id.id(),
            context.organization().id(),
            normalized.isEmpty() ? "all" : normalized
        );

        this.projectRepository.saveScanLanguages(id, normalized);

        return normalized;
    }
}
