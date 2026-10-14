package dev.graphnous.persistence.scan.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.scan.result.ScanResultArchive;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.scan.Scan;
import org.springframework.stereotype.Repository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Keeps the results of scans in PostgreSQL, each as gzipped JSON in a row
 * of its own: results are large, and compress well.
 */
@Repository
public class ScanResultArchiveImpl implements ScanResultArchive {

    /**
     * Jackson 2, as the scan result model uses.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScanResultArchiveJpaRepository jpaRepository;

    public ScanResultArchiveImpl(final ScanResultArchiveJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(
        final Scan.ScanId scanId,
        final List<ScanResult> results
    ) {
        final var entities = new ArrayList<ScanResultArchiveEntity>();

        for (int position = 0; position < results.size(); position++) {
            final var entity = new ScanResultArchiveEntity();

            entity.setId(UUID.randomUUID());
            entity.setScanId(scanId.id());
            entity.setPosition(position);
            entity.setContent(compress(results.get(position)));

            entities.add(entity);
        }

        this.jpaRepository.deleteByScanId(scanId.id());
        this.jpaRepository.saveAll(entities);
    }

    @Override
    public boolean exists(final Scan.ScanId scanId) {
        return this.jpaRepository.existsByScanId(scanId.id());
    }

    @Override
    public List<ScanResult> load(final Scan.ScanId scanId) {
        return this.jpaRepository.findByScanIdOrderByPositionAsc(scanId.id())
            .stream()
            .map(entity -> decompress(entity.getContent()))
            .toList();
    }

    @Override
    public void delete(final Scan.ScanId scanId) {
        this.jpaRepository.deleteByScanId(scanId.id());
    }

    private static byte[] compress(final ScanResult result) {
        final var bytes = new ByteArrayOutputStream();

        try (final var gzip = new GZIPOutputStream(bytes)) {
            JSON.writeValue(gzip, result);
        } catch (final IOException e) {
            throw new UncheckedIOException("Writing a scan result failed", e);
        }

        return bytes.toByteArray();
    }

    private static ScanResult decompress(final byte[] content) {
        try (final var gzip = new GZIPInputStream(new ByteArrayInputStream(content))) {
            return JSON.readValue(gzip, ScanResult.class);
        } catch (final IOException e) {
            throw new UncheckedIOException("Reading a kept scan result failed", e);
        }
    }
}
