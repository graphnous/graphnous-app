package dev.graphnous.application.scan.log;

import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public interface ScanLogRepository {

    ScanLog save(ScanLog log);

    List<ScanLog> findByScanId(Scan.ScanId scanId);

    List<ScanLog> findByScanIdAfter(
        Scan.ScanId scanId,
        long sequence
    );

    OptionalLong findLatestSequence(
        Scan.ScanId scanId
    );

    void deleteByScanId(Scan.ScanId scanId);

}
