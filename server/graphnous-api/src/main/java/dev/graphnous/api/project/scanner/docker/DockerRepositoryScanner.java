package dev.graphnous.api.project.scanner.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import dev.graphnous.application.project.scanner.RepositoryScanner;
import dev.graphnous.application.project.scanner.ScanLogger;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog.ScanLogLevel;
import dev.graphnous.scanner.ScanFailure;
import dev.graphnous.scanner.ScanReport;
import dev.graphnous.scanner.ScannerListener;
import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.docker.DockerSandbox;
import dev.graphnous.scanner.docker.DockerWorkspace;
import dev.graphnous.scanner.executor.DefaultScanExecutor;
import dev.graphnous.scanner.listener.ScanProcessListener;
import dev.graphnous.scanner.model.ScanTarget;
import dev.graphnous.scanner.plan.ScanPlan;
import dev.graphnous.scanner.plan.ScanPlanner;
import dev.graphnous.scanner.sandbox.ScanSandbox;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs each scanner in a container that mounts the workspace the
 * repository was checked out in. The scanner's progress and output go to
 * the log of the scan.
 */
public class DockerRepositoryScanner implements RepositoryScanner {

    private final ScanPlanner planner;
    private final List<ScannerDefinition> scanners;
    private final DockerClient docker;
    private final DockerWorkspace workspace;
    private final ObjectMapper objectMapper;
    private final DockerScanContainers containers;

    public DockerRepositoryScanner(
        final ScanPlanner planner,
        final List<ScannerDefinition> scanners,
        final DockerClient docker,
        final DockerWorkspace workspace,
        final ObjectMapper objectMapper,
        final DockerScanContainers containers
    ) {
        this.planner = planner;
        this.scanners = scanners;
        this.docker = docker;
        this.workspace = workspace;
        this.objectMapper = objectMapper;
        this.containers = containers;
    }

    @Override
    public ScanPlan plan(
        final Path path,
        final ScanLogger logger
    ) {
        final var plan = planner.plan(path);

        new LoggingListener(logger).onPlanCreated(plan);

        return plan;
    }

    @Override
    public ScanReport scan(
        final Scan.ScanId scanId,
        final Path path,
        final ScanPlan plan,
        final ScanLogger logger
    ) {
        final var listener = new LoggingListener(logger);

        final var dockerSandbox = new DockerSandbox(
            docker,
            workspace,
            objectMapper,
            listener,
            containers.labels(scanId)
        );

        // A cancelled scan starts no containers for its remaining targets
        final ScanSandbox sandbox = (definition, repository, target) -> {
            if (containers.isCancelled(scanId)) {
                throw new IllegalStateException("Scan was cancelled");
            }

            return dockerSandbox.execute(definition, repository, target);
        };

        return new DefaultScanExecutor(scanners, sandbox, List.of(listener)).execute(path, plan);
    }

    private static String describe(final ScanTarget target) {
        final var path = target.getPath() == null || target.getPath().isEmpty()
            ? "."
            : target.getPath();

        return target.getLanguage() + " " + path;
    }

    private record LoggingListener(ScanLogger logger) implements ScannerListener, ScanProcessListener {

        @Override
        public void onScanStarted() {
        }

        @Override
        public void onPlanCreated(final ScanPlan plan) {
            logger.log(
                ScanLogLevel.INFO,
                "Found " + plan.targets().size()
                    + (plan.targets().size() == 1 ? " target" : " targets")
            );

            plan.warnings().forEach(warning ->
                logger.log(ScanLogLevel.WARN, warning)
            );
        }

        @Override
        public void onStepChanged(final ScanStep step) {
            logger.log(
                ScanLogLevel.INFO,
                "[" + step.index() + "/" + step.total() + "] Scanning " + describe(step.target())
            );
        }

        @Override
        public void onStepFailed(
            final ScanStep step,
            final ScanFailure failure
        ) {
            logger.log(
                ScanLogLevel.ERROR,
                "Failed to scan " + describe(step.target()) + ": " + failure.message()
            );
        }

        @Override
        public void onScanComplete() {
        }

        @Override
        public void stdout(final String line) {
            logger.log(ScanLogLevel.INFO, line);
        }

        @Override
        public void stderr(final String line) {
            logger.log(ScanLogLevel.WARN, line);
        }
    }
}
