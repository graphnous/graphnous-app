package dev.graphnous.persistence.scan.stats;

import dev.graphnous.application.scan.stats.ScanStatRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ScanStatRepositoryImpl implements ScanStatRepository {

    private final ScanStatJpaRepository jpaRepository;

    public ScanStatRepositoryImpl(final ScanStatJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ScanStats save(final ScanStats stats) {
        return toDomain(this.jpaRepository.save(toEntity(stats)));
    }

    @Override
    public Optional<ScanStats> findByScanId(final Scan.ScanId scanId) {
        return this.jpaRepository.findByScanId(scanId.id()).map(ScanStatRepositoryImpl::toDomain);
    }

    @Override
    public void deleteByScanId(final Scan.ScanId scanId) {
        this.jpaRepository.deleteByScanId(scanId.id());
    }

    private static ScanStatEntity toEntity(final ScanStats stats) {
        final var entity = new ScanStatEntity();

        entity.setId(stats.id().id());
        entity.setScanId(stats.scanId().id());
        entity.setProjectId(stats.projectId().id());
        entity.setNumberOfFilesScanned(stats.numberOfFilesScanned());
        entity.setNumberOfClassesParsed(stats.numberOfClassesParsed());
        entity.setNumberOfMethodsParsed(stats.numberOfMethodsParsed());

        return entity;
    }

    private static ScanStats toDomain(final ScanStatEntity entity) {
        return new ScanStats(
            new ScanStats.ScanStatId(entity.getId()),
            new Scan.ScanId(entity.getScanId()),
            new Project.ProjectId(entity.getProjectId()),
            entity.getNumberOfFilesScanned(),
            entity.getNumberOfClassesParsed(),
            entity.getNumberOfMethodsParsed()
        );
    }
}
