package dev.graphnous.application.enhancer;

import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;

import java.util.List;

/**
 * The enhancers installed on this server.
 */
public interface EnhancerRegistry {

    /**
     * The installed enhancer manifests, in the order they run where their
     * dependencies allow it.
     */
    List<EnhancerManifestSchema> installed();

}
