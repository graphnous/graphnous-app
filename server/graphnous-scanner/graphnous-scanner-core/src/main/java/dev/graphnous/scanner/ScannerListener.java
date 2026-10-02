package dev.graphnous.scanner;

import dev.graphnous.scanner.model.ScanTarget;
import dev.graphnous.scanner.plan.ScanPlan;

public interface ScannerListener {

    void onScanStarted();

    void onPlanCreated(ScanPlan plan);

    void onStepChanged(ScanStep step);

    /**
     * The scanner for this step failed; the remaining steps still run.
     */
    void onStepFailed(ScanStep step, ScanFailure failure);

    void onScanComplete();

    record ScanStep(
        int index,
        int total,
        ScanTarget target
    ) { }
}
