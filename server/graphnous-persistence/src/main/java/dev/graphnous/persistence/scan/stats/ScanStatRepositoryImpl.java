package dev.graphnous.persistence.scan.stats;

import dev.graphnous.application.scan.stats.ScanStatRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;
import dev.graphnous.domain.system.System;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public Optional<ScanStats> findLatestByProjectId(final Project.ProjectId projectId) {
        return this.jpaRepository.findLatestByProjectId(projectId.id(), PageRequest.of(0, 1)).stream()
            .findFirst()
            .map(ScanStatRepositoryImpl::toDomain);
    }

    @Override
    public List<ScanStats> findLatestBySystemId(final System.SystemId systemId) {
        return this.jpaRepository.findLatestBySystemId(systemId.id()).stream()
            .map(ScanStatRepositoryImpl::toDomain)
            .toList();
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
        entity.setNumberOfModulesScanned(stats.numberOfModulesScanned());
        entity.setLanguages(stats.languages());
        entity.setNumberOfClassesParsed(stats.numberOfClassesParsed());
        entity.setNumberOfMethodsParsed(stats.numberOfMethodsParsed());

        return entity;
    }

    private static ScanStats toDomain(final ScanStatEntity entity) {
        return new ScanStats(
            new ScanStats.ScanStatId(entity.getId()),
            new Scan.ScanId(entity.getScanId()),
            new Project.ProjectId(entity.getProjectId()),
            entity.getNumberOfModulesScanned(),
            entity.getLanguages(),
            entity.getNumberOfClassesParsed(),
            entity.getNumberOfMethodsParsed()
        );
    }
}
