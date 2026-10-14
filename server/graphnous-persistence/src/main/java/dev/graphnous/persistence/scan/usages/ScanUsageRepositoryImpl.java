package dev.graphnous.persistence.scan.usages;

import dev.graphnous.application.scan.usages.ScanUsageRepository;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.links.HttpLinkRepositoryImpl;
import dev.graphnous.persistence.scan.result.EnhancementRepositoryImpl;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static dev.graphnous.persistence.scan.GraphPages.number;
import static dev.graphnous.persistence.scan.GraphPages.string;

/**
 * Reads what uses a node from the relationships ScanResultRepositoryImpl
 * stores for the bodies of methods and functions, and for inheritance; and
 * over HTTP, from the links HttpLinkRepositoryImpl stores between scans.
 */
@Repository
public class ScanUsageRepositoryImpl implements ScanUsageRepository {

    /**
     * How a node is used: by what a body does, or by a class that extends
     * or implements it.
     */
    private static final String USED_BY = "CALLS|INSTANTIATES|READS|WRITES|USES|EXTENDS|IMPLEMENTS";

    /**
     * What following usages outward goes along: what calls the method that
     * uses it, or constructs its class.
     */
    private static final String CALLED_BY = "CALLS|INSTANTIATES";

    /**
     * How far up and down inheritance is followed.
     */
    private static final int MAX_INHERITANCE = 10;

    private final Driver driver;

    public ScanUsageRepositoryImpl(final Driver driver) {
        this.driver = driver;
    }

    @Override
    public Optional<ScanUsages.Found> findUsages(
        final Scan.ScanId scanId,
        final String nodeId,
        final int depth,
        final int limit
    ) {
        final var scan = scanId.id().toString();

        try (final var session = driver.session()) {
            return session.executeRead(tx -> {
                final var subject = subject(tx, scan, nodeId);

                if (subject.isEmpty()) {
                    return Optional.empty();
                }

                final var usages = new ArrayList<ScanUsages.Usage>();

                // What was asked about, and its methods and fields
                final var own = members(tx, subject.get().id());

                // With the methods of their names above and below, which
                // a change to one is a change to
                var targets = withInherited(tx, own);
                final var seen = new HashSet<>(targets);

                // The classes and methods below it, which have to follow it
                usages.addAll(inheriting(tx, own, limit + 1));

                for (int step = 1; step <= depth && !targets.isEmpty() && usages.size() <= limit; step++) {
                    final var found = new ArrayList<>(
                        usages(tx, targets, step == 1 ? USED_BY : CALLED_BY, step, limit + 1 - usages.size())
                    );

                    // And what calls, over HTTP, an endpoint one of them serves
                    found.addAll(requests(tx, targets, step, limit + 1 - usages.size() - found.size()));

                    usages.addAll(found);

                    // Each user once, however many ways lead to it
                    targets = found.stream()
                        .map(usage -> usage.user().id())
                        .filter(seen::add)
                        .toList();
                }

                final var truncated = usages.size() > limit;

                return Optional.of(new ScanUsages.Found(
                    subject.get(),
                    truncated ? usages.subList(0, limit) : usages,
                    truncated
                ));
            });
        }
    }

    /**
     * The class, method or field with the id; for a node an enhancer
     * added, the one it enhances.
     */
    private static Optional<ScanUsages.Node> subject(
        final TransactionContext tx,
        final String scanId,
        final String nodeId
    ) {
        return tx.run("""
                CALL {
                    MATCH (node:Class {id: $id}) RETURN node
                    UNION
                    MATCH (node:Method {id: $id}) RETURN node
                    UNION
                    MATCH (node:Field {id: $id}) RETURN node
                    UNION
                    MATCH (source)-[:ENHANCE]->(:%s {id: $id})
                    WHERE source:Class OR source:Method OR source:Field
                    RETURN source AS node
                }
                WITH node WHERE node.scanId = $scanId
                RETURN node.id AS id, labels(node)[0] AS type, node.name AS name, node.qualifiedName AS qualifiedName
                LIMIT 1
                """.formatted(EnhancementRepositoryImpl.ENHANCED),
            Map.of("id", nodeId, "scanId", scanId)
        ).list(record -> node(record.get("id"), record.get("type"), record.get("name"), record.get("qualifiedName")))
            .stream()
            .findFirst();
    }

    /**
     * The node's id, with those of its methods and fields when it is a
     * class.
     */
    private static List<String> members(
        final TransactionContext tx,
        final String id
    ) {
        final var ids = new ArrayList<String>();
        ids.add(id);

        ids.addAll(tx.run("""
                MATCH (:Class {id: $id})-[:HAS_METHOD|HAS_FIELD]->(member)
                RETURN member.id AS id
                ORDER BY id
                """,
            Map.of("id", id)
        ).list(record -> record.get("id").asString()));

        return ids;
    }

