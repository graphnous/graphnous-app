package dev.graphnous.application.pagination;

import java.util.List;
import java.util.function.Function;

public record Page<T>(
    List<T> content,
    int page,
    int size,
    int totalElements,
    int totalPages
) {

    public <R> Page<R> map(final Function<T, R> mapper) {
        return new Page<>(
            content
                .stream()
                .map(mapper)
                .toList(),
            page,
            size,
            totalElements,
            totalPages
        );
    }
}
