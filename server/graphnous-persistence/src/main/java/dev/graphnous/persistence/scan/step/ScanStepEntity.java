package dev.graphnous.persistence.scan.step;

import dev.graphnous.domain.scan.ScanStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "scan_steps",
    indexes = {
        @Index(
            name = "idx_scan_steps_scan_position",
            columnList = "scan_id, position"
        )
    }
)
public class ScanStepEntity {

    @Id
    private UUID id;

    @Column(name = "scan_id", nullable = false)
    private UUID scanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStep.ScanStepType type;

    /**
     * Where the step comes in the scan's execution, to read them in order.
     */
    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStep.ScanStepStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(columnDefinition = "TEXT")
    private String error;

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

    public ScanStep.ScanStepType getType() {
        return type;
    }

    public void setType(final ScanStep.ScanStepType type) {
        this.type = type;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public ScanStep.ScanStepStatus getStatus() {
        return status;
    }

    public void setStatus(final ScanStep.ScanStepStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(final Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
    }
}
