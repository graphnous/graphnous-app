package dev.graphnous.persistence.enhancer;

import dev.graphnous.application.enhancer.EnhancementRepository;
import dev.graphnous.application.enhancer.RuleOutcome;
import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.application.enhancer.model.Rule;
import dev.graphnous.domain.scan.Scan;
import org.neo4j.driver.Driver;
import org.neo4j.driver.TransactionConfig;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Applies enhancer rules to a scan's graph in Neo4j, one query and one
 * transaction per rule. See {@link RuleCypher} for what a rule may touch.
 */
@Repository
public class EnhancementRepositoryImpl implements EnhancementRepository {

    /**
     * Rules run regular expressions from their manifest; a rule that takes
     * longer than this is stopped, and nothing of it is kept.
     */
    private static final Duration RULE_TIMEOUT = Duration.ofSeconds(60);

    private final Driver driver;

    public EnhancementRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public RuleOutcome apply(
        final Scan.ScanId scanId,
        final String targetPath,
        final EnhancerManifestSchema manifest,
        final Rule rule
    ) {
        final var id = scanId.id().toString();

        // The id the scan result graph gives a target
        final var targetId = targetPath == null ? null : id + "|" + targetPath;

        final var compiled = RuleCypher.compile(id, targetId, manifest, rule);

        try (final var session = driver.session()) {
            return session.executeWrite(
                tx -> {
                    final var result = tx.run(compiled.query(), compiled.parameters());
                    final var row = result.single();
                    final var counters = result.consume().counters();

                    return new RuleOutcome(
                        rule.getId(),
                        row.get("matched").asLong(),
                        counters.labelsAdded(),
                        counters.propertiesSet(),
                        counters.relationshipsCreated(),
                        row.get("skipped").asLong()
                    );
                },
                TransactionConfig.builder().withTimeout(RULE_TIMEOUT).build()
            );
        }
    }
}
