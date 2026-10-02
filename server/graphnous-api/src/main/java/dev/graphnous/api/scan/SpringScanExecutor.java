package dev.graphnous.api.scan;

import dev.graphnous.application.project.scanner.ScanExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
public class SpringScanExecutor implements ScanExecutor {

    private final Executor executor;

    /**
     * Spring Boot's task executor; named, as scheduling adds a second
     * executor, which scans must not tie up.
     */
    public SpringScanExecutor(
        @Qualifier("applicationTaskExecutor") final Executor executor
    ) {
        this.executor = executor;
    }

    @Override
    public void execute(final Runnable task) {
        executor.execute(task);
    }

}
