package com.kamal.lc.report.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import com.kamal.lc.payroll.repository.CaptainAdjustmentRepository;
import com.kamal.lc.payroll.service.PayrollCalculator;
import com.kamal.lc.report.dto.CaptainReportResponse;
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
    private final SupplierIdAssignmentRepository assignmentRepo;
    private final SettingsResolver settingsResolver;
    private final PayrollCalculator calculator;

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
        BigDecimal dailyIncentives = sum(counted, Shift::getDailyIncentive);
        BigDecimal fuelAllowances = sum(counted, Shift::getFuelAllowance);
        BigDecimal carAllowances = sum(counted, Shift::getCarAllowance);
        BigDecimal supplierRevenue = sum(counted, Shift::getSupplierRevenue)
                .add(sum(counted, Shift::getSupplierExtraRevenue));

        BigDecimal adjustments = adjustmentRepo.findForCaptainInPeriod(captainId, from, to).stream()
                .map(ReportService::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PayrollCalculator.Result pay = calculator.compute(new PayrollCalculator.Input(
                captain.getCaptainType(), captain.isUsesOwnVehicle(),
                attended, totalOrders, lateDeductions, dailyIncentives, fuelAllowances,
                carAllowances, adjustments
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

        List<TeamReportResponse.Row> rows = new ArrayList<>();
        for (ShiftRepository.CaptainPeriodTotals totals : shiftRepo.aggregateByCaptain(from, to)) {
            Captain captain = captains.get(totals.getCaptainId());
            if (captain == null) continue;

            int attended = (int) totals.getAttendedShifts();
            int absent = (int) totals.getAbsentShifts();
            int orders = (int) totals.getTotalOrders();

            PayrollCalculator.Result pay = calculator.compute(new PayrollCalculator.Input(
                    captain.getCaptainType(), captain.isUsesOwnVehicle(), attended, orders,
                    totals.getLateDeductions(), totals.getDailyIncentives(), totals.getFuelAllowances(),
                    totals.getCarAllowances(),
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

        return TeamReportResponse.builder()
                .periodFrom(from.toString())
                .periodTo(to.toString())
                .rows(rows)
                .totals(totals(rows))
                .build();
    }

    // ---- helpers ----

    private TeamReportResponse.Totals totals(List<TeamReportResponse.Row> rows) {
        return TeamReportResponse.Totals.builder()
                .captains(rows.size())
                .attendedShifts(rows.stream().mapToInt(TeamReportResponse.Row::getAttendedShifts).sum())
                .absentShifts(rows.stream().mapToInt(TeamReportResponse.Row::getAbsentShifts).sum())
                .totalOrders(rows.stream().mapToInt(TeamReportResponse.Row::getTotalOrders).sum())
                .totalHours(rows.stream().map(TeamReportResponse.Row::getTotalHours)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .gross(rows.stream().map(TeamReportResponse.Row::getGross)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .net(rows.stream().map(TeamReportResponse.Row::getNet)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .supplierRevenueTotal(rows.stream().map(TeamReportResponse.Row::getSupplierRevenueTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .margin(rows.stream().map(TeamReportResponse.Row::getMargin)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
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
