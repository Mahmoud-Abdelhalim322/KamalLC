package com.kamal.lc.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One captain's performance and projected pay over a period. */
@Data
@Builder
public class CaptainReportResponse {

    private UUID captainId;
    private String captainCode;
    private String captainName;
    private String captainType;
    private boolean usesOwnVehicle;
    private boolean fuelAllowanceEligible;
    private String supplierExternalIds;

    private String periodFrom;
    private String periodTo;

    // ---- Activity ----
    private int attendedShifts;
    private int absentShifts;
    private int totalShifts;
    /** Attended ÷ total, as a fraction. */
    private BigDecimal attendanceRate;
    private int totalOrders;
    private BigDecimal totalHours;
    private BigDecimal avgOrdersPerShift;
    private BigDecimal avgHoursPerShift;
    private BigDecimal avgBusyUtilization;
    private BigDecimal avgHourlyUtr;
    private BigDecimal avgDeliveryOntime;

    // ---- Pay (projected — an approved payroll run is the authority) ----
    private BigDecimal basePay;
    private BigDecimal lateDeductions;
    private BigDecimal dailyIncentives;
    private BigDecimal fuelAllowances;
    private BigDecimal carAllowances;
    private BigDecimal targetBonus;
    private BigDecimal extraOrderPay;
    private BigDecimal gross;
    private BigDecimal adjustmentsTotal;
    private BigDecimal net;

    // ---- Target progress ----
    private int monthlyOrderTarget;
    private int ordersToTarget;
    private BigDecimal targetProgress;
    /** What one more order is worth right now — 0 below target, the post-target rate above it. */
    private BigDecimal nextOrderValue;

    // ---- Profitability ----
    private BigDecimal supplierRevenueTotal;
    private BigDecimal margin;

    // ---- Comparison with the team over the same period ----
    private BigDecimal teamAvgOrdersPerShift;
    private BigDecimal teamAvgBusyUtilization;
    private BigDecimal teamAvgAttendanceRate;

    private List<DailyPoint> daily;
    private List<DeductionBreakdown> deductionBreakdown;

    /** One day in the period — the series behind the charts. */
    @Data
    @Builder
    public static class DailyPoint {
        private String date;
        private boolean attended;
        private Integer orders;
        private BigDecimal hours;
        private BigDecimal busyUtilization;
        private BigDecimal hourlyUtr;
        private BigDecimal netShiftPay;
        private BigDecimal lateDeduction;
        private String zoneName;
    }

    /** How many shifts landed on each deduction amount, and what that cost in total. */
    @Data
    @Builder
    public static class DeductionBreakdown {
        private BigDecimal deductionAmount;
        private int shiftCount;
        private BigDecimal total;
    }
}
