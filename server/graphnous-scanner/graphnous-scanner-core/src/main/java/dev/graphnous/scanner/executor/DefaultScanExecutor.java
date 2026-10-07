package dev.graphnous.scanner.executor;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.ScanFailure;
import dev.graphnous.scanner.ScanReport;
import dev.graphnous.scanner.ScannerListener;
import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.plan.ScanPlan;
import dev.graphnous.scanner.sandbox.ScanSandbox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultScanExecutor implements ScanExecutor {

    private final List<ScannerDefinition> scanners;
    private final ScanSandbox sandbox;
    private final List<ScannerListener> listeners;

    public DefaultScanExecutor(
        final List<ScannerDefinition> scanners,
        final ScanSandbox sandbox
    ) {
        this(scanners, sandbox, Collections.emptyList());
    }

    public DefaultScanExecutor(
        final List<ScannerDefinition> scanners,
        final ScanSandbox sandbox,
        final List<ScannerListener> listeners
    ) {
        this.scanners = scanners;
        this.sandbox = sandbox;

        this.listeners = listeners;
    }

    @Override
    public ScanReport execute(
        final Path path,
        final ScanPlan plan
    ) {
        final var results = new ArrayList<ScanResult>();
        final var failures = new ArrayList<ScanFailure>();

        for (int i = 0; i < plan.targets().size(); i++) {
            final var target = plan.targets().get(i);

            final var step = new ScannerListener.ScanStep(
                i + 1,
                plan.targets().size(),
                target
            );

            listeners.forEach(listener ->
                listener.onStepChanged(step)
            );

            try {
                results.add(execute(path, target));
            } catch (RuntimeException e) {
                // An interrupted scan stops as a whole rather than skipping ahead
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }

                final var failure = new ScanFailure(target, e);

                failures.add(failure);

                listeners.forEach(listener ->
                    listener.onStepFailed(step, failure)
                );
            }
        }

        return new ScanReport(results, failures);
    }

    private ScanResult execute(final Path path, final ScanTarget target) {
        final var scanner = findScanner(target);

        return sandbox.execute(scanner, path, target);
    }

    private ScannerDefinition findScanner(final ScanTarget target) {
        return scanners.stream()
            .filter(scanner -> scanner.supports(target))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No scanner available for target: " + target
            ));
    }

}