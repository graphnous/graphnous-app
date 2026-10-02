package dev.graphnous.persistence.scan.log;

import dev.graphnous.domain.scan.log.ScanLog;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "scan_logs",
    indexes = {
        @Index(
            name = "idx_scan_logs_scan_sequence",
            columnList = "scan_id, sequence"
        )
    }
)
public class ScanLogEntity {

    @Id
    private UUID id;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanLog.ScanLogLevel level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return this.id;
    }

    public void setScanId(final UUID scanId) {
        this.scanId = scanId;
    }

    public UUID getScanId() {
        return this.scanId;
    }

    public void setSequence(final long sequence) {
        this.sequence = sequence;
    }

    public long getSequence() {
        return this.sequence;
    }

    public void setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Instant getTimestamp() {
        return this.timestamp;
    }

    public void setLevel(final ScanLog.ScanLogLevel level) {
        this.level = level;
    }

    public ScanLog.ScanLogLevel getLevel() {
        return this.level;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public String getMessage() {
        return this.message;
    }
}
