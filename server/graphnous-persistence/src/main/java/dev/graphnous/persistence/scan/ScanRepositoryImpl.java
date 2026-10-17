package dev.graphnous.persistence.scan;

import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ScanRepositoryImpl implements ScanRepository {

    private static final List<Scan.ScanStatus> ACTIVE = Arrays.stream(Scan.ScanStatus.values())
        .filter(status -> !status.isEndState())
        .toList();

    private final ScanJpaRepository jpaRepository;
    private final ScanNeo4jRepository neo4jRepository;

    private final ScanMapper scanMapper;

    public ScanRepositoryImpl(
        final ScanJpaRepository scanJpaRepository,
        final ScanNeo4jRepository scanNeo4jRepository
    ) {
        this.jpaRepository = scanJpaRepository;
        this.neo4jRepository = scanNeo4jRepository;

        this.scanMapper = new ScanMapper();
    }



    @Override
    public Scan save(Scan scan) {
        final var entity = this.jpaRepository.save(
            this.scanMapper.fromDomain(scan)
        );

        this.neo4jRepository.updateStatus(
            scan.id().id(),
            scan.status()
        );

        return this.scanMapper.toDomain(entity);
    }

    @Override
    public Page<Scan> findAll(Project.ProjectId projectId, PageQuery pageQuery) {
        return toPage(this.jpaRepository.findAllByProjectId(projectId.id(), pageable(pageQuery)));
    }

    @Override
    public Page<Scan> findAllInSystem(System.SystemId systemId, PageQuery pageQuery) {
        return toPage(this.jpaRepository.findAllBySystemId(systemId.id(), pageable(pageQuery)));
    }

    @Override
    public List<Scan> findLatestCompletedInSystem(final System.SystemId systemId) {
        return this.jpaRepository.findLatestBySystemIdAndStatus(systemId.id(), Scan.ScanStatus.COMPLETED)
            .stream()
            .map(this.scanMapper::toDomain)
            .toList();
    }

    @Override
    public Optional<Scan> findLatestCompleted(final Project.ProjectId projectId, final String branch) {
        return this.jpaRepository
            .findFirstByProjectIdAndBranchAndStatusOrderByCreatedAtDesc(projectId.id(), branch, Scan.ScanStatus.COMPLETED)
            .map(this.scanMapper::toDomain);
    }

    @Override
    public List<Scan> findOfRevision(final Project.ProjectId projectId, final String revision) {
        return this.jpaRepository.findAllOfRevision(projectId.id(), revision)
            .stream()
            .map(this.scanMapper::toDomain)
            .toList();
    }

    private static org.springframework.data.domain.Pageable pageable(final PageQuery pageQuery) {
        final var sort = switch (pageQuery.sort().direction()) {
            case ASC -> org.springframework.data.domain.Sort
                .by(pageQuery.sort().property())
                .ascending();

            case DESC -> org.springframework.data.domain.Sort
                .by(pageQuery.sort().property())
                .descending();
        };

        return org.springframework.data.domain.PageRequest.of(
            pageQuery.page(),
            pageQuery.size(),
            sort
        );
    }

    private Page<Scan> toPage(final org.springframework.data.domain.Page<ScanEntity> result) {
        return new Page<>(
            result
                .stream()
                .map(this.scanMapper::toDomain)
                .toList(),
            result.getNumber(),
            result.getSize(),
            Math.toIntExact(result.getTotalElements()),
            result.getTotalPages()
        );
    }

    @Override
    public Scan findById(Scan.ScanId id) {
        final var entity = this.jpaRepository.findById(id.id())
                .orElseThrow(() -> new NotFoundException(id.id().toString()));

        return this.scanMapper.toDomain(entity);
    }

    @Override
    public List<Scan.ScanId> findIdsByProjectId(final Project.ProjectId projectId) {
        return this.jpaRepository.findIdsByProjectId(projectId.id())
            .stream()
            .map(Scan.ScanId::new)
            .toList();
    }

    @Override
    public List<Scan> findActive() {
        return this.jpaRepository.findAllByStatusIn(ACTIVE)
            .stream()
            .map(this.scanMapper::toDomain)
            .toList();
    }

    @Override
    public boolean hasActiveScans(final Project.ProjectId projectId) {
        return this.jpaRepository.existsByProjectIdAndStatusIn(projectId.id(), ACTIVE);
    }

    @Override
    public List<Scan> findFinishedCreatedBefore(final Instant cutoff) {
        return this.jpaRepository.findAllByStatusNotInAndCreatedAtBefore(ACTIVE, cutoff)
            .stream()
            .map(this.scanMapper::toDomain)
            .toList();
    }

    @Override
    public List<Scan.ScanId> findOldestFinished(final Project.ProjectId projectId, final int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return this.jpaRepository
            .findIdsByProjectIdAndStatusNotInOldestFirst(
                projectId.id(),
                ACTIVE,
                org.springframework.data.domain.PageRequest.of(0, limit)
            )
            .stream()
            .map(Scan.ScanId::new)
            .toList();
    }

    @Override
    public void delete(final Scan.ScanId scanId) {
        // Graph first: the scan row is how a failed delete is found again
        this.neo4jRepository.deleteScan(scanId.id());

        this.jpaRepository.deleteById(scanId.id());
    }

    @Override
    public int count(Project.ProjectId projectId) {
        return this.jpaRepository.countByProjectId(projectId.id());
    }

    @Override
    public int countInSystem(final System.SystemId systemId) {
        return this.jpaRepository.countBySystemId(systemId.id());
    }

    @Override
    public void startScan(final UUID snapshotId, Scan.ScanId scanId) {
        final var scan = this.findById(scanId);

        this.neo4jRepository.createScan(
            snapshotId,
            scan.id().id(),
            scan.revision().branch(),
            scan.revision().revision(),
            scan.revision().requestedRevision(),
            scan.status(),
            Instant.now()
        );
    }

    @Override
    public void updateRevision(final Scan.ScanId scanId, final String revision) {
        if (this.jpaRepository.updateRevision(scanId.id(), revision) == 0) {
            throw new NotFoundException("Scan " + scanId.id() + " not found");
        }

        this.neo4jRepository.updateRevision(scanId.id(), revision);
    }
}
