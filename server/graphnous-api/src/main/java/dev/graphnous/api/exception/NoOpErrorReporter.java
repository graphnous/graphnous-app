package dev.graphnous.api.exception;

import dev.graphnous.application.exception.ErrorReporter;

public class NoOpErrorReporter implements ErrorReporter {
    @Override
    public void report(Throwable exception, ErrorContext context) {
        // NoOp
    }
}
