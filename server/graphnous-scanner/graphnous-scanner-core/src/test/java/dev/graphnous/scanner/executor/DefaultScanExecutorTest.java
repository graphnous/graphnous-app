package dev.graphnous.scanner.executor;

import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.ScanFailure;
import dev.graphnous.scanner.ScannerListener;
import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.plan.ScanPlan;
import dev.graphnous.scanner.sandbox.ScanSandbox;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultScanExecutorTest {

    private static final Path REPOSITORY = Path.of("repo");

    private final ScannerDefinition javaScanner = new FakeScannerDefinition(ScanTarget.Language.JAVA);
    private final ScannerDefinition typescriptScanner = new FakeScannerDefinition(ScanTarget.Language.TYPESCRIPT);

    @Test
    void executesEachTargetWithTheSupportingScanner() {
        final var javaTarget = target(ScanTarget.Language.JAVA, "backend");
        final var typescriptTarget = target(ScanTarget.Language.TYPESCRIPT, "frontend");

        final var sandbox = new RecordingSandbox();

        final var executor = new DefaultScanExecutor(
            List.of(javaScanner, typescriptScanner),
            sandbox
        );

        final var report = executor.execute(
            REPOSITORY,
            new ScanPlan(List.of(javaTarget, typescriptTarget))
        );

        assertThat(sandbox.executions).containsExactly(
            new Execution(javaScanner, REPOSITORY, javaTarget),
            new Execution(typescriptScanner, REPOSITORY, typescriptTarget)
        );

        assertThat(report.results())
            .extracting(ScanResult::getTarget)
            .containsExactly(javaTarget, typescriptTarget);

        assertThat(report.hasFailures()).isFalse();
    }

    @Test
    void notifiesListenersOfEachStep() {
        final var first = target(ScanTarget.Language.JAVA, "a");
        final var second = target(ScanTarget.Language.JAVA, "b");

        final var steps = new ArrayList<ScannerListener.ScanStep>();

        final var executor = new DefaultScanExecutor(
            List.of(javaScanner),
            new RecordingSandbox(),
            List.of(new StepListener(steps, new ArrayList<>()))
        );

        executor.execute(REPOSITORY, new ScanPlan(List.of(first, second)));

        assertThat(steps).containsExactly(
            new ScannerListener.ScanStep(1, 2, first),
            new ScannerListener.ScanStep(2, 2, second)
        );
    }

    @Test
    void returnsNoResultsForAnEmptyPlan() {
        final var sandbox = new RecordingSandbox();

        final var executor = new DefaultScanExecutor(List.of(javaScanner), sandbox);

        final var report = executor.execute(REPOSITORY, new ScanPlan(List.of()));

        assertThat(report.results()).isEmpty();
        assertThat(report.failures()).isEmpty();
        assertThat(sandbox.executions).isEmpty();
    }

    @Test
    void continuesWithTheNextTargetWhenAScannerFails() {
        final var first = target(ScanTarget.Language.JAVA, "a");
        final var broken = target(ScanTarget.Language.JAVA, "broken");
        final var last = target(ScanTarget.Language.JAVA, "c");

        final var sandbox = new RecordingSandbox();
        sandbox.failFor(broken, new IllegalStateException("Scanner failed with exit code 3"));

        final var executor = new DefaultScanExecutor(List.of(javaScanner), sandbox);

        final var report = executor.execute(REPOSITORY, new ScanPlan(List.of(first, broken, last)));

        assertThat(sandbox.executions)
            .extracting(Execution::target)
            .containsExactly(first, broken, last);

        assertThat(report.results())
            .extracting(ScanResult::getTarget)
            .containsExactly(first, last);

        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.target()).isSameAs(broken);
            assertThat(failure.message()).isEqualTo("Scanner failed with exit code 3");
        });
    }

    @Test
    void reportsTargetsWithoutSupportingScannerAsFailures() {
        final var java = target(ScanTarget.Language.JAVA, "backend");
        final var typescript = target(ScanTarget.Language.TYPESCRIPT, "frontend");

        final var executor = new DefaultScanExecutor(
            List.of(javaScanner),
            new RecordingSandbox()
        );

        final var report = executor.execute(REPOSITORY, new ScanPlan(List.of(typescript, java)));

        assertThat(report.results())
            .extracting(ScanResult::getTarget)
            .containsExactly(java);

        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.target()).isSameAs(typescript);
            assertThat(failure.message()).startsWith("No scanner available for target");
        });
    }

    @Test
    void notifiesListenersOfFailures() {
        final var ok = target(ScanTarget.Language.JAVA, "a");
        final var broken = target(ScanTarget.Language.JAVA, "b");

        final var sandbox = new RecordingSandbox();
        sandbox.failFor(broken, new IllegalStateException("boom"));

        final var steps = new ArrayList<ScannerListener.ScanStep>();
        final var failures = new ArrayList<ScanFailure>();

        new DefaultScanExecutor(List.of(javaScanner), sandbox, List.of(new StepListener(steps, failures)))
            .execute(REPOSITORY, new ScanPlan(List.of(ok, broken)));

        assertThat(steps).hasSize(2);
        assertThat(failures).singleElement().satisfies(failure -> {
            assertThat(failure.target()).isSameAs(broken);
            assertThat(failure.message()).isEqualTo("boom");
        });
    }

    @Test
    void stopsTheWholeScanWhenInterrupted() {
        final var interrupted = target(ScanTarget.Language.JAVA, "a");
        final var next = target(ScanTarget.Language.JAVA, "b");

        final var sandbox = new RecordingSandbox();
        sandbox.interruptFor(interrupted);

        final var executor = new DefaultScanExecutor(List.of(javaScanner), sandbox);

        try {
            assertThatThrownBy(() -> executor.execute(REPOSITORY, new ScanPlan(List.of(interrupted, next))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Scanner execution was interrupted");

            assertThat(sandbox.executions)
                .extracting(Execution::target)
                .containsExactly(interrupted);
        } finally {
            Thread.interrupted();
        }
    }

    private static ScanTarget target(
        final ScanTarget.Language language,
        final String path
    ) {
        final var target = new ScanTarget();
        target.setLanguage(language);
        target.setPath(path);

        return target;
    }

    private record Execution(
        ScannerDefinition scanner,
        Path path,
        ScanTarget target
    ) { }

    private static class RecordingSandbox implements ScanSandbox {

        private final List<Execution> executions = new ArrayList<>();

        private final Map<ScanTarget, RuntimeException> failures = new IdentityHashMap<>();

        void failFor(final ScanTarget target, final RuntimeException error) {
            failures.put(target, error);
        }

        /**
         * Behaves like a sandbox whose scanner process is interrupted.
         */
        void interruptFor(final ScanTarget target) {
            failures.put(target, null);
        }

        @Override
        public ScanResult execute(
            final ScannerDefinition definition,
            final Path path,
            final ScanTarget target
        ) {
            executions.add(new Execution(definition, path, target));

            if (failures.containsKey(target)) {
                final var error = failures.get(target);

                if (error == null) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Scanner execution was interrupted");
                }

                throw error;
            }

            final var result = new ScanResult();
            result.setTarget(target);

            return result;
        }
    }

    private record FakeScannerDefinition(ScanTarget.Language language) implements ScannerDefinition {

        @Override
        public boolean supports(final ScanTarget target) {
            return target.getLanguage() == language;
        }

        @Override
        public String image() {
            return "image";
        }

        @Override
        public List<String> command(final String repository, final ScanTarget target) {
            return List.of();
        }

        @Override
        public String output() {
            return "/output/scan-result.json";
        }
    }

    private record StepListener(
        List<ScannerListener.ScanStep> steps,
        List<ScanFailure> failures
    ) implements ScannerListener {

        @Override
        public void onScanStarted() { }

        @Override
        public void onPlanCreated(final ScanPlan plan) { }

        @Override
        public void onStepChanged(final ScanStep step) {
            steps.add(step);
        }

        @Override
        public void onStepFailed(final ScanStep step, final ScanFailure failure) {
            failures.add(failure);
        }

        @Override
        public void onScanComplete() { }
    }
}
