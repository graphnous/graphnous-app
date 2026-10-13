package dev.graphnous.scanner.definition;

/**
 * A volume or directory a scanner's container mounts besides the
 * repository, such as a cache the scanner keeps between scans.
 *
 * @param type     VOLUME: the named Docker volume {@code source}, created on
 *                 first use. BIND: the directory or file {@code source} on
 *                 the Docker host.
 * @param source   the volume name, or the absolute path on the Docker host
 * @param target   the absolute path in the container
 * @param readOnly whether the scanner may only read it
 */
public record ScannerMount(
    Type type,
    String source,
    String target,
    boolean readOnly
) {

    public enum Type {
        VOLUME,
        BIND
    }

    public ScannerMount {
        if (type == null) {
            throw new IllegalArgumentException("A scanner mount needs a type, VOLUME or BIND");
        }

        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("A scanner mount needs a source");
        }

        if (type == Type.BIND && !source.startsWith("/")) {
            throw new IllegalArgumentException("A bind mount's source must be an absolute path on the Docker host, got " + source);
        }

        if (target == null || !target.startsWith("/")) {
            throw new IllegalArgumentException("A scanner mount's target must be an absolute path, got " + target);
        }
    }
}
