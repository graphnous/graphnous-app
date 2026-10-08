package dev.graphnous.api.scan.v1;

import dev.graphnous.application.pagination.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A page of what a scan found: its files, classes, methods or
 * dependencies. Shaped like the API's other pages; not in the published
 * OpenAPI spec yet, so it is its own.
 *
 * @param page          the page's number, from 0
 * @param totalElements of every page
 */
public record ScanItemPage<T>(
    List<T> content,
    int page,
    int size,
    int totalElements,
    int totalPages
) {

    static <D, T> ScanItemPage<T> of(final Page<D> page, final Function<D, T> mapper) {
        return new ScanItemPage<>(
            page.content().stream().map(mapper).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages()
        );
    }
}
