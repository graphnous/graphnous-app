package dev.graphnous.persistence.scan.stats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(
    name = "scan_stats",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_scan_stats_scan",
            columnNames = "scan_id"
        )
    }
)
public class ScanStatEntity {

    @Id
    private UUID id;

    @Column(name = "scan_id", nullable = false, updatable = false)
    private UUID scanId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "number_of_files_scanned", nullable = false)
    private int numberOfFilesScanned;

    @Column(name = "number_of_classes_parsed", nullable = false)
    private int numberOfClassesParsed;

    @Column(name = "number_of_methods_parsed", nullable = false)
    private int numberOfMethodsParsed;

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

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(final UUID projectId) {
        this.projectId = projectId;
    }

    public int getNumberOfFilesScanned() {
        return numberOfFilesScanned;
    }

    public void setNumberOfFilesScanned(final int numberOfFilesScanned) {
        this.numberOfFilesScanned = numberOfFilesScanned;
    }

    public int getNumberOfClassesParsed() {
        return numberOfClassesParsed;
    }

    public void setNumberOfClassesParsed(final int numberOfClassesParsed) {
        this.numberOfClassesParsed = numberOfClassesParsed;
    }

    public int getNumberOfMethodsParsed() {
        return numberOfMethodsParsed;
    }

    public void setNumberOfMethodsParsed(final int numberOfMethodsParsed) {
        this.numberOfMethodsParsed = numberOfMethodsParsed;
    }
}
