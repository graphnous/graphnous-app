package dev.graphnous.persistence.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ChatThreadJpaRepository extends JpaRepository<ChatThreadEntity, UUID> {

    Page<ChatThreadEntity> findAllBySystemIdAndUserId(
        final UUID systemId,
        final UUID userId,
        final Pageable pageable
    );

    /**
     * The threads of the single user of a server without security.
     */
    Page<ChatThreadEntity> findAllBySystemIdAndUserIdIsNull(
        final UUID systemId,
        final Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM ChatThreadEntity t WHERE t.systemId = :systemId")
    void deleteBySystemId(
        @Param("systemId") final UUID systemId
    );
}
