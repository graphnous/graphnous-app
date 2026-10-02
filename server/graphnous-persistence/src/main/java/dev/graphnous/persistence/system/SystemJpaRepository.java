package dev.graphnous.persistence.system;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface SystemJpaRepository extends JpaRepository<SystemEntity, UUID> {

    Page<SystemEntity> findAllByOrganizationId(
        final UUID organizationId,
        final Pageable pageable
    );

    Optional<SystemEntity> findByOrganizationIdAndId(
        final UUID organizationId,
        final UUID id
    );

    int countByOrganizationId(
        final UUID organizationId
    );
}
