package dev.graphnous.application.exception;

public class GraphnousException extends RuntimeException {

    public GraphnousException(final String message) {
        this(message, null);
    }

    public GraphnousException(
        final String message,
        final Throwable cause
    ) {
        super(message, cause);
    }
}