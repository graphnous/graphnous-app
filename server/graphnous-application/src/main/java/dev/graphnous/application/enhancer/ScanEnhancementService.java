package dev.graphnous.application.enhancer;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.ConflictException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.project.scanner.ScanExecutor;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.ScanSteps;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.application.scan.result.ScanResultArchive;
import dev.graphnous.application.scan.result.ScanResultRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep.ScanStepType;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;
import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.EnhancerProvider;
import dev.graphnous.enhancer.EnhancerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enhances a scan again after it completed, such as with an enhancer
 * installed or updated since: runs the enhancers on the results the scan
 * kept, and replaces what they added to its graph before.
 * <p>
 * It can store the kept results again first, as the server stores them now:
 * a scan stored by an earlier version then gets what the server has stored
 * since, such as who uses what, without scanning the project again.
 * <p>
 * It runs in the background, as the step that enhances the results, after
 * the step that stores them when they are stored again; the steps run
 * anew, and the scan itself stays completed.
 */
public class ScanEnhancementService {

    private static final Logger log = LoggerFactory.getLogger(ScanEnhancementService.class);

    private final ScanService scanService;
    private final AuthorizationService authorizationService;

    private final ScanResultArchive scanResultArchive;
    private final ScanResultRepository scanResultRepository;

    private final EnhancerRegistry enhancerRegistry;
    private final EnhancerProvider enhancerProvider;
    private final EnhancementRepository enhancementRepository;

    private final ScanSteps scanSteps;
    private final ScanLogService scanLogService;
    private final ScanExecutor scanExecutor;

    /**
     * The scans being enhanced again by this server, so one is not enhanced
     * twice at once.
     */
    private final Set<Scan.ScanId> enhancing = ConcurrentHashMap.newKeySet();

    public ScanEnhancementService(
        final ScanService scanService,
        final AuthorizationService authorizationService,
        final ScanResultArchive scanResultArchive,
        final ScanResultRepository scanResultRepository,
        final EnhancerRegistry enhancerRegistry,
        final EnhancerProvider enhancerProvider,
        final EnhancementRepository enhancementRepository,
        final ScanSteps scanSteps,
        final ScanLogService scanLogService,
        final ScanExecutor scanExecutor
    ) {
        this.scanService = scanService;
        this.authorizationService = authorizationService;

        this.scanResultArchive = scanResultArchive;
        this.scanResultRepository = scanResultRepository;

        this.enhancerRegistry = enhancerRegistry;
        this.enhancerProvider = enhancerProvider;
        this.enhancementRepository = enhancementRepository;

        this.scanSteps = scanSteps;
        this.scanLogService = scanLogService;
        this.scanExecutor = scanExecutor;
    }

