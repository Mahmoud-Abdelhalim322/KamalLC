package com.kamal.lc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic paginated response wrapper.
 *
 * @param <T> the type of items in the page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /** Page size used when a list endpoint is called without one. */
    public static final int DEFAULT_SIZE = 10;

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    /**
     * Pages a list that is already filtered on the server, for sources that are aggregated in memory
     * (unions, per-row computed figures) rather than read with a single pageable query.
     * A page past the end comes back empty, as with a pageable query.
     */
    public static <T> PageResponse<T> slice(List<T> all, int page, int size) {
        int safeSize = size > 0 ? size : DEFAULT_SIZE;
        int totalPages = (int) Math.ceil(all.size() / (double) safeSize);
        int safePage = Math.max(0, page);
        int from = (int) Math.min((long) safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return PageResponse.<T>builder()
                .content(List.copyOf(all.subList(from, to)))
                .page(safePage)
                .size(safeSize)
                .totalElements(all.size())
                .totalPages(totalPages)
                .last(safePage >= totalPages - 1)
                .build();
    }
}