    /**
     * The ids with those of the methods of the same name of the classes
     * above and below the class of each method among them; constructors
     * are of their own class only.
     */
    private static List<String> withInherited(
        final TransactionContext tx,
        final List<String> ids
    ) {
        final var all = new ArrayList<>(ids);

        tx.run("""
                MATCH (class:Class)-[:HAS_METHOD]->(method:Method)
                WHERE method.id IN $ids AND coalesce(method.kind, '') <> 'CONSTRUCTOR'
                CALL {
                    WITH class
                    MATCH (class)-[:EXTENDS|IMPLEMENTS*1..%1$d]->(related:Class) RETURN related
                    UNION
                    WITH class
                    MATCH (related:Class)-[:EXTENDS|IMPLEMENTS*1..%1$d]->(class) RETURN related
                }
                MATCH (related)-[:HAS_METHOD]->(other:Method {name: method.name})
                WHERE coalesce(other.kind, '') <> 'CONSTRUCTOR'
                RETURN DISTINCT other.id AS id
                ORDER BY id
                """.formatted(MAX_INHERITANCE),
            Map.of("ids", ids)
        ).list(record -> record.get("id").asString())
            .stream()
            .filter(id -> !all.contains(id))
            .forEach(all::add);

        return all;
    }

    /**
     * What has to follow a change through inheritance, each as a usage:
     * the classes that extend or implement those that extend or implement
     * one of the classes, which the usages themselves do not reach, and
     * the methods of the classes below that override one of the methods.
     */
    private static List<ScanUsages.Usage> inheriting(
        final TransactionContext tx,
        final List<String> ids,
        final int limit
    ) {
        return tx.run("""
                CALL {
                    MATCH (class:Class) WHERE class.id IN $ids
                    MATCH (user:Class)-[usage:EXTENDS|IMPLEMENTS]->(target:Class)-[:EXTENDS|IMPLEMENTS*1..%1$d]->(class)
                    RETURN DISTINCT user, type(usage) AS relationship, target, null AS owner, null AS targetOwner
                    UNION
                    MATCH (targetOwner:Class)-[:HAS_METHOD]->(target:Method)
                    WHERE target.id IN $ids AND coalesce(target.kind, '') <> 'CONSTRUCTOR'
                    MATCH (owner:Class)-[:EXTENDS|IMPLEMENTS*1..%1$d]->(targetOwner)
                    MATCH (owner)-[:HAS_METHOD]->(user:Method {name: target.name})
                    WHERE coalesce(user.kind, '') <> 'CONSTRUCTOR'
                    RETURN DISTINCT user, 'OVERRIDES' AS relationship, target, owner, targetOwner
                }
                OPTIONAL MATCH (file:File)-[:DECLARES]->(declared)
                WHERE declared = user OR declared = owner
                WITH user, relationship, target, owner, targetOwner, head(collect(file.path)) AS file
                ORDER BY file, user.id, target.id
                LIMIT $limit
                RETURN relationship, null AS line, null AS arguments, null AS argumentNames, null AS argumentSpreads,
                       file, owner.name AS owner, targetOwner.name AS targetOwner,
                       user.id AS userId, labels(user)[0] AS userType, user.name AS userName,
                       user.qualifiedName AS userQualifiedName,
                       target.id AS targetId, labels(target)[0] AS targetType, target.name AS targetName,
                       target.qualifiedName AS targetQualifiedName, user.scanId AS userScanId,
                       null AS callMethod, null AS callUrl, null AS endpointMethod, null AS endpointUrl
                """.formatted(MAX_INHERITANCE),
            Map.of("ids", ids, "limit", limit)
        ).list(record -> usage(record, 1));
    }

    /**
     * What uses any of the targets along the relationships, by file and
     * line, so the same request gives the same usages.
     */
    private static List<ScanUsages.Usage> usages(
        final TransactionContext tx,
        final List<String> targets,
        final String relationships,
        final int depth,
        final int limit
    ) {
        return tx.run("""
                MATCH (target) WHERE target.id IN $targets AND (target:Class OR target:Method OR target:Field)
                MATCH (user)-[usage:%s]->(target)
                OPTIONAL MATCH (owner:Class)-[:HAS_METHOD]->(user)
                OPTIONAL MATCH (targetOwner:Class)-[:HAS_METHOD|HAS_FIELD]->(target)
                OPTIONAL MATCH (file:File)-[:DECLARES]->(declared)
                WHERE declared = user OR declared = owner
                WITH user, usage, target, owner, targetOwner, head(collect(file.path)) AS file
                ORDER BY file, usage.line, user.id, target.id
                LIMIT $limit
                RETURN type(usage) AS relationship, usage.line AS line, usage.arguments AS arguments,
                       usage.argumentNames AS argumentNames, usage.argumentSpreads AS argumentSpreads,
                       file, owner.name AS owner, targetOwner.name AS targetOwner,
                       user.id AS userId, labels(user)[0] AS userType, user.name AS userName,
                       user.qualifiedName AS userQualifiedName,
                       target.id AS targetId, labels(target)[0] AS targetType, target.name AS targetName,
                       target.qualifiedName AS targetQualifiedName, user.scanId AS userScanId,
                       null AS callMethod, null AS callUrl, null AS endpointMethod, null AS endpointUrl
                """.formatted(relationships),
            Map.of("targets", targets, "limit", limit)
        ).list(record -> usage(record, depth));
    }

