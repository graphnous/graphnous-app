package dev.graphnous.persistence.enhancer;

import dev.graphnous.application.enhancer.model.EnhancerManifestSchema;
import dev.graphnous.application.enhancer.model.PropertyMap;
import dev.graphnous.application.enhancer.model.RelationshipAction;
import dev.graphnous.application.enhancer.model.Rule;
import dev.graphnous.application.enhancer.model.Selector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Compiles one enhancer rule into a single Cypher query over a scan's graph.
 * <p>
 * The query matches the rule's nodes, adds its labels and properties, and
 * merges its relationships, all named in the manifest's namespace. Values
 * are parameters; names of labels, properties and relationship types cannot
 * be, so they are checked against strict patterns before they are written
 * into the query. Every node and relationship the rule changes records it
 * in {@code <Namespace>_enhancedBy}.
 * <p>
 * Rules only see the nodes of the scan, and of one target for a
 * target-scoped rule. Dependencies are shared between scans, so they are
 * not a kind a rule can match.
 */
final class RuleCypher {


    private static final Pattern NAMESPACE = Pattern.compile("[A-Z][A-Za-z0-9]{1,31}");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern RELATIONSHIP_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern PROPERTY_PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{(node|target)\\.([^}]*)}");

    private static final Set<String> CONDITIONS = Set.of("all", "any", "not", "property", "related", "hasLabel");
    private static final List<String> OPERATORS = List.of(
        "equals", "in", "matches", "startsWith", "endsWith", "contains", "exists"
    );

    /**
     * A query and its parameters. It returns one row with the number of
     * nodes the rule matched ({@code matched}) and of those it left without
     * an 'exactlyOne' relationship ({@code skipped}).
     */
    record Compiled(String query, Map<String, Object> parameters) {
    }

    private final String namespace;
    private final boolean limitedToTarget;
    private final Map<String, Object> parameters = new HashMap<>();

    private int nextParameter;
    private int nextAlias;

    private RuleCypher(
        final String namespace,
        final boolean limitedToTarget
    ) {
        this.namespace = namespace;
        this.limitedToTarget = limitedToTarget;
    }

    /**
     * @param targetId the id of the target node a target-scoped rule is
     *                 limited to; null for a scan-scoped rule
     * @throws IllegalArgumentException for a rule that cannot be compiled,
     *                                  naming what is wrong
     */
    static Compiled compile(
        final String scanId,
        final String targetId,
        final EnhancerManifestSchema manifest,
        final Rule rule
    ) {
        final var namespace = require(NAMESPACE, manifest.getNamespace(), "namespace");
        final var compiler = new RuleCypher(namespace, targetId != null);

        compiler.parameters.put("scanId", scanId);
        compiler.parameters.put("targetId", targetId);
        compiler.parameters.put("enhancedBy", manifest.getId() + "/" + rule.getId());

        try {
            return new Compiled(compiler.query(rule), Map.copyOf(withoutNullValues(compiler.parameters)));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Rule %s of enhancer %s: %s".formatted(rule.getId(), manifest.getId(), e.getMessage()),
                e
            );
        }
    }

    private String query(final Rule rule) {
        final var actions = rule.getActions();

        if (actions == null) {
            throw new IllegalArgumentException("it has no actions");
        }

        final var query = new StringBuilder();

        query.append(match("n", rule.getMatch(), null)).append('\n');

        final var nodeUpdates = new ArrayList<String>();

        for (final var label : actions.getAddLabels()) {
            nodeUpdates.add("n:" + name(label, "label"));
        }

        nodeUpdates.addAll(propertyUpdates("n", actions.getSetProperties(), false));

        if (!nodeUpdates.isEmpty()) {
            final var enhancedBy = "n." + name("enhancedBy", "property");

            nodeUpdates.add(enhancedBy + " = CASE WHEN $enhancedBy IN coalesce(" + enhancedBy + ", []) "
                + "THEN " + enhancedBy + " ELSE coalesce(" + enhancedBy + ", []) + $enhancedBy END");

            query.append("SET ").append(String.join(", ", nodeUpdates)).append('\n');
        }

        query.append("WITH n\n");

        final var skipped = new ArrayList<String>();

        for (final var relationship : actions.getAddRelationships()) {
            query.append(relationship(relationship));

            if (relationship.getCardinality() == RelationshipAction.Cardinality.EXACTLY_ONE) {
                skipped.add("CASE WHEN COUNT { " + target(relationship) + " } = 1 THEN 0 ELSE 1 END");
            }
        }

        query.append("RETURN count(n) AS matched, ")
            .append(skipped.isEmpty() ? "0" : "sum(" + String.join(" + ", skipped) + ")")
            .append(" AS skipped");

        return query.toString();
    }

    /**
     * Merges the relationship from each matched node to its targets.
     */
    private String relationship(final RelationshipAction action) {
        final var type = name(require(RELATIONSHIP_TYPE, action.getType(), "relationship type"), "relationship type");

        final var chosen = switch (action.getCardinality() == null ? RelationshipAction.Cardinality.ALL : action.getCardinality()) {
            case ALL -> "found";
            case FIRST -> "found[0..1]";
            case EXACTLY_ONE -> "CASE WHEN size(found) = 1 THEN found ELSE [] END";
        };

        final var merge = action.getDirection() == RelationshipAction.Direction.IN
            ? "MERGE (t)-[r:" + type + "]->(n)"
            : "MERGE (n)-[r:" + type + "]->(t)";

        final var updates = new ArrayList<String>();
        updates.add("r." + name("enhancedBy", "property") + " = $enhancedBy");
        updates.addAll(propertyUpdates("r", action.getProperties(), true));

        // Ordered, so 'first' picks the same target every time
        return "CALL (n) {\n"
            + "  " + target(action) + "\n"
            + "  WITH n, t ORDER BY t.id\n"
            + "  WITH n, collect(t) AS found\n"
            + "  UNWIND " + chosen + " AS t\n"
            + "  " + merge + "\n"
            + "  SET " + String.join(", ", updates) + "\n"
            + "}\n";
    }

    /**
     * Matches the targets of a relationship, other than the node itself.
     */
    private String target(final RelationshipAction action) {
        if (action.getTarget() == null) {
            throw new IllegalArgumentException("relationship " + action.getType() + " has no target");
        }

        return match("t", action.getTarget(), "n") + " AND t <> n";
    }

    /**
     * {@code MATCH (alias:Kind) WHERE ...} for a selector, limited to the
     * scan and, for a target-scoped rule, to the target.
     *
     * @param source the matched node, which {"source": ...} values refer to;
     *               null outside a relationship target
     */
    private String match(
        final String alias,
        final Selector selector,
        final String source
    ) {
        if (selector == null) {
            throw new IllegalArgumentException("a selector is missing");
        }

        return "MATCH (" + alias + ":" + kind(selector.getKind()) + ")\n"
            + "WHERE " + String.join(" AND ", selection(alias, selector, source));
    }

    private List<String> selection(
        final String alias,
        final Selector selector,
        final String source
    ) {
        final var conditions = new ArrayList<String>();

        conditions.add(alias + ".scanId = $scanId");

        if (limitedToTarget) {
            conditions.add(alias + ".targetId = $targetId");
        }

        if (selector.getLanguage() != null) {
            final var target = alias();

            conditions.add("EXISTS { MATCH (" + target + ":ScanTarget {id: " + alias + ".targetId}) WHERE toLower("
                + target + ".language) = " + parameter(selector.getLanguage().toLowerCase(Locale.ROOT)) + " }");
        }

        if (selector.getWhere() != null) {
            conditions.add(condition(alias, selector.getWhere(), source));
        }

        return conditions;
    }

    private String condition(
        final String alias,
        final Object condition,
        final String source
    ) {
        final var map = map(condition, "condition");
        final var kinds = map.keySet().stream().filter(CONDITIONS::contains).toList();

        if (kinds.size() != 1) {
            throw new IllegalArgumentException(
                "a condition needs exactly one of " + CONDITIONS.stream().sorted().toList() + ", got " + map.keySet()
            );
        }

        return switch (kinds.getFirst()) {
            case "all" -> combine(alias, map.get("all"), " AND ", source);
            case "any" -> combine(alias, map.get("any"), " OR ", source);
            case "not" -> "NOT (" + condition(alias, map.get("not"), source) + ")";
            case "hasLabel" -> alias + ":" + label(string(map.get("hasLabel"), "hasLabel"));
            case "related" -> related(alias, map(map.get("related"), "related"), source);
            default -> property(alias, map, source);
        };
    }

    private String combine(
        final String alias,
        final Object conditions,
        final String operator,
        final String source
    ) {
        final var list = list(conditions, "all/any");

        if (list.isEmpty()) {
            throw new IllegalArgumentException("all/any needs at least one condition");
        }

        return list.stream()
            .map(condition -> "(" + condition(alias, condition, source) + ")")
            .collect(Collectors.joining(operator, "(", ")"));
    }

    private String property(
        final String alias,
        final Map<String, Object> condition,
        final String source
    ) {
        final var property = alias + "." + propertyKey(string(condition.get("property"), "property"));
        final var operators = OPERATORS.stream().filter(condition::containsKey).toList();

        if (operators.size() != 1) {
            throw new IllegalArgumentException("property " + condition.get("property")
                + " needs exactly one of " + OPERATORS + ", got " + operators);
        }

        final var operator = operators.getFirst();
        final var value = condition.get(operator);
        final var ignoreCase = Boolean.FALSE.equals(condition.get("caseSensitive"));

        return switch (operator) {
            case "exists" -> property + (Boolean.TRUE.equals(value) ? " IS NOT NULL" : " IS NULL");
            case "matches" -> property + " =~ " + parameter((ignoreCase ? "(?i)" : "") + regex(string(value, "matches")));
            case "in" -> ignoreCase
                ? "(" + property + " IS :: STRING AND toLower(" + property + ") IN " + parameter(lowered(list(value, "in"))) + ")"
                : property + " IN " + parameter(scalars(list(value, "in")));
            case "equals" -> {
                final var expected = value instanceof Map<?, ?> reference
                    ? sourceProperty(reference, source)
                    : parameter(scalar(value, "equals"));

                yield ignoreCase
                    ? "(" + property + " IS :: STRING AND toLower(" + property + ") = toLower(" + expected + "))"
                    : property + " = " + expected;
            }
            default -> {
                final var cypher = switch (operator) {
                    case "startsWith" -> " STARTS WITH ";
                    case "endsWith" -> " ENDS WITH ";
                    default -> " CONTAINS ";
                };
                final var text = string(value, operator);

                yield ignoreCase
                    ? "(" + property + " IS :: STRING AND toLower(" + property + ")" + cypher
                        + parameter(text.toLowerCase(Locale.ROOT)) + ")"
                    : property + cypher + parameter(text);
            }
        };
    }

    /**
     * {"source": "name"}: a property of the matched node, to correlate a
     * relationship's targets with it.
     */
    private String sourceProperty(
        final Map<?, ?> reference,
        final String source
    ) {
        if (source == null) {
            throw new IllegalArgumentException("{\"source\": ...} is only valid in a relationship target");
        }

        if (reference.size() != 1 || !reference.containsKey("source")) {
            throw new IllegalArgumentException("equals takes a value or {\"source\": \"<property>\"}, got " + reference);
        }

        return source + "." + propertyKey(string(reference.get("source"), "source"));
    }

    /**
     * Whether the node has, over the relationship, the required number of
     * neighbours that match the 'to' selector.
     */
    private String related(
        final String alias,
        final Map<String, Object> related,
        final String source
    ) {
        final var type = relationshipName(string(related.get("type"), "related type"));
        final var direction = related.getOrDefault("direction", "out");
        final var neighbour = alias();

        final String neighbourNode;
        final String where;

        if (related.get("to") == null) {
            neighbourNode = "(" + neighbour + ")";
            where = "";
        } else {
            final var to = selector(map(related.get("to"), "related to"));
            neighbourNode = "(" + neighbour + ":" + kind(to.getKind()) + ")";
            where = " WHERE " + String.join(" AND ", selection(neighbour, to, source));
        }

        final var pattern = switch (String.valueOf(direction)) {
            case "out" -> "(" + alias + ")-[:" + type + "]->" + neighbourNode;
            case "in" -> "(" + alias + ")<-[:" + type + "]-" + neighbourNode;
            case "both" -> "(" + alias + ")-[:" + type + "]-" + neighbourNode;
            default -> throw new IllegalArgumentException("unknown direction " + direction);
        };

        final var count = "COUNT { MATCH " + pattern + where + " }";
        final var bounds = map(related.getOrDefault("count", Map.of()), "count");

        final var conditions = new ArrayList<String>();
        conditions.add(count + " >= " + parameter(bound(bounds.getOrDefault("min", 1), "min")));

        if (bounds.get("max") != null) {
            conditions.add(count + " <= " + parameter(bound(bounds.get("max"), "max")));
        }

        return "(" + String.join(" AND ", conditions) + ")";
    }

    /**
     * {@code alias.`Namespace_key` = value} for each property to write.
     *
     * @param relationship whether the properties go on a relationship, whose
     *                     templates can also use ${target.<property>}
     */
    private List<String> propertyUpdates(
        final String alias,
        final PropertyMap properties,
        final boolean relationship
    ) {
        if (properties == null) {
            return List.of();
        }

        return properties.getAdditionalProperties()
            .entrySet()
            .stream()
            .map(property -> alias + "." + name(property.getKey(), "property") + " = "
                + value(property.getValue(), relationship))
            .toList();
    }

    /**
     * A literal, {"template": "${node.name}Endpoint"} or {"fromProperty": "name"}.
     */
    private String value(
        final Object value,
        final boolean relationship
    ) {
        if (!(value instanceof Map<?, ?> map)) {
            return parameter(scalar(value, "property value"));
        }

        if (map.size() == 1 && map.containsKey("fromProperty")) {
            return "n." + propertyKey(string(map.get("fromProperty"), "fromProperty"));
        }

        if (map.size() == 1 && map.containsKey("template")) {
            return template(string(map.get("template"), "template"), relationship);
        }

        throw new IllegalArgumentException("a property value is a literal, template or fromProperty, got " + map);
    }

    private String template(
        final String template,
        final boolean relationship
    ) {
        final var parts = new ArrayList<String>();
        final var matcher = TEMPLATE_VARIABLE.matcher(template);

        var end = 0;

        while (matcher.find()) {
            if (matcher.start() > end) {
                parts.add(parameter(template.substring(end, matcher.start())));
            }

            final var node = matcher.group(1).equals("target") ? "t" : "n";

            if (node.equals("t") && !relationship) {
                throw new IllegalArgumentException("${target.…} is only valid on a relationship: " + template);
            }

            parts.add("coalesce(toString(" + node + "." + propertyKey(matcher.group(2)) + "), '')");
            end = matcher.end();
        }

        if (end < template.length()) {
            parts.add(parameter(template.substring(end)));
        }

        return parts.isEmpty() ? parameter("") : "(" + String.join(" + ", parts) + ")";
    }

    /**
     * The kinds a rule can match are the nodes of one scan, as the manifest
     * schema lists them.
     */
    private static String kind(final Selector.NodeKind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("a selector has no kind");
        }

        return "`" + kind.value() + "`";
    }

    /**
     * The kind of a selector inside a condition, which the model keeps as
     * JSON.
     */
    private static Selector.NodeKind kind(final String kind) {
        try {
            return Selector.NodeKind.fromValue(kind);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown node kind " + kind + "; expected one of "
                + Arrays.stream(Selector.NodeKind.values()).map(Selector.NodeKind::value).toList());
        }
    }

    /**
     * A name this enhancer produces, prefixed with its namespace.
     */
    private String name(
        final String name,
        final String what
    ) {
        return "`" + namespace + "_" + require(IDENTIFIER, name, what) + "`";
    }

    /**
     * A label to test for: core, or produced by an enhancer, with its
     * namespace.
     */
    private static String label(final String label) {
        return "`" + require(IDENTIFIER, label, "label") + "`";
    }

    private static String relationshipName(final String type) {
        return "`" + require(IDENTIFIER, type, "relationship type") + "`";
    }

    private static String propertyKey(final String key) {
        return "`" + require(PROPERTY_PATH, key, "property") + "`";
    }

    private String parameter(final Object value) {
        final var name = "p" + nextParameter++;

        parameters.put(name, value);

        return "$" + name;
    }

    private String alias() {
        return "x" + nextAlias++;
    }

    private static String require(
        final Pattern pattern,
        final String value,
        final String what
    ) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid " + what + " " + value);
        }

        return value;
    }

    private static String regex(final String regex) {
        try {
            Pattern.compile(regex);

            return regex;
        } catch (final PatternSyntaxException e) {
            throw new IllegalArgumentException("invalid regex " + regex + ": " + e.getDescription());
        }
    }

    private static Selector selector(final Map<String, Object> map) {
        final var selector = new Selector();
        selector.setKind(kind(string(map.get("kind"), "kind")));
        selector.setLanguage(map.get("language") == null ? null : string(map.get("language"), "language"));
        selector.setWhere(map.get("where"));

        return selector;
    }

    private static Object scalar(
        final Object value,
        final String what
    ) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        throw new IllegalArgumentException(what + " must be a string, number or boolean, got " + value);
    }

    private static List<Object> scalars(final List<Object> values) {
        return values.stream().map(value -> scalar(value, "in")).toList();
    }

    private static List<String> lowered(final List<Object> values) {
        return values.stream().map(value -> String.valueOf(scalar(value, "in")).toLowerCase(Locale.ROOT)).toList();
    }

    private static long bound(
        final Object value,
        final String what
    ) {
        if (value instanceof Number number && number.longValue() >= 0) {
            return number.longValue();
        }

        throw new IllegalArgumentException("count " + what + " must be a number of at least 0, got " + value);
    }

    private static String string(
        final Object value,
        final String what
    ) {
        if (value instanceof String string) {
            return string;
        }

        throw new IllegalArgumentException(what + " must be a string, got " + value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(
        final Object value,
        final String what
    ) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        throw new IllegalArgumentException(what + " must be an object, got " + value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(
        final Object value,
        final String what
    ) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }

        throw new IllegalArgumentException(what + " must be a list, got " + value);
    }

    private static Map<String, Object> withoutNullValues(final Map<String, Object> parameters) {
        final var copy = new HashMap<>(parameters);
        copy.values().removeIf(Objects::isNull);

        return copy;
    }
}
