package dev.graphnous.scanner.executor;

import dev.graphnous.scanner.ScanReport;
import dev.graphnous.scanner.plan.ScanPlan;

import java.nio.file.Path;

public interface ScanExecutor {

    /**
     * Scans each target of the plan. A target whose scanner fails is
     * reported in {@link ScanReport#failures()} without stopping the others.
     */
    ScanReport execute(final Path path, final ScanPlan plan);

}
