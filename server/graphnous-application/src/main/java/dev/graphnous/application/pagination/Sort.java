package dev.graphnous.application.pagination;

public record Sort(
    String property,
    Direction direction
) {

    public enum Direction {
        ASC,
        DESC
        ;

        public static Direction fromValue(String value) {
            for (Direction dir : Direction.values()) {
                if (dir.name().equalsIgnoreCase(value)) {
                    return dir;
                }
            }
            throw new IllegalArgumentException("Unexpected value '" + value + "'");
        }
    }

    public static Sort defaultSort() {
        return new Sort("createdAt", Direction.DESC);
    }
}
