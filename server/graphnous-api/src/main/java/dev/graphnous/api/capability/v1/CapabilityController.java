package dev.graphnous.api.capability.v1;

import dev.graphnous.api.v1.generated.capability.CapabilitiesResponse;
import dev.graphnous.application.capability.CapabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/capabilities")
public class CapabilityController {

    private final CapabilityService capabilityService;

    private final CapabilityMapper capabilityMapper;

    public CapabilityController(
        final CapabilityService capabilityService
    ) {
        this.capabilityService = capabilityService;

        this.capabilityMapper = new CapabilityMapper();
    }

    @GetMapping
    public ResponseEntity<CapabilitiesResponse> getCapabilities() {
        return ResponseEntity.ok(
            this.capabilityMapper.toResponse(this.capabilityService.getCapabilities())
        );
    }

}
