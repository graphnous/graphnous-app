package dev.graphnous.persistence.project;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProjectNeo4jRepository extends Neo4jRepository<ProjectNode, UUID> {

    @Query("""
        MERGE (p:Project {id: $id})
        SET p.name = $name
        WITH p
        MATCH (s:System {id: $systemId})
        MERGE (s)-[:HAS_PROJECT]->(p)
    """)
    void saveProject(
        @Param("id") UUID id,
        @Param("name") String name,
        @Param("systemId") UUID systemId
    );

    /**
     * Deletes the project and its snapshots, except those another project
     * shares: snapshots are identified by git url and path only. The scans
     * are deleted beforehand, as they are found through the database.
     */
    @Query("""
    MATCH (p:Project {id: $projectId})
    OPTIONAL MATCH (p)-[:HAS_SNAPSHOT]->(snapshot:ProjectSnapshot)
    WHERE NOT EXISTS {
        MATCH (other:Project)-[:HAS_SNAPSHOT]->(snapshot)
        WHERE other <> p
    }
    DETACH DELETE snapshot, p
    """)
    void deleteProject(
        @Param("projectId") UUID projectId
    );

    @Query("""
    MATCH (p:Project {id: $projectId})
    MERGE (snapshot:ProjectSnapshot {
        gitUrl: $gitUrl,
        path: $path
    })
    ON CREATE SET
        snapshot.id = $snapshotId,
        snapshot.createdAt = $createdAt
    MERGE (p)-[:HAS_SNAPSHOT]->(snapshot)
    RETURN snapshot.id
    """)
    UUID getOrCreateSnapshot(
            @Param("projectId") UUID projectId,
            @Param("gitUrl") String gitUrl,
            @Param("path") String path,
            @Param("snapshotId") UUID snapshotId,
            @Param("createdAt") Instant createdAt
    );

}
