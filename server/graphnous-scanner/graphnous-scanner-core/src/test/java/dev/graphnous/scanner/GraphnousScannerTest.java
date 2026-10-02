package dev.graphnous.scanner;

import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;
import dev.graphnous.scanner.plan.ScanPlan;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphnousScannerTest {

    @Test
    void plansAndExecutesTheScanForThePath() {
        final var path = Path.of("repo");
        final var plan = new ScanPlan(List.of(new ScanTarget()));
        final var result = new ScanResultSchema();

        final var plannedPaths = new ArrayList<Path>();
        final var executed = new ArrayList<ScanPlan>();

        final var scanner = new GraphnousScanner(
            planPath -> {
                plannedPaths.add(planPath);
                return plan;
            },
            (executePath, executePlan) -> {
                assertThat(executePath).isEqualTo(path);
                executed.add(executePlan);
                return new ScanReport(List.of(result), List.of());
            }
        );

        final var report = scanner.scan(path);

        assertThat(plannedPaths).containsExactly(path);
        assertThat(executed).containsExactly(plan);
        assertThat(report.results()).containsExactly(result);
        assertThat(report.failures()).isEmpty();
    }

    @Test
    void notifiesListenersInOrder() {
        final var plan = new ScanPlan(List.of());
        final var events = new ArrayList<String>();

        final var listener = new ScannerListener() {
            @Override
            public void onScanStarted() {
                events.add("started");
            }

            @Override
            public void onPlanCreated(final ScanPlan created) {
                assertThat(created).isSameAs(plan);
                events.add("planned");
            }

            @Override
            public void onStepChanged(final ScanStep step) {
                events.add("step");
            }

            @Override
            public void onStepFailed(final ScanStep step, final ScanFailure failure) {
                events.add("failed");
            }

            @Override
            public void onScanComplete() {
                events.add("completed");
            }
        };

        final var scanner = new GraphnousScanner(
            path -> {
                events.add("plan");
                return plan;
            },
            (path, executePlan) -> {
                events.add("execute");
                return new ScanReport(List.of(), List.of());
            },
            List.of(listener)
        );

        scanner.scan(Path.of("repo"));

        assertThat(events).containsExactly(
            "started",
            "plan",
            "planned",
            "execute",
            "completed"
        );
    }
}
