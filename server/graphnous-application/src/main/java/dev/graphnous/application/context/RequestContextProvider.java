package dev.graphnous.application.context;

/**
 * Who the current request is for: the user, and the organization they
 * work in. With security on, from the request's access token; without it,
 * one fixed organization for everyone.
 */
public interface RequestContextProvider {

    RequestContext get();
}
