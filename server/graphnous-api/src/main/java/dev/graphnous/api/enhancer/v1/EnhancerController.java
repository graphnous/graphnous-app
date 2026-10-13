package dev.graphnous.api.enhancer.v1;

import dev.graphnous.enhancer.EnhancerRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The enhancers installed on this server.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/enhancers")
public class EnhancerController {

    private final EnhancerRegistry enhancerRegistry;

    public EnhancerController(final EnhancerRegistry enhancerRegistry) {
        this.enhancerRegistry = enhancerRegistry;
    }

    /**
     * @param name    such as spring; unique among the installed enhancers
     * @param version such as 1.0.0
     */
    public record EnhancerResponse(
        String name,
        String version
    ) {
    }

    /**
     * Every installed enhancer, in the order they run.
     */
    @GetMapping
    public ResponseEntity<List<EnhancerResponse>> getEnhancers() {
        return ResponseEntity.ok(
            this.enhancerRegistry.enhancers().stream()
                .map(enhancer -> new EnhancerResponse(enhancer.name(), enhancer.version()))
                .toList()
        );
    }

}
