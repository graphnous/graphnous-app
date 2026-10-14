package dev.graphnous.api.scanner.v1;

import dev.graphnous.application.scan.ScanLanguages;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The scanners this server runs, one per language.
 * <p>
 * Not in the published OpenAPI spec yet, so its models are its own.
 */
@RestController
@RequestMapping("/api/v1/scanners")
public class ScannerController {

    private final ScanLanguages scanLanguages;

    public ScannerController(final ScanLanguages scanLanguages) {
        this.scanLanguages = scanLanguages;
    }

    /**
     * @param language such as JAVA; what a project or scan names to scan
     *                 only some languages
     */
    public record ScannerResponse(
        String language
    ) {
    }

    /**
     * Every language this server has a scanner for, by name.
     */
    @GetMapping
    public ResponseEntity<List<ScannerResponse>> getScanners() {
        return ResponseEntity.ok(
            this.scanLanguages.available().stream()
                .map(ScannerResponse::new)
                .toList()
        );
    }

}
