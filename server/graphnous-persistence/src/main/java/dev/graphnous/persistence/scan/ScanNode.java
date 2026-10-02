package dev.graphnous.persistence.scan;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.UUID;

@Node("Nodes")
public class ScanNode {

    @Id
    private UUID id;

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return this.id;
    }
}
