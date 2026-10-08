package dev.graphnous.persistence.scan;

import dev.graphnous.domain.scan.Scan;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ScanNeo4jRepository extends Neo4jRepository<dev.graphnous.persistence.scan.ScanNode, UUID> {

    @Query("""
    MATCH (snapshot:ProjectSnapshot {id: $snapshotId})
    MERGE (scan:Scan {id: $scanId})
    SET
        scan.branch = $branch,
        scan.revision = $revision,
        scan.requestedRevision = $requestedRevision,
        scan.status = $status,
        scan.createdAt = $createdAt
    MERGE (snapshot)-[:HAS_SCAN]->(scan)
    """)
    void createScan(
        @Param("snapshotId") UUID snapshotId,
        @Param("scanId") UUID scanId,
        @Param("branch") String branch,
        @Param("revision") String revision,
        @Param("requestedRevision") String requestedRevision,
        @Param("status") Scan.ScanStatus status,
        @Param("createdAt") Instant createdAt
    );

    @Query("""
    MATCH (scan:Scan {id: $scanId})
    DETACH DELETE scan
    """)
    void deleteScan(
        @Param("scanId") UUID scanId
    );

    @Query("""
    MATCH (scan:Scan {id: $scanId})
    SET scan.revision = $revision
    """)
    void updateRevision(
        @Param("scanId") UUID scanId,
        @Param("revision") String revision
    );

    @Query("""
    MATCH (scan:Scan {id: $scanId})
    SET scan.status = $status
    """)
    void updateStatus(
        @Param("scanId") UUID scanId,
        @Param("status") Scan.ScanStatus status
    );
}
