package dev.graphnous.persistence.system;

import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.system.exception.SystemNotFoundException;
import dev.graphnous.domain.system.System;
import dev.graphnous.application.system.SystemRepository;
import org.springframework.stereotype.Repository;

@Repository
public class SystemRepositoryImpl implements SystemRepository {

    private final SystemJpaRepository jpaRepository;
    private final SystemNeo4jRepository neo4jRepository;

    private final SystemMapper systemMapper;

    public SystemRepositoryImpl(
        final SystemJpaRepository jpaRepository,
        final SystemNeo4jRepository neo4jRepository
    ) {
        this.jpaRepository = jpaRepository;
        this.neo4jRepository = neo4jRepository;

        this.systemMapper = new SystemMapper();
    }

    @Override
    public System save(
        final OrganizationId organizationId,
        final System system
    ) {
        final var systemEntity = this.systemMapper.fromDomain(system);
        systemEntity.setOrganizationId(organizationId.id());

        final var entity = this.jpaRepository.save(systemEntity);

        final var node = new SystemNode();

        node.setId(entity.getId());
        node.setName(entity.getName());

        this.neo4jRepository.save(node);

        return this.systemMapper.toDomain(entity);
    }

    @Override
    public Page<System> findAll(
        final OrganizationId organizationId,
        final PageQuery pageQuery
    ) {
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

        final var result = this.jpaRepository.findAllByOrganizationId(
            organizationId.id(),
            pageable
        );

        return new Page<>(
            result
                .stream()
                .map(this.systemMapper::toDomain)
                .toList(),
            result.getNumber(),
            result.getSize(),
            Math.toIntExact(result.getTotalElements()),
            result.getTotalPages()
        );
    }

    @Override
    public int count(
        final OrganizationId organizationId
    ) {
        return this.jpaRepository.countByOrganizationId(organizationId.id());
    }

    @Override
    public System findById(
        final OrganizationId organizationId,
        final System.SystemId id
    ) {
        final var entity = this.jpaRepository.findByOrganizationIdAndId(organizationId.id(), id.id())
            .orElseThrow(() -> new SystemNotFoundException(id));

        return this.systemMapper.toDomain(entity);
    }

    @Override
    public void delete(
        final OrganizationId organizationId,
        final System.SystemId id
    ) {
        final var entity = this.jpaRepository.findByOrganizationIdAndId(organizationId.id(), id.id())
            .orElseThrow(() -> new SystemNotFoundException(id));

        this.jpaRepository.delete(entity);

        // Its projects are deleted beforehand, see ProjectDeleter
        this.neo4jRepository.deleteById(id.id());
    }
}
