package dev.graphnous.persistence.scan;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pages through what a query matches in a scan's graph: how many rows
 * there are, and one page of them, read in one transaction so the two
 * agree.
 */
public final class GraphPages {

    private GraphPages() {
    }

    /**
     * @param match      the query up to the rows to page through, ending in a
     *                   MATCH, WHERE or WITH
     * @param sorts      the expression to order by for each property the
     *                   page may sort by
     * @param tiebreaker what orders rows the sort leaves equal, so pages
     *                   neither repeat nor skip rows
     * @param returns    what to return for each row, after RETURN
     */
    public static <T> Page<T> page(
        final Driver driver,
        final String match,
        final Map<String, String> sorts,
        final String tiebreaker,
        final String returns,
        final Map<String, Object> parameters,
        final PageQuery page,
        final Function<Record, T> mapper
    ) {
        final var sort = sorts.get(page.sort().property());

        if (sort == null) {
            throw new IllegalArgumentException("Cannot sort by " + page.sort().property());
        }

        final var direction = page.sort().direction().name();

        final var arguments = new HashMap<>(parameters);
        arguments.put("skip", (long) page.page() * page.size());
        arguments.put("limit", page.size());

        try (final var session = driver.session()) {
            return session.executeRead(tx -> {
                final var total = tx.run(match + "\nRETURN count(*) AS total", arguments)
                    .single()
                    .get("total")
                    .asLong();

                final List<T> content = tx.run(
                    match
                        + "\nWITH * ORDER BY " + sort + " " + direction + ", " + tiebreaker
                        + "\nSKIP $skip LIMIT $limit"
                        + "\nRETURN " + returns,
                    arguments
                ).list(mapper::apply);

                return new Page<>(
                    content,
                    page.page(),
                    page.size(),
                    Math.toIntExact(total),
                    Math.toIntExact((total + page.size() - 1) / page.size())
                );
            });
        }
    }

    public static String string(final Value value) {
        return value.isNull() ? null : value.asString();
    }

    public static List<String> strings(final Value value) {
        return value.isNull() ? List.of() : value.asList(Value::asString);
    }

    public static Long number(final Value value) {
        return value.isNull() ? null : value.asLong();
    }
}
