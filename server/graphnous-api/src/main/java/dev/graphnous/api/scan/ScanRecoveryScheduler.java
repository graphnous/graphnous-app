package dev.graphnous.api.scan;

import dev.graphnous.application.scan.ScanRecovery;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScanRecoveryScheduler implements SmartInitializingSingleton {

    private final ScanRecovery scanRecovery;

    public ScanRecoveryScheduler(final ScanRecovery scanRecovery) {
        this.scanRecovery = scanRecovery;
    }

    /**
     * Runs once the context is set up but before the web server starts, so
     * no scan of this run can be failed as interrupted.
     */
    @Override
    public void afterSingletonsInstantiated() {
        this.scanRecovery.failInterrupted();
    }

    @Scheduled(
        fixedDelayString = "${graphnous.scans.recovery-interval}",
        initialDelayString = "${graphnous.scans.recovery-interval}"
    )
    public void failTimedOut() {
        this.scanRecovery.failTimedOut();
    }
}
