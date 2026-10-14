package dev.graphnous.persistence.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * The patterns of the files the project leaves out of its scans, as
     * JSON; not part of the entity, so saving a project leaves them alone.
     */
    @Query(value = "SELECT scan_excludes FROM projects WHERE id = :id", nativeQuery = true)
    String findScanExcludes(
        @Param("id") final UUID id
    );

    @Modifying
    @Transactional
    @Query(value = "UPDATE projects SET scan_excludes = :excludes WHERE id = :id", nativeQuery = true)
    int updateScanExcludes(
        @Param("id") final UUID id,
        @Param("excludes") final String excludes
    );

    /**
     * The languages the project scans, as JSON; not part of the entity,
     * so saving a project leaves them alone.
     */
    @Query(value = "SELECT scan_languages FROM projects WHERE id = :id", nativeQuery = true)
    String findScanLanguages(
        @Param("id") final UUID id
    );

    @Modifying
    @Transactional
    @Query(value = "UPDATE projects SET scan_languages = :languages WHERE id = :id", nativeQuery = true)
    int updateScanLanguages(
        @Param("id") final UUID id,
        @Param("languages") final String languages
    );

    @Query("SELECT p.id FROM ProjectEntity p WHERE p.systemId = :systemId")
    List<UUID> findIdsBySystemId(
        @Param("systemId") final UUID systemId
    );
}
