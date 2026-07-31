package com.kamal.lc.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Every captain side by side over a period, plus the totals underneath. */
@Data
@Builder
public class TeamReportResponse {

    private String periodFrom;
    private String periodTo;
    private List<Row> rows;
    private Totals totals;

    @Data
    @Builder
    public static class Row {
        private UUID captainId;
        private String captainCode;
        private String captainName;
        private String captainType;
        private String supplierExternalIds;

        private int attendedShifts;
        private int absentShifts;
        private BigDecimal attendanceRate;
        private int totalOrders;
        private BigDecimal totalHours;
        private BigDecimal avgOrdersPerShift;
        private BigDecimal avgBusyUtilization;
        private BigDecimal avgHourlyUtr;
        private BigDecimal avgDeliveryOntime;

        private BigDecimal lateDeductions;
        private BigDecimal gross;
        private BigDecimal net;

        private BigDecimal supplierRevenueTotal;
        private BigDecimal margin;

        private boolean targetAchieved;
        private int ordersToTarget;
    }

    @Data
    @Builder
    public static class Totals {
        private int captains;
        private int attendedShifts;
        private int absentShifts;
        private int totalOrders;
        private BigDecimal totalHours;
        private BigDecimal gross;
        private BigDecimal net;
        private BigDecimal supplierRevenueTotal;
        private BigDecimal margin;
        private int captainsAtTarget;
        /** Captains within reach of the monthly target — the ones worth a nudge. */
        private int captainsNearTarget;
    }
}
