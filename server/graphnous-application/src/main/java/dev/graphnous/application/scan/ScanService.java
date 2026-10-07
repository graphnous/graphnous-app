package dev.graphnous.application.scan;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.entitlement.Entitlement;
import dev.graphnous.application.entitlement.EntitlementService;
import dev.graphnous.application.event.EventPublisher;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.GraphnousException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;
import dev.graphnous.domain.scan.ScanStep.ScanStepType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;

public class ScanService {

    private final EventPublisher eventPublisher;

    private final ScanRepository scanRepository;
    private final ScanDeleter scanDeleter;
    private final ScanSteps scanSteps;
    private final ScanRetention scanRetention;

    private final AuthorizationService authorizationService;
    private final EntitlementService entitlementService;

    private final ProjectService projectService;

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);


    public ScanService(
        final EventPublisher eventPublisher,
        final ScanRepository scanRepository,
        final ScanDeleter scanDeleter,
        final ScanSteps scanSteps,
        final ScanRetention scanRetention,
        final ProjectService projectService,
        final AuthorizationService authorizationService,
        final EntitlementService entitlementService
    ) {
        this.eventPublisher = eventPublisher;

        this.scanRepository = scanRepository;
        this.scanDeleter = scanDeleter;
        this.scanSteps = scanSteps;
        this.scanRetention = scanRetention;

        this.authorizationService = authorizationService;
        this.entitlementService = entitlementService;

        this.projectService = projectService;
    }

    public Page<Scan> getScans(
        final RequestContext context,
        final PageQuery pageQuery,
        final Project.ProjectId projectId
    ) {
        authorizationService.authorize(
            context,
            Permission.SCAN_READ
        );

        this.projectService.getProject(
            context,
            projectId
        );

        log.debug(
            "Getting scans organizationId={} projectId={} page={} size={}",
            context.organization().id(),
            projectId.id(),
            pageQuery.page(),
            pageQuery.size()
        );

        return scanRepository.findAll(
            projectId,
            pageQuery
        );
    }

    public Scan getScan(
        final RequestContext context,
        final Scan.ScanId id
    ) {
        authorizationService.authorize(
            context,
            Permission.SCAN_READ
        );

        log.debug(
            "Getting project organizationId={} id={}",
            context.organization().id(),
            id.id()
        );

        final var scan = scanRepository.findById(
            id
        );

        projectService.getProject(context, scan.projectId());

        return scan;
    }

    public Scan create(
        RequestContext context,
        CreateScanCommand command
    ) {
        log.info(
            "Creating scan organizationId={} projectId={}",
            context.organization().id(),
            command.projectId().id()
        );

        final var scan = newScan(context, command.projectId(), command.revision(), command.branch());

        this.eventPublisher.publish(
            new StartScanEvent(
                scan.id(),
                context
            )
        );

        return scan;
    }

    /**
     * Creates a scan from results scanned elsewhere, such as by a CI job or
     * a build plugin. The steps that produce results are skipped; storing
     * and enhancing the results continue in the background.
     *
     * @throws ValidationException when the results cannot be stored: none,
     *                             one without a target, or two for the
     *                             same target
     */
    public Scan upload(
        final RequestContext context,
        final UploadScanCommand command
    ) {
        log.info(
            "Uploading scan organizationId={} projectId={} results={}",
            context.organization().id(),
            command.projectId().id(),
            command.results().size()
        );

        this.authorizationService.authorize(context, Permission.SCAN_CREATE);

        validate(command.results());

        final var scan = newScan(context, command.projectId(), command.revision(), command.branch());

        for (final var step : List.of(ScanStepType.CHECKOUT, ScanStepType.PLAN, ScanStepType.SCAN)) {
            this.scanSteps.skip(scan.id(), step);
        }

        this.eventPublisher.publish(
            new UploadedScanEvent(
                scan.id(),
                command.results(),
                context
            )
        );

        return scan;
    }

    /**
     * A new pending scan of the project, with its steps.
     */
    private Scan newScan(
        final RequestContext context,
        final Project.ProjectId projectId,
        final String revision,
        final String branch
    ) {
        this.authorizationService.authorize(context, Permission.SCAN_CREATE);
        this.entitlementService.require(context.organization(), Entitlement.SCANS);

        // Only for an existing project the caller may read
        this.projectService.getProject(context, projectId);

        // Before the limit is checked, so old scans make way for new ones
        this.scanRetention.makeRoomFor(projectId);

        this.entitlementService.requireWithinLimit(
            context.organization(),
            Entitlement.SCANS,
            this.scanRepository.count(projectId)
        );

        final var scan = this.scanRepository.save(
            new Scan(
                Scan.ScanId.generate(),
                projectId,
                Scan.ScanStatus.PENDING,
                new Scan.SourceRevision(revision, branch),
                Instant.now(),
                Instant.now(),
                null
            )
        );

        this.scanSteps.create(scan.id());

        log.info(
            "Created scan organizationId={} projectId={} scanId={}",
            context.organization().id(),
            projectId.id(),
            scan.id()
        );

        return scan;
    }

    /**
     * Uploaded results are stored as one graph per target, so each needs a
     * target of its own.
     */
    private static void validate(final List<ScanResult> results) {
        if (results.isEmpty()) {
            throw new ValidationException("Upload at least one scan result");
        }

        final var paths = new HashSet<String>();

        for (int i = 0; i < results.size(); i++) {
            final var target = results.get(i).getTarget();

            if (target == null || target.getPath() == null || target.getLanguage() == null) {
                throw new ValidationException("Scan result " + (i + 1) + " has no target with a path and a language");
            }

            if (!paths.add(target.getPath())) {
                throw new ValidationException("More than one scan result is for target " + target.getPath());
            }
        }
    }

    /**
     * The steps of executing the scan, in the order they run.
     */
    public List<ScanStep> getSteps(
        final RequestContext context,
        final Scan.ScanId scanId
    ) {
        // Checks the caller may read the scan, and that it exists
        this.getScan(context, scanId);

        return this.scanSteps.steps(scanId);
    }

    public void delete(
        final RequestContext context,
        final DeleteScanCommand command
    ) {
        authorizationService.authorize(
            context,
            Permission.SCAN_DELETE
        );

        log.info(
            "Deleting scan organizationId={} scanId={}",
            context.organization().id(),
            command.scanId().id()
        );

        final var scan = this.getScan(context, command.scanId());

        // A scan that is still active would keep writing results and logs
        // for a scan that no longer exists
        if (!scan.status().isEndState()) {
            throw new ConflictException(
                "Scan %s cannot be deleted while it has status '%s'".formatted(scan.id().id(), scan.status())
            );
        }

        scanDeleter.deleteScan(scan.id());
    }

    public void startScan(
        final RequestContext context,
        final Scan.ScanId scanId
    ) {
        final var scan = this.getScan(context, scanId);
        final var project = this.projectService.getProject(context, scan.projectId());

        this.scanRepository.startScan(
            this.projectService.getSnapshotId(project.id()),
            scan.id()
        );
    }

    public void updateStatus(
        final RequestContext context,
        final Scan.ScanId scanId,
        final Scan.ScanStatus status
    ) {
        final var scan = this.getScan(
            context,
            scanId
        );

        if (scan.status().equals(status)) {
            throw new GraphnousException("Scan %s already has status '%s'".formatted(scanId.id(), scan.status().toString()));
        }

        if (scan.status().isEndState()) {
            throw new GraphnousException("Scan %s has end State:'%s'".formatted(scanId.id(), scan.status().toString()));
        }

        final var updatedScan = scan.withStatus(status, Instant.now());

        this.scanRepository.save(updatedScan);
    }


}
