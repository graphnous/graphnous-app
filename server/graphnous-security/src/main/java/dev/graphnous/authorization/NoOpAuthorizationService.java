package dev.graphnous.authorization;

import dev.graphnous.application.authorization.AuthorizationService;
import dev.graphnous.application.authorization.Permission;
import dev.graphnous.application.context.RequestContext;

public class NoOpAuthorizationService implements AuthorizationService {

    @Override
    public void authorize(RequestContext context, Permission permission) {
        // NoOp
    }

}
