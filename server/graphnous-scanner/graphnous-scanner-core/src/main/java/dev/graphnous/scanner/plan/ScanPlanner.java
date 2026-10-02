package dev.graphnous.scanner.plan;

import java.nio.file.Path;

public interface ScanPlanner {

    ScanPlan plan(final Path path);

}
