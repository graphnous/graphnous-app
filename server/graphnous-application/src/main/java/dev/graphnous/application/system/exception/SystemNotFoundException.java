package dev.graphnous.application.system.exception;

import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.domain.system.System;

public class SystemNotFoundException extends NotFoundException {

    public SystemNotFoundException(System.SystemId id) {
        super("System with id %s not found".formatted(id.id()));
    }

}
