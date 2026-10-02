package dev.graphnous.persistence.project;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.UUID;

@Node("Projects")
public class ProjectNode {

    @Id
    private UUID id;

    private String name;

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return this.id;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

}
