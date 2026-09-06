package com.kamal.lc.payroll.service;

import com.kamal.lc.settings.service.EffectiveSettings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The monthly half of the pay rules, kept separate from persistence so it can be checked
 * against Kamal's payroll sheet directly.
 *
 * <p>Per-shift amounts (daily wage, late deduction, daily incentive, fuel and car allowances) are
 * already computed and summed by then; what this adds is the monthly order target, which
 * can only be judged once the whole period is known.
 */
@Component
public class PayrollCalculator {

    private static final int MONEY_SCALE = 2;

    public record Input(
            String captainType,
            boolean usesOwnVehicle,
            int attendedShifts,
            int totalOrders,
            BigDecimal lateDeductions,
            BigDecimal supplierDeductions,
            BigDecimal dailyIncentives,
            BigDecimal fuelAllowances,
            BigDecimal carAllowances,
            BigDecimal vacationCommission,
            BigDecimal adjustmentsTotal) {}

    public record Result(
            BigDecimal basePay,
            BigDecimal lateDeductions,
            BigDecimal supplierDeductions,
            BigDecimal dailyIncentives,
            BigDecimal fuelAllowances,
            BigDecimal carAllowances,
            BigDecimal targetBonus,
            BigDecimal extraOrderPay,
            BigDecimal vacationCommission,
            BigDecimal gross,
            BigDecimal adjustmentsTotal,
            BigDecimal net) {}

    public Result compute(Input input, EffectiveSettings settings) {
        BigDecimal basePay = money(settings.getCaptainDailyRate()
                .multiply(BigDecimal.valueOf(input.attendedShifts())));

        BigDecimal lateDeductions = money(nz(input.lateDeductions()));
        BigDecimal supplierDeductions = money(nz(input.supplierDeductions()));
        BigDecimal dailyIncentives = money(nz(input.dailyIncentives()));
        BigDecimal fuelAllowances = money(nz(input.fuelAllowances()));
        BigDecimal carAllowances = money(nz(input.carAllowances()));
        BigDecimal vacationCommission = money(nz(input.vacationCommission()));

        // Hitting the monthly order target pays a flat bonus; orders past it are then paid
        // per order at a rate that depends on who carries the vehicle cost.
        boolean targetMet = input.totalOrders() >= settings.getMonthlyOrderTarget();
        BigDecimal targetBonus = targetMet ? money(settings.getTargetAchievementBonus()) : zero();

        BigDecimal extraOrderPay = zero();
        if (targetMet) {
            int ordersPastTarget = input.totalOrders() - settings.getMonthlyOrderTarget();
            BigDecimal rate = settings.postTargetRateFor(input.captainType(), input.usesOwnVehicle());
            extraOrderPay = money(rate.multiply(BigDecimal.valueOf(ordersPastTarget)));
        }

        BigDecimal gross = money(basePay
                .subtract(lateDeductions)
                .subtract(supplierDeductions)
                .add(dailyIncentives)
                .add(fuelAllowances)
                .add(carAllowances)
                .add(targetBonus)
                .add(extraOrderPay)
                .add(vacationCommission));

        BigDecimal adjustments = money(nz(input.adjustmentsTotal()));
        BigDecimal net = money(gross.add(adjustments));

        return new Result(basePay, lateDeductions, supplierDeductions, dailyIncentives, fuelAllowances, carAllowances,
                targetBonus, extraOrderPay, vacationCommission, gross, adjustments, net);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
