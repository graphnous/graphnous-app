package dev.graphnous.application.pagination;

public record PageQuery(
    int page,
    int size,
    Sort sort
) {

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be >= 0");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size, Sort.defaultSort());
    }

}