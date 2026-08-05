package com.kamal.lc.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily Delivery Per Captain Report — the grid every operations manager reviews
 * at the end of the month. One row per captain, one column per day, colour-coded
 * by how many orders the captain delivered that day.
 */
@Data
@Builder
public class OrdersDashboardResponse {

    private String periodFrom;
    private String periodTo;
    /** Ordered list of ISO dates (yyyy-MM-dd) that form the column headers. */
    private List<String> dates;
    private int target;

    private List<CaptainRow> captainRows;
    private DailySummary dailySummary;

    @Data
    @Builder
    public static class CaptainRow {
        private UUID captainId;
        private String captainCode;
        private String captainName;
        private String captainType;
        private int target;
        private int actual;
        /** actual / target, as a fraction (1.05 = 105%). */
        private BigDecimal achievementRate;
        /** date → order count for that day; missing dates mean zero. */
        private Map<String, Integer> dailyOrders;
    }

    @Data
    @Builder
    public static class DailySummary {
        private int totalCaptains;
        private int totalTarget;
        private int totalActual;
        private BigDecimal totalRate;
        /** date → sum of all captains' orders for that day. */
        private Map<String, Integer> dailyTotals;
    }
}
