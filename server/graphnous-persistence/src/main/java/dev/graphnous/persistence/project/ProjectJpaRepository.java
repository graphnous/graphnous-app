package dev.graphnous.persistence.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, UUID> {

    Page<ProjectEntity> findAllBySystemId(
        final UUID systemId,
        final Pageable pageable
    );

    Optional<ProjectEntity> findBySystemIdAndId(
        final UUID systemId,
        final UUID id
    );

    int countBySystemId(
        final UUID systemId
    );

    @Query("SELECT p.id FROM ProjectEntity p WHERE p.systemId = :systemId")
    List<UUID> findIdsBySystemId(
        @Param("systemId") final UUID systemId
    );
}
