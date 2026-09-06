package com.kamal.lc.dashboard.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.expiry.DocumentAlert;
import com.kamal.lc.captain.expiry.DocumentAlertEvaluator;
import com.kamal.lc.captain.expiry.DocumentKind;
import com.kamal.lc.captain.expiry.DocumentStatus;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.security.TenantContext;
import com.kamal.lc.custody.service.CustodyService;
import com.kamal.lc.dashboard.dto.CaptainPerformanceDTO;
import com.kamal.lc.dashboard.dto.DashboardResponse;
import com.kamal.lc.fleet.service.FleetService;
import com.kamal.lc.imports.entity.ImportBatch;
import com.kamal.lc.imports.repository.ImportBatchRepository;
import com.kamal.lc.report.dto.TeamReportResponse;
import com.kamal.lc.report.service.ReportService;
import com.kamal.lc.settings.entity.AppSetting;
import com.kamal.lc.settings.repository.AppSettingRepository;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.repository.ShiftRepository;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.expiry.StaffDocumentAlert;
import com.kamal.lc.staff.expiry.StaffDocumentAlertEvaluator;
import com.kamal.lc.staff.expiry.StaffDocumentKind;
import com.kamal.lc.staff.repository.StaffRepository;
import com.kamal.lc.supplier.repository.SupplierIdSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dashboard service — assembles summary data from all modules.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<String> ALERT_KEYS = List.of(
            "IQAMA_ALERT_DAYS",
            "WORK_PERMIT_ALERT_DAYS",
            "INSURANCE_ALERT_DAYS",
            "PASSPORT_ALERT_DAYS",
            "LICENCE_ALERT_DAYS",
            "QIWA_CONTRACT_ALERT_DAYS"
    );

    /** Cache TTL in milliseconds — 5 minutes (#8). */
    private static final long DOCUMENT_TALLY_TTL_MS = 5 * 60 * 1000L;

    private final CaptainRepository captainRepo;
    private final StaffRepository staffRepo;
    private final SupplierIdSlotRepository slotRepo;
    private final ImportBatchRepository batchRepo;
    private final ShiftRepository shiftRepo;
    private final ReportService reportService;
    private final DocumentAlertEvaluator captainAlerts;
    private final StaffDocumentAlertEvaluator staffAlerts;
    private final FleetService fleetService;
    private final CustodyService custodyService;
    private final AppSettingRepository appSettingRepo;
    private final SettingsResolver settingsResolver;

    /** Per-tenant cached document tally (#8). */
    private record CachedTally(DocumentTally tally, long timestampMs) {}
    private final Map<UUID, CachedTally> documentTallyCache = new ConcurrentHashMap<>();

    public DashboardResponse getDashboardData() {
        return getDashboardData(null, null);
    }

    public DashboardResponse getDashboardData(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        LocalDate periodStart = (from != null) ? from : today.withDayOfMonth(1);
        LocalDate periodEnd = (to != null) ? to : today.withDayOfMonth(today.lengthOfMonth());

        LocalDate weekEnd = periodEnd.isAfter(today) ? today : periodEnd;
        LocalDate weekStart = weekEnd.minusDays(6);

        long activeCaptains = captainRepo.countByStatus("ACTIVE");
        long unmatchedIds = slotRepo.countUnassigned();

        // Last committed import info
        String lastImportDate = null;
        String lastImportFile = null;
        String lastImportResult = null;

        var lastImport = batchRepo.findFirstByStatusOrderByUploadedAtDesc("COMMITTED");
        if (lastImport.isPresent()) {
            ImportBatch batch = lastImport.get();
            lastImportDate = batch.getUploadedAt().toString();
            lastImportFile = batch.getFileName();
            lastImportResult = batch.getImportedCount() + " سجل تم استيراده";
        }

        TeamReportResponse periodReport = reportService.teamReport(periodStart, periodEnd);
        TeamReportResponse.Totals periodTotals = periodReport.getTotals();

        TeamReportResponse weekReport = reportService.teamReport(weekStart, weekEnd);

        DocumentTally documents = cachedTallyDocuments(today);
        FleetService.FleetStats fleetStats = fleetService.getFleetStats();

        // Find primary alert threshold setting
        int currentAlertDays = 30;
        Map<DocumentKind, Integer> captainDays = captainAlerts.alertDays(today);
        if (!captainDays.isEmpty()) {
            currentAlertDays = captainDays.values().iterator().next();
        }

        // Calculations & Leaderboards
        BigDecimal avgOrdersPerShift = periodTotals.getAttendedShifts() > 0
                ? BigDecimal.valueOf(periodTotals.getTotalOrders())
                .divide(BigDecimal.valueOf(periodTotals.getAttendedShifts()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal fleetUtilizationRate = fleetStats.totalVehicles() > 0
                ? BigDecimal.valueOf(fleetStats.assigned())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(fleetStats.totalVehicles()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long totalCaptainsInReport = periodTotals.getCaptains() > 0 ? periodTotals.getCaptains() : activeCaptains;
        BigDecimal targetAchievementRate = totalCaptainsInReport > 0
                ? BigDecimal.valueOf(periodTotals.getCaptainsAtTarget())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCaptainsInReport), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long captainsBelowTarget = Math.max(0, totalCaptainsInReport - periodTotals.getCaptainsAtTarget() - periodTotals.getCaptainsNearTarget());

        List<CaptainPerformanceDTO> topCaptainsMonth = mapLeaderboard(periodReport.getRows(), true);
        List<CaptainPerformanceDTO> bottomCaptainsMonth = mapLeaderboard(periodReport.getRows(), false);
        List<CaptainPerformanceDTO> topCaptainsWeek = mapLeaderboard(weekReport.getRows(), true);
        List<CaptainPerformanceDTO> bottomCaptainsWeek = mapLeaderboard(weekReport.getRows(), false);

        return DashboardResponse.builder()
                .totalActiveCaptains(activeCaptains)
                .shiftsLast7Days(shiftRepo.countBetween(weekStart, weekEnd))
                .unmatchedNinjaIds(unmatchedIds)
                .lastImportDate(lastImportDate)
                .lastImportFile(lastImportFile)
                .lastImportResult(lastImportResult)
                .currentPeriodFrom(periodStart.toString())
                .currentPeriodTo(periodEnd.toString())
                .shiftsThisMonth(periodTotals.getAttendedShifts() + periodTotals.getAbsentShifts())
                .ordersThisMonth(periodTotals.getTotalOrders())
                .unmatchedShifts(shiftRepo.countUnmatched())
                .captainsAtTarget(periodTotals.getCaptainsAtTarget())
                .captainsNearTarget(periodTotals.getCaptainsNearTarget())
                .captainsBelowTarget(captainsBelowTarget)
                .expiredDocuments(documents.expired())
                .expiringDocuments(documents.expiring())
                .missingDocuments(documents.missing())
                .captainsWithDocumentAlerts(documents.members())
                .totalVehicles(fleetStats.totalVehicles())
                .availableVehicles(fleetStats.available())
                .assignedVehicles(fleetStats.assigned())
                .maintenanceVehicles(fleetStats.maintenance())
                .lateReturns(fleetStats.lateReturns())
                .activeCustodyItems(custodyService.countActive())
                .currentAlertDays(currentAlertDays)
                .totalNetPayroll(periodTotals.getNet())
                .totalGrossPayroll(periodTotals.getGross())
                .totalSupplierRevenue(periodTotals.getSupplierRevenueTotal())
                .estimatedMargin(periodTotals.getMargin())
                .avgOrdersPerShift(avgOrdersPerShift)
                .fleetUtilizationRate(fleetUtilizationRate)
                .targetAchievementRate(targetAchievementRate)
                .topCaptainsThisMonth(topCaptainsMonth)
                .bottomCaptainsThisMonth(bottomCaptainsMonth)
                .topCaptainsThisWeek(topCaptainsWeek)
                .bottomCaptainsThisWeek(bottomCaptainsWeek)
                .build();
    }

    private List<CaptainPerformanceDTO> mapLeaderboard(List<TeamReportResponse.Row> rows, boolean top) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<TeamReportResponse.Row> sorted = new ArrayList<>(rows);
        if (top) {
            sorted.sort(Comparator.comparing(TeamReportResponse.Row::getTotalOrders).reversed());
        } else {
            sorted.sort(Comparator.comparing(TeamReportResponse.Row::getTotalOrders));
        }
        return sorted.stream().limit(5).map(r -> CaptainPerformanceDTO.builder()
                .captainId(r.getCaptainId())
                .kamalCode(r.getCaptainCode())
                .captainName(r.getCaptainName())
                .captainType(r.getCaptainType())
                .totalOrders(r.getTotalOrders())
                .attendedShifts(r.getAttendedShifts())
                .attendanceRate(r.getAttendanceRate())
                .netPay(r.getNet())
                .build()).toList();
    }

    @Transactional
    public void updateAlertThreshold(int alertDays) {
        if (alertDays <= 0) {
            alertDays = 30;
        }
        LocalDate today = LocalDate.now();

        for (String key : ALERT_KEYS) {
            AppSetting current = appSettingRepo.findCurrent(key).orElse(null);
            if (current != null) {
                if (today.equals(current.getEffectiveFrom())) {
                    current.setValue(String.valueOf(alertDays));
                    appSettingRepo.save(current);
                } else {
                    current.setEffectiveTo(today.minusDays(1));
                    appSettingRepo.save(current);

                    appSettingRepo.save(AppSetting.builder()
                            .settingKey(key)
                            .settingGroup("EXPIRED_DOCUMENTS")
                            .labelAr(current.getLabelAr())
                            .value(String.valueOf(alertDays))
                            .valueType("INTEGER")
                            .unitLabel("يوم")
                            .effectiveFrom(today)
                            .build());
                }
            } else {
                appSettingRepo.save(AppSetting.builder()
                        .settingKey(key)
                        .settingGroup("EXPIRED_DOCUMENTS")
                        .labelAr("مهلة إنذار الوثائق")
                        .value(String.valueOf(alertDays))
                        .valueType("INTEGER")
                        .unitLabel("يوم")
                        .effectiveFrom(today)
                        .build());
            }
        }
        settingsResolver.invalidate();
    }

    /**
     * Returns a cached document tally for the current tenant, recomputing only if
     * the cached value is older than {@link #DOCUMENT_TALLY_TTL_MS} (#8).
     */
    private DocumentTally cachedTallyDocuments(LocalDate today) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        // Use a sentinel key for null tenant (platform admin)
        UUID cacheKey = tenantId != null ? tenantId : new UUID(0, 0);
        long now = System.currentTimeMillis();

        CachedTally cached = documentTallyCache.get(cacheKey);
        if (cached != null && (now - cached.timestampMs()) < DOCUMENT_TALLY_TTL_MS) {
            return cached.tally();
        }

        DocumentTally tally = tallyDocuments(today);
        documentTallyCache.put(cacheKey, new CachedTally(tally, now));
        return tally;
    }

    /**
     * Walks active captains AND staff members once, counting documents by verdict.
     */
    private DocumentTally tallyDocuments(LocalDate today) {
        Map<DocumentKind, Integer> captainAlertDays = captainAlerts.alertDays(today);
        long expired = 0, expiring = 0, missing = 0, members = 0;

        for (Captain captain : captainRepo.findByStatus("ACTIVE")) {
            boolean flagged = false;
            for (DocumentAlert alert : captainAlerts.evaluate(captain, today, captainAlertDays)) {
                if (alert.status() == DocumentStatus.EXPIRED) expired++;
                else if (alert.status() == DocumentStatus.EXPIRING) expiring++;
                else if (alert.status() == DocumentStatus.MISSING) missing++;
                else continue;
                flagged = true;
            }
            if (flagged) members++;
        }

        Map<StaffDocumentKind, Integer> staffAlertDays = staffAlerts.alertDays(today);
        for (Staff staff : staffRepo.findByStatus("ACTIVE")) {
            boolean flagged = false;
            for (StaffDocumentAlert alert : staffAlerts.evaluate(staff, today, staffAlertDays)) {
                if (alert.status() == DocumentStatus.EXPIRED) expired++;
                else if (alert.status() == DocumentStatus.EXPIRING) expiring++;
                else if (alert.status() == DocumentStatus.MISSING) missing++;
                else continue;
                flagged = true;
            }
            if (flagged) members++;
        }

        return new DocumentTally(expired, expiring, missing, members);
    }

    private record DocumentTally(long expired, long expiring, long missing, long members) {}
}

