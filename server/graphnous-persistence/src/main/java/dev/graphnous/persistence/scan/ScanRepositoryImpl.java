package dev.graphnous.persistence.scan;

import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.scan.ScanRepository;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
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
        final var sort = switch (pageQuery.sort().direction()) {
            case ASC -> org.springframework.data.domain.Sort
                .by(pageQuery.sort().property())
                .ascending();

            case DESC -> org.springframework.data.domain.Sort
                .by(pageQuery.sort().property())
                .descending();
        };


        final var pageable = org.springframework.data.domain.PageRequest.of(
            pageQuery.page(),
            pageQuery.size(),
            sort
        );

        final var result = this.jpaRepository.findAllByProjectId(
            projectId.id(),
            pageable
        );

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
    public void startScan(final UUID snapshotId, Scan.ScanId scanId) {
        final var scan = this.findById(scanId);

        this.neo4jRepository.createScan(
            snapshotId,
            scan.id().id(),
            scan.revision().branch(),
            scan.revision().revision(),
            scan.status(),
            Instant.now()
        );
    }
}
