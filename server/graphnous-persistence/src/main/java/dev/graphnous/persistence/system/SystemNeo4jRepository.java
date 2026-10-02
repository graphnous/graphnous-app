package dev.graphnous.persistence.system;

import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.UUID;

public interface SystemNeo4jRepository extends Neo4jRepository<SystemNode, UUID> {
}
