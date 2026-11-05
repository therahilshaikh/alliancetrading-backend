package in.ruhmans.alliancetrading.common.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * Cursor-based page. nextCursor is null when there are no more results.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public static <T> CursorPage<T> of(List<T> fetched, int limit, Function<T, String> cursorExtractor) {
        if (fetched.size() > limit) {
            List<T> page = fetched.subList(0, limit);
            return new CursorPage<>(List.copyOf(page), cursorExtractor.apply(page.get(page.size() - 1)));
        }
        return new CursorPage<>(List.copyOf(fetched), null);
    }
}
