package dev.graphnous.scanner.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.scanner.definition.ScannerDefinition;
import dev.graphnous.scanner.listener.ScanProcessListener;
import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileSystemScanSandbox implements ScanSandbox {

    private final ObjectMapper objectMapper;
    private final ScanProcessListener scanProcessListener;

    public FileSystemScanSandbox(
        final ObjectMapper objectMapper,
        final ScanProcessListener scanProcessListener
    ) {
        this.objectMapper = objectMapper;

        this.scanProcessListener = scanProcessListener;
    }

    @Override
    public ScanResultSchema execute(
        final ScannerDefinition scanner,
        final Path path,
        final ScanTarget target
    ) {
        final var output = createOutputFile();

        try {
            final var command = scanner.command(path, target);

            command.add("--output");
            command.add(output.toString());

            final var process = new ProcessBuilder(command)
                .start();

            final var stdoutThread = Thread.ofVirtual().start(() ->
                process.inputReader()
                    .lines()
                    .forEach(scanProcessListener::stdout)
            );

            final var stderrThread = Thread.ofVirtual().start(() ->
                process.errorReader()
                    .lines()
                    .forEach(scanProcessListener::stderr)
            );

            final var exitCode = process.waitFor();

            stdoutThread.join();
            stderrThread.join();


            if (exitCode != 0) {
                throw new IllegalStateException(
                    "Scanner failed with exit code " + exitCode
                );
            }

            return objectMapper.readValue(
                output.toFile(),
                ScanResultSchema.class
            );

        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to execute scanner",
                e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                "Scanner execution was interrupted",
                e
            );
        } finally {
            delete(output);
        }
    }

    private Path createOutputFile() {
        try {
            return Files.createTempFile(
                "graphnous-scan-",
                ".json"
            );
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to create scan output file",
                e
            );
        }
    }

    private void delete(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // A leftover temp file does not invalidate the scan, so report
            // it instead of failing.
            scanProcessListener.stderr(
                "Failed to delete scan output file " + path + ": " + e
            );
        }
    }
}
