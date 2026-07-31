package com.kamal.lc.shift.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.shift.entity.Shift;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns one shift's raw supplier numbers into money — both what the captain earns and
 * what the supplier pays Kamal for it.
 *
 * <p>Deliberately free of repositories and entities in its core: {@link #compute} takes
 * plain values and returns plain values, so the rules can be tested against Kamal's
 * spreadsheet without a database.
 */
@Component
public class ShiftCalculator {

    private static final int MONEY_SCALE = 2;

    /** What the calculator needs to know about a shift. */
    public record Input(
            BigDecimal workingHours,
            Integer totalDelivered,
            String attendanceFlag,
            BigDecimal busyUtilization,
            boolean fuelAllowanceEligible,
            boolean carAllowanceEligible,
            boolean excluded,
            String captainType,
            boolean usesOwnVehicle) {

        public Input(BigDecimal workingHours, Integer totalDelivered, String attendanceFlag,
                     BigDecimal busyUtilization, boolean fuelAllowanceEligible,
                     boolean carAllowanceEligible, boolean excluded) {
            this(workingHours, totalDelivered, attendanceFlag, busyUtilization,
                    fuelAllowanceEligible, carAllowanceEligible, excluded, null, true);
        }
    }

    /** Everything the calculator decides, ready to be written onto a {@link Shift}. */
    public record Result(
            boolean attended,
            BigDecimal dailyWage,
            BigDecimal lateDeduction,
            int extraOrders,
            BigDecimal dailyIncentive,
            BigDecimal fuelAllowance,
            BigDecimal carAllowance,
            BigDecimal netShiftPay,
            boolean qualifiesFullRate,
            BigDecimal supplierRevenue,
            BigDecimal supplierExtraRevenue,
            BigDecimal shiftMargin,
            String calcNote) {}

    public Result compute(Input input, EffectiveSettings settings) {
        BigDecimal hours = input.workingHours() != null ? input.workingHours() : BigDecimal.ZERO;
        int delivered = input.totalDelivered() != null ? input.totalDelivered() : 0;

        if (input.excluded()) {
            return zeroResult("مستبعد يدويًا — لا يدخل في الحساب");
        }

        // A shift counts as attendance unless the supplier flagged it absent or the captain
        // barely showed up. Below that floor there is no daily wage and no deduction either.
        boolean flaggedAbsent = input.attendanceFlag() != null
                && input.attendanceFlag().toLowerCase().contains("absent");
        boolean belowFloor = hours.compareTo(settings.getMinAttendedHours()) < 0;

        if (flaggedAbsent || belowFloor) {
            String reason = flaggedAbsent
                    ? "غياب — مسجّل Absent من المورد"
                    : "غياب — ساعات العمل (" + trim(hours) + ") أقل من الحد الأدنى ("
                            + trim(settings.getMinAttendedHours()) + ")";
            return zeroResult(reason);
        }

        List<String> notes = new ArrayList<>();

        BigDecimal dailyWage = money(settings.getCaptainDailyRate());
        notes.add("يومية " + trim(dailyWage));

        BigDecimal lateDeduction = money(settings.lateDeductionFor(hours));
        if (lateDeduction.signum() > 0) {
            notes.add("− خصم تأخير " + trim(lateDeduction) + " (عمل " + trim(hours) + " ساعة)");
        }

        int extraOrders = Math.max(0, delivered - settings.getDailyOrderThreshold());
        BigDecimal extraOrderRate = settings.dailyExtraOrderRateFor(input.captainType(), input.usesOwnVehicle());
        BigDecimal dailyIncentive = money(extraOrderRate.multiply(BigDecimal.valueOf(extraOrders)));
        if (extraOrders > 0) {
            notes.add("+ حافز " + trim(dailyIncentive) + " (" + extraOrders + " طلب فوق "
                    + settings.getDailyOrderThreshold() + ")");
        }

        // The two allowances are decided independently — each has its own switch in the
        // settings, its own rate and its own hours threshold — so a period can pay one
        // without the other.
        boolean fuelDue = settings.isFuelAllowanceEnabled()
                && input.fuelAllowanceEligible()
                && hours.compareTo(settings.getFuelHoursThreshold()) >= 0;
        BigDecimal fuelAllowance = fuelDue ? money(settings.getFuelAllowanceDaily()) : zero();
        if (fuelDue) {
            notes.add("+ بدل بنزين " + trim(fuelAllowance));
        }

        boolean carDue = settings.isCarAllowanceEnabled()
                && input.carAllowanceEligible()
                && hours.compareTo(settings.getCarHoursThreshold()) >= 0;
        BigDecimal carAllowance = carDue ? money(settings.getCarAllowanceDaily()) : zero();
        if (carDue) {
            notes.add("+ بدل سيارة " + trim(carAllowance));
        }

        BigDecimal netShiftPay = money(dailyWage
                .subtract(lateDeduction)
                .add(dailyIncentive)
                .add(fuelAllowance)
                .add(carAllowance));
        notes.add("= " + trim(netShiftPay) + " ريال");

        // --- Supplier revenue side ---
        // A shift that meets the supplier's hours + utilization condition is paid at the
        // flat full-shift rate plus a per-order bonus above the threshold. Otherwise the
        // supplier pays per delivered order, which already covers the extra orders.
        boolean qualifies = input.busyUtilization() != null
                && hours.compareTo(settings.getHoursThreshold()) >= 0
                && input.busyUtilization().compareTo(settings.getUtilizationThreshold()) >= 0;

        BigDecimal supplierRevenue;
        BigDecimal supplierExtraRevenue;
        if (qualifies) {
            supplierRevenue = money(settings.getFullShiftRate());
            supplierExtraRevenue = money(settings.getSupplierExtraOrderRate()
                    .multiply(BigDecimal.valueOf(extraOrders)));
        } else {
            supplierRevenue = money(settings.getPerOrderRate().multiply(BigDecimal.valueOf(delivered)));
            supplierExtraRevenue = zero();
        }

        BigDecimal margin = money(supplierRevenue.add(supplierExtraRevenue).subtract(netShiftPay));

        return new Result(true, dailyWage, lateDeduction, extraOrders, dailyIncentive,
                fuelAllowance, carAllowance, netShiftPay, qualifies, supplierRevenue,
                supplierExtraRevenue, margin, String.join(" ", notes));
    }

    /** Runs {@link #compute} for a persisted shift and writes the result onto it. */
    public Shift apply(Shift shift, Captain captain, EffectiveSettings settings) {
        Result result = compute(new Input(
                shift.getWorkingHours(),
                shift.getTotalDelivered(),
                shift.getAttendanceFlag(),
                shift.getBusyUtilization(),
                captain != null && captain.isFuelAllowanceEligible(),
                // The car allowance compensates the vehicle, so it follows the own-vehicle flag.
                captain != null && captain.isUsesOwnVehicle(),
                shift.isExcluded(),
                captain != null ? captain.getCaptainType() : null,
                captain != null && captain.isUsesOwnVehicle()
        ), settings);

        shift.setAttended(result.attended());
        shift.setDailyWage(result.dailyWage());
        shift.setLateDeduction(result.lateDeduction());
        shift.setExtraOrders(result.extraOrders());
        shift.setDailyIncentive(result.dailyIncentive());
        shift.setFuelAllowance(result.fuelAllowance());
        shift.setCarAllowance(result.carAllowance());
        shift.setNetShiftPay(result.netShiftPay());
        shift.setQualifiesFullRate(result.qualifiesFullRate());
        shift.setSupplierRevenue(result.supplierRevenue());
        shift.setSupplierExtraRevenue(result.supplierExtraRevenue());
        shift.setShiftMargin(result.shiftMargin());
        shift.setCalcNote(truncate(result.calcNote()));
        shift.setSettingsVersion(settings.getVersion());

        // Legacy V8 revenue columns, kept in step so older reads stay consistent.
        shift.setShiftIncome(result.supplierRevenue().add(result.supplierExtraRevenue()));
        shift.setNetShiftValue(result.netShiftPay());

        return shift;
    }

    private Result zeroResult(String note) {
        return new Result(false, zero(), zero(), 0, zero(), zero(), zero(), zero(),
                false, zero(), zero(), zero(), note);
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Drops trailing zeros so notes read "57" and "10.5" rather than "57.00". */
    private static String trim(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String truncate(String note) {
        return note != null && note.length() > 500 ? note.substring(0, 500) : note;
    }
}
