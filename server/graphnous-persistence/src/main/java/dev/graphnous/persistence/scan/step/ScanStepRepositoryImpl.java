package dev.graphnous.persistence.scan.step;

import dev.graphnous.application.scan.ScanStepRepository;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.ScanStep;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ScanStepRepositoryImpl implements ScanStepRepository {

    private final ScanStepJpaRepository jpaRepository;

    public ScanStepRepositoryImpl(final ScanStepJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void saveAll(final List<ScanStep> steps) {
        this.jpaRepository.saveAll(steps.stream().map(ScanStepRepositoryImpl::toEntity).toList());
    }

    @Override
    public ScanStep save(final ScanStep step) {
        return toDomain(this.jpaRepository.save(toEntity(step)));
    }

    @Override
    public List<ScanStep> findByScanId(final Scan.ScanId scanId) {
        return this.jpaRepository.findByScanIdOrderByPositionAsc(scanId.id())
            .stream()
            .map(ScanStepRepositoryImpl::toDomain)
            .toList();
    }

    @Override
    public void deleteByScanId(final Scan.ScanId scanId) {
        this.jpaRepository.deleteByScanId(scanId.id());
    }

    private static ScanStepEntity toEntity(final ScanStep step) {
        final var entity = new ScanStepEntity();

        entity.setId(step.id().id());
        entity.setScanId(step.scanId().id());
        entity.setType(step.type());
        entity.setPosition(step.type().ordinal());
        entity.setStatus(step.status());
        entity.setStartedAt(step.startedAt());
        entity.setFinishedAt(step.finishedAt());
        entity.setError(step.error());

        return entity;
    }

    private static ScanStep toDomain(final ScanStepEntity entity) {
        return new ScanStep(
            new ScanStep.ScanStepId(entity.getId()),
            new Scan.ScanId(entity.getScanId()),
            entity.getType(),
            entity.getStatus(),
            entity.getStartedAt(),
            entity.getFinishedAt(),
            entity.getError()
        );
    }
}
