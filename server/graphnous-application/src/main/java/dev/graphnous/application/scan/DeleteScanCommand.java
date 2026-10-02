package dev.graphnous.application.scan;

import dev.graphnous.domain.scan.Scan;

public record DeleteScanCommand(Scan.ScanId scanId)
{ }
