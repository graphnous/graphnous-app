package dev.graphnous.scanner;

import dev.graphnous.scanner.executor.ScanExecutor;
import dev.graphnous.scanner.plan.ScanPlanner;

import java.nio.file.Path;
import java.util.List;

import static java.util.Collections.emptyList;

public class GraphnousScanner {

    private final ScanPlanner scanPlanner;
    private final ScanExecutor scanExecutor;

    private final List<ScannerListener> listeners;

    public GraphnousScanner(
        final ScanPlanner scanPlanner,
        final ScanExecutor scanExecutor
    ) {
        this(scanPlanner, scanExecutor, emptyList());
    }

    public GraphnousScanner(
        final ScanPlanner scanPlanner,
        final ScanExecutor scanExecutor,
        final List<ScannerListener> listeners
    ) {
        this.scanPlanner = scanPlanner;
        this.scanExecutor = scanExecutor;

        this.listeners = listeners;
    }

    /**
     * Scans every target found in the repository; see {@link ScanReport}
     * for the results and the targets that failed.
     */
    public ScanReport scan(final Path path) {
        listeners.forEach(ScannerListener::onScanStarted);

        final var plan = scanPlanner.plan(path);

        listeners.forEach(listener ->
            listener.onPlanCreated(plan)
        );

        final var report = scanExecutor.execute(path, plan);

        listeners.forEach(ScannerListener::onScanComplete);

        return report;
    }

}
