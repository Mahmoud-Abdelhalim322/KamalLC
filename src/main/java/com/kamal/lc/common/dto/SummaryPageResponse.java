package com.kamal.lc.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A page of rows plus figures computed over the whole filtered set (KPI strips, table footers),
 * so the client never has to load every row to show totals.
 *
 * @param <T> the type of items in the page
 * @param <S> the summary type
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummaryPageResponse<T, S> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean last;
    private S summary;

    public static <T, S> SummaryPageResponse<T, S> of(PageResponse<T> page, S summary) {
        return new SummaryPageResponse<>(page.getContent(), page.getPage(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast(), summary);
    }
}
