package com.kamal.lc.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dashboard summary response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

    private long totalActiveCaptains;
    private long shiftsLast7Days;
    private long unmatchedNinjaIds;
    private String lastImportDate;
    private String lastImportFile;
    private String lastImportResult;

    // ---- Current month ----
    private String currentPeriodFrom;
    private String currentPeriodTo;
    private long shiftsThisMonth;
    private long ordersThisMonth;
    /** Shift rows whose supplier ID is not linked to any captain — work waiting to be done. */
    private long unmatchedShifts;

    // ---- Things worth acting on ----
    private long captainsAtTarget;
    /** Close enough to the monthly target that a nudge is worth real money. */
    private long captainsNearTarget;
    private long captainsBelowTarget;

    // ---- Document expiries (active captains only) ----
    // Counted per document, not per captain: renewing is done one document at a time, so the
    // number the office acts on is how many documents need work. A valid document adds to
    // none of these.

    /** Documents whose date has already passed. */
    private long expiredDocuments;
    /** Documents lapsing inside their own alert window. */
    private long expiringDocuments;
    /** Documents with no date on file — a gap that hides whichever of the two it really is. */
    private long missingDocuments;
    /** Captains carrying at least one of the above, for the size of the problem. */
    private long captainsWithDocumentAlerts;

    // ---- Fleet ----
    private long totalVehicles;
    private long availableVehicles;
    private long assignedVehicles;
    private long maintenanceVehicles;
    private long lateReturns;
    private long activeCustodyItems;
    private int currentAlertDays;

    // ---- Financials & Projections ----
    private BigDecimal totalNetPayroll;
    private BigDecimal totalGrossPayroll;
    private BigDecimal totalSupplierRevenue;
    private BigDecimal estimatedMargin;

    // ---- Productivity & Efficiency Rates ----
    private BigDecimal avgOrdersPerShift;
    private BigDecimal fleetUtilizationRate;
    private BigDecimal targetAchievementRate;

    // ---- Leaderboard (Top / Bottom 5) ----
    private List<CaptainPerformanceDTO> topCaptainsThisMonth;
    private List<CaptainPerformanceDTO> bottomCaptainsThisMonth;
    private List<CaptainPerformanceDTO> topCaptainsThisWeek;
    private List<CaptainPerformanceDTO> bottomCaptainsThisWeek;
}

