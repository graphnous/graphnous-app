package dev.graphnous.scanner.definition;

/**
 * A file put into a scanner's container before it starts, such as the
 * settings with the credentials of the repositories the scanner downloads
 * from. Unlike a mount, it is copied, so it does not have to exist on the
 * Docker host, and goes with the container.
 *
 * @param target  the absolute path of the file in the container, in a
 *                directory such as {@code /maven-settings}
 * @param content the content of the file
 */
public record ScannerFile(
    String target,
    byte[] content
) {

    public ScannerFile {
        // In a directory of its own, which is created for it
        if (target == null || !target.startsWith("/") || target.endsWith("/") || target.lastIndexOf('/') == 0) {
            throw new IllegalArgumentException(
                "A scanner file's target must be an absolute file path in a directory, got " + target
            );
        }

        if (content == null) {
            throw new IllegalArgumentException("The scanner file " + target + " has no content");
        }

        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public String toString() {
        // Without the content, which may be secret
        return "ScannerFile[" + target + "]";
    }
}
