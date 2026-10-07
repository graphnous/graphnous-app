package dev.graphnous.scanner.targetdetector;

import dev.graphnous.core.model.ScanTarget;

import java.nio.file.Path;
import java.util.List;

public interface ScanTargetDetector {

    List<ScanTarget> detect(Path repository);

}
