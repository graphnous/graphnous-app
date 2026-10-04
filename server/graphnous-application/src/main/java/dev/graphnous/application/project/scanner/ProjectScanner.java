package dev.graphnous.application.project.scanner;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.enhancer.Enhancement;
import dev.graphnous.application.enhancer.EnhancerPipeline;
import dev.graphnous.application.enhancer.EnhancerRegistry;
import dev.graphnous.application.enhancer.RuleOutcome;
import dev.graphnous.application.event.EventPublisher;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanCompletedEvent;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep.ScanStepType;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;

import dev.graphnous.scanner.ScanFailure;
import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProjectScanner {

    private final ScanService scanService;
    private final ProjectService projectService;

    private final ScanLogService scanLogService;

    private final ScanExecutor scanExecutor;

    private final SourceCheckout sourceCheckout;
    private final RepositoryScanner repositoryScanner;
    private final ScanResultRepository scanResultRepository;

    private final EnhancerPipeline enhancerPipeline;
    private final EnhancerRegistry enhancerRegistry;

    private final ScanSteps scanSteps;

    private final EventPublisher eventPublisher;

    public ProjectScanner(
        final ScanService scanService,
        final ProjectService projectService,
        final ScanLogService scanLogService,
        final ScanExecutor scanExecutor,
        final SourceCheckout sourceCheckout,
        final RepositoryScanner repositoryScanner,
        final ScanResultRepository scanResultRepository,
        final EnhancerPipeline enhancerPipeline,
        final EnhancerRegistry enhancerRegistry,
        final ScanSteps scanSteps,
        final EventPublisher eventPublisher
    ) {
        this.scanService = scanService;
        this.projectService = projectService;

        this.scanLogService = scanLogService;

        this.scanExecutor = scanExecutor;

        this.sourceCheckout = sourceCheckout;
        this.repositoryScanner = repositoryScanner;
        this.scanResultRepository = scanResultRepository;

        this.enhancerPipeline = enhancerPipeline;
        this.enhancerRegistry = enhancerRegistry;

        this.scanSteps = scanSteps;

        this.eventPublisher = eventPublisher;
    }

    public void startScan(
        final StartScanCommand command,
        final RequestContext context
    ) {
        final var scan = this.scanService.getScan(
            context,
            command.scanId()
        );

        final var project = this.projectService.getProject(
            context,
            scan.projectId()
        );

        this.scanService.updateStatus(
            context,
            scan.id(),
            Scan.ScanStatus.QUEUED
        );

        this.scanExecutor.execute(
            () -> this.executeScan(project, scan, context)
        );

    }

    private void executeScan(
        final Project project,
        final Scan scan,
        final RequestContext context
    ) {
        final ScanLogger logger = (level, message) ->
            this.scanLogService.log(scan.id(), level, message);

        final var scanId = scan.id();

        Path checkout = null;

        // The step that fails the scan when something goes wrong
        ScanStepType step = null;

        try {
            this.scanService.startScan(context, scanId);
            this.scanService.updateStatus(context, scanId, Scan.ScanStatus.RUNNING);

            step = start(scanId, ScanStepType.CHECKOUT);
            logger.log(
                ScanLogLevel.INFO,
                "Checking out " + project.gitUrl() + describe(scan.revision())
            );
            checkout = this.sourceCheckout.checkout(scanId, project.gitUrl(), scan.revision(), logger);
            this.scanSteps.complete(scanId, step);

            final var path = scanPath(checkout, project.path());

            step = start(scanId, ScanStepType.PLAN);
            final var plan = this.repositoryScanner.plan(path, logger);
            this.scanSteps.complete(scanId, step);

            step = start(scanId, ScanStepType.SCAN);
            final var report = this.repositoryScanner.scan(scanId, path, plan, logger);

            final var total = report.results().size() + report.failures().size();

            logger.log(
                report.hasFailures() ? ScanLogLevel.WARN : ScanLogLevel.INFO,
                "Scanned " + report.results().size() + " of " + total + " targets"
            );

            // A scan in which some targets failed still produced results
            if (report.hasFailures() && report.results().isEmpty()) {
                throw new IllegalStateException("No target could be scanned: " + describe(report.failures()));
            }

            this.scanSteps.complete(scanId, step);

            final var status = currentStatus(scan, context);

            if (status.isEmpty()) {
                // Deleted after recovery failed it; nothing left to report to
                return;
            }

            if (status.get().isEndState()) {
                logger.log(
                    ScanLogLevel.WARN,
                    "Scan finished after it was marked " + status.get() + "; discarding its results"
                );
                return;
            }

            step = start(scanId, ScanStepType.STORE);
            this.scanResultRepository.save(scanId, report.results());
            this.scanSteps.complete(scanId, step);

            // The results are stored, so enhancing does not fail the scan
            step = null;
            enhance(scanId, report.results(), logger);

            complete(scan, report.results(), context);
        } catch (Exception e) {
            fail(scan, context, logger, step, e);
        } finally {
            if (checkout != null) {
                removeCheckout(scan, logger);
            }
        }
    }

    /**
     * Stores and enhances results scanned elsewhere, in the background. The
     * scan's checkout, plan and scan steps are already skipped.
     */
    public void storeUploaded(
        final Scan.ScanId scanId,
        final List<ScanResultSchema> results,
        final RequestContext context
    ) {
        final var scan = this.scanService.getScan(context, scanId);

        this.scanService.updateStatus(context, scan.id(), Scan.ScanStatus.QUEUED);

        this.scanExecutor.execute(
            () -> this.executeUpload(scan, results, context)
        );
    }

    private void executeUpload(
        final Scan scan,
        final List<ScanResultSchema> results,
        final RequestContext context
    ) {
        final ScanLogger logger = (level, message) ->
            this.scanLogService.log(scan.id(), level, message);

        final var scanId = scan.id();

        ScanStepType step = null;

        try {
            this.scanService.startScan(context, scanId);
            this.scanService.updateStatus(context, scanId, Scan.ScanStatus.RUNNING);

            logger.log(ScanLogLevel.INFO, "Storing " + results.size() + " uploaded scan result(s)");

            step = start(scanId, ScanStepType.STORE);
            this.scanResultRepository.save(scanId, results);
            this.scanSteps.complete(scanId, step);

            // The results are stored, so enhancing does not fail the scan
            step = null;
            enhance(scanId, results, logger);

            complete(scan, results, context);
        } catch (Exception e) {
            fail(scan, context, logger, step, e);
        }
    }

    /**
     * Completes the scan, and announces it with its results.
     */
    private void complete(
        final Scan scan,
        final List<ScanResultSchema> results,
        final RequestContext context
    ) {
        this.scanService.updateStatus(context, scan.id(), Scan.ScanStatus.COMPLETED);

        this.eventPublisher.publish(
            new ScanCompletedEvent(scan.id(), scan.projectId(), results)
        );
    }

    /**
     * Fails the scan and the step it was in, unless recovery already failed
     * or since deleted it.
     */
    private void fail(
        final Scan scan,
        final RequestContext context,
        final ScanLogger logger,
        final ScanStepType step,
        final Exception error
    ) {
        if (currentStatus(scan, context).filter(status -> !status.isEndState()).isEmpty()) {
            return;
        }

        logQuietly(logger, ScanLogLevel.ERROR, "Scan failed: " + message(error));

        if (step != null) {
            failQuietly(scan.id(), step, message(error));
        }

        this.scanService.updateStatus(context, scan.id(), Scan.ScanStatus.FAILED);
    }

    private ScanStepType start(
        final Scan.ScanId scanId,
        final ScanStepType step
    ) {
        this.scanSteps.start(scanId, step);

        return step;
    }

    /**
     * Runs the installed enhancers on the scan's results, as two steps: the
     * target-scoped enhancers on each result, then the scan-scoped ones on
     * all of them. A step that fails is recorded and logged, but does not
     * fail the scan; the scan-wide step is skipped after a failed first one.
     */
    private void enhance(
        final Scan.ScanId scanId,
        final List<ScanResultSchema> results,
        final ScanLogger logger
    ) {
        final var manifests = this.enhancerRegistry.installed();
        final var enhancers = !manifests.isEmpty() && !results.isEmpty();

        final var enhanced = runEnhanceStep(scanId, ScanStepType.ENHANCE_RESULTS, logger, () -> {
            if (!enhancers) {
                return;
            }

            for (final var target : this.enhancerPipeline.enhanceTargets(scanId, results, manifests)) {
                for (final var enhancement : target.enhancements()) {
                    log(logger, "Enhanced " + describe(target.target()) + " with ", enhancement);
                }
            }
        });

        if (!enhanced) {
            return;
        }

        runEnhanceStep(scanId, ScanStepType.ENHANCE_SCAN, logger, () -> {
            if (!enhancers) {
                return;
            }

            for (final var enhancement : this.enhancerPipeline.enhanceScan(scanId, results, manifests)) {
                log(logger, "Enhanced the scan with ", enhancement);
            }
        });
    }

    /**
     * @return whether the step completed
     */
    private boolean runEnhanceStep(
        final Scan.ScanId scanId,
        final ScanStepType step,
        final ScanLogger logger,
        final Runnable enhance
    ) {
        this.scanSteps.start(scanId, step);

        try {
            enhance.run();
        } catch (final RuntimeException e) {
            logQuietly(logger, ScanLogLevel.WARN, "Enhancing failed: " + message(e));
            failQuietly(scanId, step, message(e));

            return false;
        }

        this.scanSteps.complete(scanId, step);

        return true;
    }

    private void failQuietly(
        final Scan.ScanId scanId,
        final ScanStepType step,
        final String error
    ) {
        try {
            this.scanSteps.fail(scanId, step, error);
        } catch (Exception ignored) {
            // The scan status is still updated
        }
    }

    private static String describe(final List<ScanFailure> failures) {
        return failures.stream()
            .map(failure -> describe(failure.target()) + ": " + message(failure.error()))
            .collect(Collectors.joining("; "));
    }

    private static String describe(final ScanTarget target) {
        final var path = target.getPath() == null || target.getPath().isEmpty() ? "." : target.getPath();

        return target.getLanguage() + " " + path;
    }

    /**
     * Logs what an enhancer that applied did, e.g. "io.acme.spring:
     * controllers matched 3, services matched 0". Rules that left matched
     * nodes without an 'exactlyOne' relationship make it a warning.
     */
    private static void log(
        final ScanLogger logger,
        final String prefix,
        final Enhancement enhancement
    ) {
        if (!enhancement.applied()) {
            return;
        }

        final var rules = enhancement.rules()
            .stream()
            .map(ProjectScanner::describe)
            .collect(Collectors.joining(", "));

        final var skipped = enhancement.rules().stream().anyMatch(rule -> rule.skipped() > 0);

        logger.log(
            skipped ? ScanLogLevel.WARN : ScanLogLevel.INFO,
            prefix + enhancement.enhancerId() + (rules.isEmpty() ? "" : ": " + rules)
        );
    }

    private static String describe(final RuleOutcome rule) {
        return rule.ruleId() + " matched " + rule.matched()
            + (rule.skipped() > 0 ? " (" + rule.skipped() + " without a unique target)" : "");
    }

    /**
     * The scan's status now, or empty when it was deleted. A scan that took
     * too long may have been failed by recovery while it ran, and deleted
     * after that.
     */
    private Optional<Scan.ScanStatus> currentStatus(
        final Scan scan,
        final RequestContext context
    ) {
        try {
            return Optional.of(this.scanService.getScan(context, scan.id()).status());
        } catch (final NotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * The directory of the project within the checkout; a project can be a
     * subdirectory of its repository.
     */
    private static Path scanPath(
        final Path checkout,
        final String projectPath
    ) {
        if (projectPath == null || projectPath.isBlank()) {
            return checkout;
        }

        final var path = checkout.resolve(projectPath).normalize();

        if (!path.startsWith(checkout)) {
            throw new IllegalArgumentException(
                "Project path is outside the repository: " + projectPath
            );
        }

        return path;
    }

    private void removeCheckout(
        final Scan scan,
        final ScanLogger logger
    ) {
        try {
            this.sourceCheckout.remove(scan.id(), logger);
        } catch (Exception e) {
            // The scan itself is done; a leftover checkout does not change its outcome
            logQuietly(logger, ScanLogLevel.WARN, "Failed to remove checkout: " + message(e));
        }
    }

    private static void logQuietly(
        final ScanLogger logger,
        final ScanLogLevel level,
        final String message
    ) {
        try {
            logger.log(level, message);
        } catch (Exception ignored) {
            // The scan status is still updated
        }
    }

    private static String describe(final Scan.SourceRevision revision) {
        if (revision == null) {
            return "";
        }

        final var parts = new StringBuilder();

        if (revision.branch() != null && !revision.branch().isBlank()) {
            parts.append(" (branch ").append(revision.branch());
        }

        if (revision.revision() != null && !revision.revision().isBlank()) {
            parts.append(parts.isEmpty() ? " (" : ", ").append("revision ").append(revision.revision());
        }

        return parts.isEmpty() ? "" : parts.append(')').toString();
    }

    private static String message(final Throwable error) {
        return error.getMessage() == null
            ? error.getClass().getSimpleName()
            : error.getMessage();
    }
}
