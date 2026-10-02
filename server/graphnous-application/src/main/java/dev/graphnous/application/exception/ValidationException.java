package dev.graphnous.application.exception;

/**
 * A request whose content is invalid, with a message that says what to fix.
 */
public class ValidationException extends GraphnousException {

    public ValidationException(final String message) {
        super(message);
    }
}
