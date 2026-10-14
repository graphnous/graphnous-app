package dev.graphnous.persistence.scan.archive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * One result of a scan as its scanner produced it: the result of one
 * target, as gzipped JSON.
 */
@Entity
@Table(
    name = "scan_result_archives",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_scan_result_archives_scan_position",
            columnNames = {"scan_id", "position"}
        )
    }
)
public class ScanResultArchiveEntity {

    @Id
    private UUID id;

    @Column(name = "scan_id", nullable = false, updatable = false)
    private UUID scanId;

    /**
     * Where the result is among the scan's, from 0.
     */
    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Column(name = "content", nullable = false, updatable = false)
    private byte[] content;

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getScanId() {
        return scanId;
    }

    public void setScanId(final UUID scanId) {
        this.scanId = scanId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(final byte[] content) {
        this.content = content;
    }
}
