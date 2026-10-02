package dev.graphnous.application.authorization;

import dev.graphnous.application.context.RequestContext;

public interface AuthorizationService {

    void authorize(
        RequestContext context,
        Permission permission
    );
}