    /**
     * Starts enhancing the scan again, in the background.
     *
     * @param enhancers the names of the enhancers to run; every installed
     *                  enhancer when null, none when empty, as when a scan
     *                  is created
     * @param restore   whether to store the kept results again first, which
     *                  removes what every enhancer added before, so only
     *                  the enhancers that run now have added to the scan
     * @throws ValidationException when an enhancer of a name is not installed
     * @throws ConflictException   when the scan is not completed, kept no
     *                             results to enhance, or is being enhanced
     */
    public Scan enhance(
        final RequestContext context,
        final Scan.ScanId scanId,
        final Set<String> enhancers,
        final boolean restore
    ) {
        // Enhancing writes to the scan, as creating it does
        authorizationService.authorize(context, Permission.SCAN_CREATE);

        // Only a scan of the caller's organization
        final var scan = scanService.getScan(context, scanId);

        final var installed = enhancerRegistry.enhancers().stream().map(Enhancer::name).collect(Collectors.toSet());
        final var unknown = enhancers == null
            ? List.<String>of()
            : enhancers.stream().filter(name -> !installed.contains(name)).sorted().toList();

        if (!unknown.isEmpty()) {
            throw new ValidationException("No such enhancer: " + String.join(", ", unknown));
        }

        if (scan.status() != Scan.ScanStatus.COMPLETED) {
            throw new ConflictException(
                "Scan %s is %s; only a completed scan can be enhanced again".formatted(scanId.id(), scan.status())
            );
        }

        if (!scanResultArchive.exists(scanId)) {
            throw new ConflictException(
                "Scan %s kept no results to enhance; scan the project again".formatted(scanId.id())
            );
        }

        if (!enhancing.add(scanId)) {
            throw new ConflictException("Scan %s is already being enhanced".formatted(scanId.id()));
        }

        log.info(
            "Enhancing scan again organizationId={} scanId={} enhancers={} restore={}",
            context.organization().id(),
            scanId.id(),
            enhancers == null ? "all" : enhancers,
            restore
        );

        // Enhancing waits for the results to be stored again, so its step
        // runs anew once they are
        final var first = restore ? ScanStepType.STORE : ScanStepType.ENHANCE_RESULTS;

        try {
            scanSteps.restart(scanId, first);
            final var chosen = enhancers == null ? null : Set.copyOf(enhancers);

            scanExecutor.execute(() -> run(scanId, chosen, restore));
        } catch (final RuntimeException e) {
            // Not started, so nothing else ends it
            failQuietly(scanId, first, message(e));
            enhancing.remove(scanId);

            throw e;
        }

        return scan;
    }

    /**
     * @param enhancers the names of the enhancers to run; null for every
     *                  installed one
     * @param restore   whether to store the results again first
     */
    private void run(
        final Scan.ScanId scanId,
        final Set<String> enhancers,
        final boolean restore
    ) {
        var step = restore ? ScanStepType.STORE : ScanStepType.ENHANCE_RESULTS;

        try {
            final var results = scanResultArchive.load(scanId);

            if (restore) {
                // Replaces the graph of the scan at once, so a failure
                // leaves it, and what the enhancers added to it, as it was
                scanResultRepository.save(scanId, results);
                logQuietly(scanId, ScanLogLevel.INFO, "Stored the results again");

                scanSteps.complete(scanId, ScanStepType.STORE);

                step = ScanStepType.ENHANCE_RESULTS;
                scanSteps.restart(scanId, step);
            }

            final var enhancements = results.stream()
                .flatMap(result -> enhancerProvider
                    .enhanceScan(result, enhancer -> enhancers == null || enhancers.contains(enhancer.name()))
                    .stream())
                .toList();

            enhancementRepository.replace(scanId, enhancements);

            if (enhancements.isEmpty()) {
                logQuietly(scanId, ScanLogLevel.INFO, "Enhanced the results again; no enhancer applies to them");
            }

            EnhancementSummary.describe(enhancements).forEach(description ->
                logQuietly(scanId, ScanLogLevel.INFO, "Enhanced the results again with " + description)
            );

            scanSteps.complete(scanId, ScanStepType.ENHANCE_RESULTS);
        } catch (final RuntimeException e) {
            log.warn("Enhancing scan again failed scanId={} step={}", scanId.id(), step, e);

            logQuietly(
                scanId,
                ScanLogLevel.WARN,
                (step == ScanStepType.STORE ? "Storing the results again failed: " : "Enhancing again failed: ") + message(e)
            );
            failQuietly(scanId, step, message(e));
        } finally {
            enhancing.remove(scanId);
        }
    }

    private void failQuietly(
        final Scan.ScanId scanId,
        final ScanStepType step,
        final String error
    ) {
        try {
            scanSteps.fail(scanId, step, error);
        } catch (final RuntimeException ignored) {
            // The failure is logged either way
        }
    }

    private void logQuietly(
        final Scan.ScanId scanId,
        final ScanLogLevel level,
        final String message
    ) {
        try {
            scanLogService.log(scanId, level, message);
        } catch (final RuntimeException ignored) {
            // The step still says how it went
        }
    }

    private static String message(final Throwable error) {
        return error.getMessage() == null
            ? error.getClass().getSimpleName()
            : error.getMessage();
    }
}
