package dev.graphnous.scanner.plan;

import dev.graphnous.core.model.ScanTarget;

import java.nio.file.Path;
import java.util.Set;

public interface ScanPlanner {

    /**
     * Plans every target found below the path.
     */
    default ScanPlan plan(final Path path) {
        return plan(path, Set.of());
    }

    /**
     * Plans the targets found below the path in the given languages; the
     * others are left out of the plan.
     *
     * @param languages the languages to scan; every language when empty
     */
    ScanPlan plan(Path path, Set<ScanTarget.Language> languages);

}
