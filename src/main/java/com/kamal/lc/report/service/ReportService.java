package com.kamal.lc.report.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import com.kamal.lc.payroll.repository.CaptainAdjustmentRepository;
import com.kamal.lc.payroll.service.PayrollCalculator;
import com.kamal.lc.report.dto.CaptainReportResponse;
import com.kamal.lc.report.dto.OrdersDashboardResponse;
import com.kamal.lc.report.dto.TeamReportResponse;
import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read-only performance and profitability reporting over the stored shifts.
 *
 * <p>Pay figures here are <em>projections</em> computed live from current rates; an
 * approved payroll run remains the authority on what was actually paid.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    /** Captains within this many orders of the target are worth chasing before month end. */
    private static final int NEAR_TARGET_WINDOW = 60;

    private final ShiftRepository shiftRepo;
    private final CaptainRepository captainRepo;
    private final CaptainAdjustmentRepository adjustmentRepo;
    private final com.kamal.lc.supplier.repository.SupplierAdjustmentRepository supplierAdjustmentRepo;
    private final SupplierIdAssignmentRepository assignmentRepo;
    private final SettingsResolver settingsResolver;
    private final PayrollCalculator calculator;
    private final com.kamal.lc.auth.service.CaptainAllocationService allocationService;
    private final com.kamal.lc.imports.repository.VacationCommissionRepository vacationCommissionRepo;

    public CaptainReportResponse captainReport(UUID captainId, LocalDate from, LocalDate to) {
        Captain captain = captainRepo.findById(captainId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));

        EffectiveSettings settings = settingsResolver.resolve(to);
        List<Shift> shifts = shiftRepo.findByCaptainIdAndShiftDateBetweenOrderByShiftDateAsc(
                captainId, from, to);

        List<Shift> counted = shifts.stream().filter(s -> !s.isExcluded()).toList();
        int attended = (int) counted.stream().filter(s -> Boolean.TRUE.equals(s.getAttended())).count();
        int absent = counted.size() - attended;
        int totalOrders = counted.stream().mapToInt(s -> nz(s.getTotalDelivered())).sum();
        BigDecimal totalHours = sum(counted, Shift::getWorkingHours);

        BigDecimal lateDeductions = sum(counted, Shift::getLateDeduction);
        BigDecimal supplierDeductions = sum(counted, Shift::getSupplierDeduction);
        BigDecimal dailyIncentives = sum(counted, Shift::getDailyIncentive);
        BigDecimal fuelAllowances = sum(counted, Shift::getFuelAllowance);
        BigDecimal carAllowances = sum(counted, Shift::getCarAllowance);
        BigDecimal supplierRevenue = sum(counted, Shift::getSupplierRevenue)
                .add(sum(counted, Shift::getSupplierExtraRevenue));

        BigDecimal adjustments = adjustmentRepo.findForCaptainInPeriod(captainId, from, to).stream()
                .map(ReportService::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal vacationCommission = vacationCommissionRepo.sumByCaptainAndDateBetween(captainId, from, to);

        PayrollCalculator.Result pay = calculator.compute(new PayrollCalculator.Input(
                captain.getCaptainType(), captain.isUsesOwnVehicle(),
                attended, totalOrders, lateDeductions, supplierDeductions, dailyIncentives, fuelAllowances,
                carAllowances, vacationCommission, adjustments
        ), settings);

        int ordersToTarget = Math.max(0, settings.getMonthlyOrderTarget() - totalOrders);
        boolean targetMet = totalOrders >= settings.getMonthlyOrderTarget();
        BigDecimal nextOrderValue = targetMet
                ? settings.postTargetRateFor(captain.getCaptainType(), captain.isUsesOwnVehicle())
                : BigDecimal.ZERO;

        TeamAverages team = teamAverages(from, to);

        return CaptainReportResponse.builder()
                .captainId(captain.getId())
                .captainCode(captain.getKamalCode())
                .captainName(captain.getFullNameAr())
                .captainType(captain.getCaptainType())
                .usesOwnVehicle(captain.isUsesOwnVehicle())
                .fuelAllowanceEligible(captain.isFuelAllowanceEligible())
                .supplierExternalIds(supplierIdsFor(captainId))
                .periodFrom(from.toString())
                .periodTo(to.toString())
                .attendedShifts(attended)
                .absentShifts(absent)
                .totalShifts(counted.size())
                .attendanceRate(ratio(attended, counted.size()))
                .totalOrders(totalOrders)
                .totalHours(scale(totalHours, 2))
                .avgOrdersPerShift(ratio(totalOrders, attended))
                .avgHoursPerShift(divide(totalHours, attended, 2))
                .avgBusyUtilization(average(counted, Shift::getBusyUtilization))
                .avgHourlyUtr(average(counted, Shift::getHourlyUtr))
                .avgDeliveryOntime(average(counted, Shift::getDeliveryOntime))
                .basePay(pay.basePay())
                .lateDeductions(pay.lateDeductions())
                .dailyIncentives(pay.dailyIncentives())
                .fuelAllowances(pay.fuelAllowances())
                .carAllowances(pay.carAllowances())
                .targetBonus(pay.targetBonus())
                .extraOrderPay(pay.extraOrderPay())
                .gross(pay.gross())
                .adjustmentsTotal(pay.adjustmentsTotal())
                .net(pay.net())
                .monthlyOrderTarget(settings.getMonthlyOrderTarget())
                .ordersToTarget(ordersToTarget)
                .targetProgress(ratio(totalOrders, settings.getMonthlyOrderTarget()))
                .nextOrderValue(nextOrderValue)
                .supplierRevenueTotal(supplierRevenue)
                .margin(supplierRevenue.subtract(pay.net()))
                .teamAvgOrdersPerShift(team.avgOrdersPerShift())
                .teamAvgBusyUtilization(team.avgBusyUtilization())
                .teamAvgAttendanceRate(team.avgAttendanceRate())
                .daily(counted.stream().map(ReportService::toDailyPoint).toList())
                .deductionBreakdown(deductionBreakdown(counted))
                .build();
    }

    public TeamReportResponse teamReport(LocalDate from, LocalDate to) {
        EffectiveSettings settings = settingsResolver.resolve(to);

        Map<UUID, Captain> captains = captainRepo.findAll().stream()
                .collect(Collectors.toMap(Captain::getId, c -> c));

        Map<UUID, BigDecimal> adjustmentsByCaptain = adjustmentRepo.findInPeriod(from, to).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCaptain().getId(),
                        Collectors.reducing(BigDecimal.ZERO, ReportService::signedAmount, BigDecimal::add)));

        Map<UUID, BigDecimal> vacationCommissionsByCaptain = vacationCommissionRepo.aggregateByCaptain(from, to).stream()
                .collect(Collectors.toMap(
                        com.kamal.lc.imports.repository.VacationCommissionRepository.CaptainVacationCommissionTotals::getCaptainId,
                        com.kamal.lc.imports.repository.VacationCommissionRepository.CaptainVacationCommissionTotals::getTotalVacationCommission));

        List<TeamReportResponse.Row> rows = new ArrayList<>();
        for (ShiftRepository.CaptainPeriodTotals totals : shiftRepo.aggregateByCaptain(from, to)) {
            Captain captain = captains.get(totals.getCaptainId());
            if (captain == null) continue;

            int attended = (int) totals.getAttendedShifts();
            int absent = (int) totals.getAbsentShifts();
            int orders = (int) totals.getTotalOrders();

            PayrollCalculator.Result pay = calculator.compute(new PayrollCalculator.Input(
                    captain.getCaptainType(), captain.isUsesOwnVehicle(), attended, orders,
                    totals.getLateDeductions(), totals.getSupplierDeductions(), totals.getDailyIncentives(), totals.getFuelAllowances(),
                    totals.getCarAllowances(),
                    vacationCommissionsByCaptain.getOrDefault(captain.getId(), BigDecimal.ZERO),
                    adjustmentsByCaptain.getOrDefault(captain.getId(), BigDecimal.ZERO)
            ), settings);

            BigDecimal revenue = nz(totals.getSupplierRevenueTotal());

            rows.add(TeamReportResponse.Row.builder()
                    .captainId(captain.getId())
                    .captainCode(captain.getKamalCode())
                    .captainName(captain.getFullNameAr())
                    .captainType(captain.getCaptainType())
                    .supplierExternalIds(supplierIdsFor(captain.getId()))
                    .attendedShifts(attended)
                    .absentShifts(absent)
                    .attendanceRate(ratio(attended, attended + absent))
                    .totalOrders(orders)
                    .totalHours(scale(nz(totals.getTotalHours()), 2))
                    .avgOrdersPerShift(ratio(orders, attended))
                    .avgBusyUtilization(scale(totals.getAvgBusyUtilization(), 4))
                    .avgHourlyUtr(scale(totals.getAvgHourlyUtr(), 3))
                    .avgDeliveryOntime(scale(totals.getAvgDeliveryOntime(), 4))
                    .lateDeductions(pay.lateDeductions())
                    .gross(pay.gross())
                    .net(pay.net())
                    .supplierRevenueTotal(revenue)
                    .margin(revenue.subtract(pay.net()))
                    .targetAchieved(orders >= settings.getMonthlyOrderTarget())
                    .ordersToTarget(Math.max(0, settings.getMonthlyOrderTarget() - orders))
                    .build());
        }

        rows.sort(Comparator.comparing(TeamReportResponse.Row::getTotalOrders).reversed());

        List<UUID> accessibleIds = allocationService.getCurrentUserAccessibleCaptainIds();
        if (accessibleIds != null) {
            rows = rows.stream().filter(r -> accessibleIds.contains(r.getCaptainId())).toList();
        }

        return TeamReportResponse.builder()
                .periodFrom(from.toString())
                .periodTo(to.toString())
                .rows(rows)
                .totals(totals(rows, from, to))
                .build();
    }

    /**
     * Daily Delivery Per Captain — the grid behind the orders dashboard.
     *
     * <p>One row per captain, one column per calendar day in the period. Each cell is
     * the number of orders delivered on that day (sum of {@code totalDelivered} across
     * the captain's non-excluded shifts on that date).
     *
     * @param captainType {@code "EMPLOYEE"}, {@code "FREELANCE"}, or {@code null}/blank for all
     */
    public OrdersDashboardResponse ordersDashboard(LocalDate from, LocalDate to, String captainType) {
        int target = settingsResolver.resolve(to).getMonthlyOrderTarget();

        // All non-excluded shifts in the period, with their captain eagerly available.
        List<UUID> accessibleIds = allocationService.getCurrentUserAccessibleCaptainIds();
        List<Shift> shifts = shiftRepo.findByShiftDateBetween(from, to).stream()
                .filter(s -> !s.isExcluded() && s.getCaptain() != null && (accessibleIds == null || accessibleIds.contains(s.getCaptain().getId())))
                .toList();

        // Build a lookup of captains; optionally filter by type.
        Map<UUID, Captain> captains = captainRepo.findAll().stream()
                .filter(c -> captainType == null || captainType.isBlank()
                        || "ALL".equalsIgnoreCase(captainType)
                        || captainType.equalsIgnoreCase(c.getCaptainType()))
                .collect(Collectors.toMap(Captain::getId, c -> c));

        // The ordered list of dates that will become the column headers.
        List<String> dates = from.datesUntil(to.plusDays(1))
                .map(LocalDate::toString)
                .toList();

        // Group: captainId → date → list of order counts (one per shift)
        Map<UUID, Map<String, List<Integer>>> grid = new LinkedHashMap<>();
        // Group: captainId → date → list of working hours (one per shift)
        Map<UUID, Map<String, List<BigDecimal>>> hoursGrid = new LinkedHashMap<>();
        // Accumulate hours and utilization per captain
        Map<UUID, BigDecimal> hoursMap = new LinkedHashMap<>();
        Map<UUID, List<BigDecimal>> utilizationMap = new LinkedHashMap<>();
        // Accumulate late deductions and extra orders per captain
        Map<UUID, BigDecimal> lateDeductionMap = new LinkedHashMap<>();
        Map<UUID, Integer> extraOrdersMap = new LinkedHashMap<>();

        for (Shift shift : shifts) {
            UUID cid = shift.getCaptain().getId();
            if (!captains.containsKey(cid)) continue;

            String dateKey = shift.getShiftDate().toString();
            int delivered = nz(shift.getTotalDelivered());
            grid.computeIfAbsent(cid, k -> new LinkedHashMap<>())
                    .computeIfAbsent(dateKey, k -> new ArrayList<>())
                    .add(delivered);

            // Accumulate hours per day per shift
            BigDecimal hours = shift.getWorkingHours() != null ? shift.getWorkingHours() : BigDecimal.ZERO;
            hoursGrid.computeIfAbsent(cid, k -> new LinkedHashMap<>())
                    .computeIfAbsent(dateKey, k -> new ArrayList<>())
                    .add(hours.setScale(1, RoundingMode.HALF_UP));

            // Accumulate total hours
            hoursMap.merge(cid, hours, BigDecimal::add);

            // Accumulate utilization values for averaging later
            if (shift.getBusyUtilization() != null) {
                utilizationMap.computeIfAbsent(cid, k -> new ArrayList<>())
                        .add(shift.getBusyUtilization());
            }

            // Accumulate late deductions
            BigDecimal lateDed = shift.getLateDeduction() != null ? shift.getLateDeduction() : BigDecimal.ZERO;
            lateDeductionMap.merge(cid, lateDed, BigDecimal::add);

            // Extra orders beyond 18 per shift
            if (delivered > 18) {
                extraOrdersMap.merge(cid, delivered - 18, Integer::sum);
            }
        }

        // Build rows
        List<OrdersDashboardResponse.CaptainRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, Map<String, List<Integer>>> entry : grid.entrySet()) {
            Captain captain = captains.get(entry.getKey());
            if (captain == null) continue;

            Map<String, List<Integer>> daily = entry.getValue();
            int actual = daily.values().stream()
                    .flatMapToInt(list -> list.stream().mapToInt(Integer::intValue))
                    .sum();
            BigDecimal rate = target > 0
                    ? BigDecimal.valueOf(actual).divide(BigDecimal.valueOf(target), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            int workDays = daily.size(); // number of distinct days with shifts

            BigDecimal totalHours = hoursMap.getOrDefault(entry.getKey(), BigDecimal.ZERO);

            List<BigDecimal> utils = utilizationMap.getOrDefault(entry.getKey(), List.of());
            BigDecimal avgUtil = utils.isEmpty() ? BigDecimal.ZERO
                    : utils.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(utils.size()), 4, RoundingMode.HALF_UP);

            rows.add(OrdersDashboardResponse.CaptainRow.builder()
                    .captainId(captain.getId())
                    .captainCode(captain.getKamalCode())
                    .captainName(captain.getFullNameAr())
                    .captainType(captain.getCaptainType())
                    .target(target)
                    .actual(actual)
                    .achievementRate(rate)
                    .dailyOrders(daily)
                    .dailyHours(hoursGrid.getOrDefault(entry.getKey(), Map.of()))
                    .workDays(workDays)
                    .totalHours(totalHours.setScale(2, RoundingMode.HALF_UP))
                    .avgBusyUtilization(avgUtil)
                    .totalLateDeduction(lateDeductionMap.getOrDefault(entry.getKey(), BigDecimal.ZERO))
                    .totalExtraOrders(extraOrdersMap.getOrDefault(entry.getKey(), 0))
                    .build());
        }

        // Sort by captainCode (KSC0001, KSC0002, …)
        rows.sort(Comparator.comparing(OrdersDashboardResponse.CaptainRow::getCaptainCode));

        // Daily totals across all captains
        Map<String, Integer> dailyTotals = new LinkedHashMap<>();
        for (String date : dates) {
            int dayTotal = rows.stream()
                    .mapToInt(r -> r.getDailyOrders().getOrDefault(date, List.of()).stream()
                            .mapToInt(Integer::intValue).sum())
                    .sum();
            dailyTotals.put(date, dayTotal);
        }

        int totalActual = rows.stream().mapToInt(OrdersDashboardResponse.CaptainRow::getActual).sum();
        int totalTarget = rows.size() * target;
        BigDecimal totalRate = totalTarget > 0
                ? BigDecimal.valueOf(totalActual).divide(BigDecimal.valueOf(totalTarget), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return OrdersDashboardResponse.builder()
                .periodFrom(from.toString())
                .periodTo(to.toString())
                .dates(dates)
                .target(target)
                .captainRows(rows)
                .dailySummary(OrdersDashboardResponse.DailySummary.builder()
                        .totalCaptains(rows.size())
                        .totalTarget(totalTarget)
                        .totalActual(totalActual)
                        .totalRate(totalRate)
                        .dailyTotals(dailyTotals)
                        .build())
                .build();
    }

    // ---- helpers ----

    private TeamReportResponse.Totals totals(List<TeamReportResponse.Row> rows, LocalDate from, LocalDate to) {
        BigDecimal shiftRevenue = rows.stream().map(TeamReportResponse.Row::getSupplierRevenueTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netSupplierAdjustments = supplierAdjustmentRepo.findByAdjustmentDateBetweenOrderByAdjustmentDateDesc(from, to)
                .stream()
                .map(a -> "ADDITION".equalsIgnoreCase(a.getType()) ? a.getAmount() : a.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSupplierRevenue = shiftRevenue.add(netSupplierAdjustments);
        BigDecimal netPay = rows.stream().map(TeamReportResponse.Row::getNet)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TeamReportResponse.Totals.builder()
                .captains(rows.size())
                .attendedShifts(rows.stream().mapToInt(TeamReportResponse.Row::getAttendedShifts).sum())
                .absentShifts(rows.stream().mapToInt(TeamReportResponse.Row::getAbsentShifts).sum())
                .totalOrders(rows.stream().mapToInt(TeamReportResponse.Row::getTotalOrders).sum())
                .totalHours(rows.stream().map(TeamReportResponse.Row::getTotalHours)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .gross(rows.stream().map(TeamReportResponse.Row::getGross)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .net(netPay)
                .supplierRevenueTotal(totalSupplierRevenue)
                .margin(totalSupplierRevenue.subtract(netPay))
                .captainsAtTarget((int) rows.stream().filter(TeamReportResponse.Row::isTargetAchieved).count())
                .captainsNearTarget((int) rows.stream()
                        .filter(row -> !row.isTargetAchieved() && row.getOrdersToTarget() <= NEAR_TARGET_WINDOW)
                        .count())
                .build();
    }

    private record TeamAverages(BigDecimal avgOrdersPerShift,
                                BigDecimal avgBusyUtilization,
                                BigDecimal avgAttendanceRate) {}

    private TeamAverages teamAverages(LocalDate from, LocalDate to) {
        List<ShiftRepository.CaptainPeriodTotals> all = shiftRepo.aggregateByCaptain(from, to);
        if (all.isEmpty()) {
            return new TeamAverages(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        long attended = all.stream().mapToLong(ShiftRepository.CaptainPeriodTotals::getAttendedShifts).sum();
        long absent = all.stream().mapToLong(ShiftRepository.CaptainPeriodTotals::getAbsentShifts).sum();
        long orders = all.stream().mapToLong(ShiftRepository.CaptainPeriodTotals::getTotalOrders).sum();

        BigDecimal utilizationSum = all.stream()
                .map(ShiftRepository.CaptainPeriodTotals::getAvgBusyUtilization)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long utilizationCount = all.stream()
                .filter(t -> t.getAvgBusyUtilization() != null).count();

        return new TeamAverages(
                ratio(orders, attended),
                divide(utilizationSum, utilizationCount, 4),
                ratio(attended, attended + absent));
    }

    private static CaptainReportResponse.DailyPoint toDailyPoint(Shift s) {
        return CaptainReportResponse.DailyPoint.builder()
                .date(s.getShiftDate() != null ? s.getShiftDate().toString() : null)
                .attended(Boolean.TRUE.equals(s.getAttended()))
                .orders(s.getTotalDelivered())
                .hours(s.getWorkingHours())
                .busyUtilization(s.getBusyUtilization())
                .hourlyUtr(s.getHourlyUtr())
                .netShiftPay(s.getNetShiftPay())
                .lateDeduction(s.getLateDeduction())
                .zoneName(s.getZoneName())
                .build();
    }

    /** Groups the period's shifts by the deduction they incurred — where the money went. */
    private List<CaptainReportResponse.DeductionBreakdown> deductionBreakdown(List<Shift> shifts) {
        Map<BigDecimal, List<Shift>> byAmount = shifts.stream()
                .filter(s -> s.getLateDeduction() != null && s.getLateDeduction().signum() > 0)
                .collect(Collectors.groupingBy(s -> s.getLateDeduction().stripTrailingZeros()));

        return byAmount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> CaptainReportResponse.DeductionBreakdown.builder()
                        .deductionAmount(e.getKey())
                        .shiftCount(e.getValue().size())
                        .total(e.getKey().multiply(BigDecimal.valueOf(e.getValue().size())))
                        .build())
                .toList();
    }

    private String supplierIdsFor(UUID captainId) {
        List<String> ids = assignmentRepo.findByCaptainIdOrderByValidFromDesc(captainId).stream()
                .map(a -> a.getSupplierIdSlot().getExternalId())
                .distinct()
                .toList();
        return ids.isEmpty() ? null : String.join(", ", ids);
    }

    private static BigDecimal signedAmount(CaptainAdjustment a) {
        return CaptainAdjustment.BONUS.equals(a.getKind()) ? a.getAmount() : a.getAmount().negate();
    }

    private static BigDecimal sum(List<Shift> shifts, java.util.function.Function<Shift, BigDecimal> field) {
        return shifts.stream().map(field).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal average(List<Shift> shifts, java.util.function.Function<Shift, BigDecimal> field) {
        List<BigDecimal> values = shifts.stream().map(field).filter(Objects::nonNull).toList();
        if (values.isEmpty()) return BigDecimal.ZERO;
        return divide(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add), values.size(), 4);
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        return divide(BigDecimal.valueOf(numerator), denominator, 4);
    }

    private static BigDecimal divide(BigDecimal numerator, long denominator, int scale) {
        if (denominator == 0) return BigDecimal.ZERO;
        return numerator.divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value != null ? value.setScale(scale, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private static int nz(Integer value) {
        return value != null ? value : 0;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
