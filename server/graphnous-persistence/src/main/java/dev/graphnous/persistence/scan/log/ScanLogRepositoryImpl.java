package dev.graphnous.persistence.scan.log;

import dev.graphnous.application.scan.log.ScanLogRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

@Repository
public class ScanLogRepositoryImpl implements ScanLogRepository {

    private final ScanLogJpaRepository jpaRepository;

    public ScanLogRepositoryImpl(
        final ScanLogJpaRepository jpaRepository
    ) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ScanLog save(ScanLog log) {
        return this.toDomain(
            this.jpaRepository.save(this.toEntity(log))
        );
    }

    @Override
    public List<ScanLog> findByScanId(
            final Scan.ScanId scanId
    ) {
        return this.jpaRepository
            .findByScanIdOrderBySequenceAsc(scanId.id())
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<ScanLog> findByScanIdAfter(
            final Scan.ScanId scanId,
            final long sequence
    ) {
        return this.jpaRepository
            .findByScanIdAndSequenceGreaterThanOrderBySequenceAsc(
                scanId.id(),
                sequence
            )
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public OptionalLong findLatestSequence(
        final Scan.ScanId scanId
    ) {
        return this.jpaRepository
                .findLatestSequence(scanId.id())
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    @Override
    public void deleteByScanId(final Scan.ScanId scanId) {
        this.jpaRepository.deleteByScanId(scanId.id());
    }

    private ScanLog toDomain(final ScanLogEntity entity) {
        return new ScanLog(
            new ScanLog.ScanLogId(entity.getId()),
            new Scan.ScanId(entity.getScanId()),
            entity. getSequence(),
            entity.getTimestamp(),
            entity.getLevel(),
            entity.getMessage()
        );
    }

    private ScanLogEntity toEntity(final ScanLog domain) {
        final var entity = new ScanLogEntity();

        entity.setId(domain.id().id());
        entity.setScanId(domain.scanId().id());

        entity.setSequence(domain.sequence());
        entity.setLevel(domain.level());
        entity.setTimestamp(domain.timestamp());

        entity.setMessage(domain.message());

        return entity;
    }
}
