package in.ruhmans.alliancetrading.common.pagination;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Offset-paged response for admin list screens (page numbers are 0-based). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(Page<?> page, List<T> items) {
        return new PageResponse<>(items, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** Clamp client-supplied paging so a request can never ask for unbounded rows. */
    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    /** Clamped page request sorted by {@code createdAt}, newest first. */
    public static PageRequest newestFirst(int page, int size) {
        return PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
