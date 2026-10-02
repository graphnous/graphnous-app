package dev.graphnous.application.project.scanner;

import dev.graphnous.domain.scan.Scan;

public record StartScanCommand(Scan.ScanId scanId) {
}