    /**
     * What calls, over HTTP, an endpoint one of the targets serves, as
     * the links stored between the scans of a system have it:
     * <pre>
     * (target)-[:ENHANCE]->(Endpoint)<-[:REQUESTS]-(HttpCall)<-[:ENHANCE]-(user)
     * </pre>
     * The user can be in the scan of another project. By file and line,
     * so the same request gives the same usages.
     */
    private static List<ScanUsages.Usage> requests(
        final TransactionContext tx,
        final List<String> targets,
        final int depth,
        final int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        return tx.run("""
                MATCH (target) WHERE target.id IN $targets AND (target:Class OR target:Method OR target:Field)
                MATCH (target)-[:ENHANCE]->(endpoint:%1$s:Endpoint)<-[:%2$s]-(call:%1$s:HttpCall)<-[:ENHANCE]-(user)
                WHERE user:Class OR user:Method OR user:Field
                OPTIONAL MATCH (owner:Class)-[:HAS_METHOD]->(user)
                OPTIONAL MATCH (targetOwner:Class)-[:HAS_METHOD|HAS_FIELD]->(target)
                OPTIONAL MATCH (file:File)-[:DECLARES]->(declared)
                WHERE declared = user OR declared = owner
                WITH user, call, endpoint, target, owner, targetOwner, head(collect(file.path)) AS file
                ORDER BY file, call.startLine, user.id, target.id
                LIMIT $limit
                RETURN 'REQUESTS' AS relationship, call.startLine AS line, null AS arguments,
                       null AS argumentNames, null AS argumentSpreads,
                       file, owner.name AS owner, targetOwner.name AS targetOwner,
                       user.id AS userId, labels(user)[0] AS userType, user.name AS userName,
                       user.qualifiedName AS userQualifiedName,
                       target.id AS targetId, labels(target)[0] AS targetType, target.name AS targetName,
                       target.qualifiedName AS targetQualifiedName, user.scanId AS userScanId,
                       call.httpMethod AS callMethod, call.url AS callUrl,
                       endpoint.httpMethod AS endpointMethod, endpoint.url AS endpointUrl
                """.formatted(EnhancementRepositoryImpl.ENHANCED, HttpLinkRepositoryImpl.REQUESTS),
            Map.of("targets", targets, "limit", limit)
        ).list(record -> usage(record, depth));
    }

    private static ScanUsages.Usage usage(
        final Record record,
        final int depth
    ) {
        return new ScanUsages.Usage(
            record.get("relationship").asString(),
            node(record.get("userId"), record.get("userType"), record.get("userName"), record.get("userQualifiedName")),
            string(record.get("owner")),
            string(record.get("file")),
            line(record.get("line")),
            node(record.get("targetId"), record.get("targetType"), record.get("targetName"), record.get("targetQualifiedName")),
            string(record.get("targetOwner")),
            depth,
            arguments(record),
            record.get("userScanId").isNull() ? null : new Scan.ScanId(UUID.fromString(record.get("userScanId").asString())),
            record.get("endpointUrl").isNull() ? null : new ScanUsages.Request(
                string(record.get("callMethod")),
                string(record.get("callUrl")),
                string(record.get("endpointMethod")),
                string(record.get("endpointUrl"))
            )
        );
    }

    /**
     * What the call passes; null when the usage does not say.
     */
    private static List<ScanUsages.Argument> arguments(final Record record) {
        if (record.get("arguments").isNull()) {
            return null;
        }

        final var values = record.get("arguments").asList(Value::asString);
        final var names = record.get("argumentNames").isNull()
            ? List.<String>of()
            : record.get("argumentNames").asList(Value::asString);
        final var spreads = record.get("argumentSpreads").isNull()
            ? List.<Boolean>of()
            : record.get("argumentSpreads").asList(Value::asBoolean);

        final var arguments = new ArrayList<ScanUsages.Argument>();

        for (int i = 0; i < values.size(); i++) {
            arguments.add(new ScanUsages.Argument(
                i < names.size() && !names.get(i).isEmpty() ? names.get(i) : null,
                values.get(i),
                i < spreads.size() && spreads.get(i)
            ));
        }

        return arguments;
    }

    private static Integer line(final Value value) {
        final var line = number(value);

        return line == null ? null : Math.toIntExact(line);
    }

    private static ScanUsages.Node node(
        final Value id,
        final Value type,
        final Value name,
        final Value qualifiedName
    ) {
        return new ScanUsages.Node(string(id), string(type), string(name), string(qualifiedName));
    }
}
