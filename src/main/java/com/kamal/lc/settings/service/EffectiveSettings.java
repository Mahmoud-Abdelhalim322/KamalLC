package com.kamal.lc.settings.service;

import com.kamal.lc.common.exception.BusinessException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The full set of rates in force on one date, resolved once and passed into the
 * calculators. Holding a snapshot (rather than looking keys up one by one) is what
 * makes a shift or a payroll line reproducible: everything it used came from here.
 */
@Getter
public class EffectiveSettings {

    // --- Captain pay ---
    private final BigDecimal captainDailyRate;
    private final int standardWorkDays;
    private final BigDecimal minAttendedHours;

    /** Late-hour ladder, sorted by threshold descending — first match wins. */
    private final List<LateTier> lateTiers;

    // --- Daily order incentive ---
    private final int dailyOrderThreshold;
    private final BigDecimal dailyExtraOrderRate;
    private final BigDecimal dailyExtraOrderRateCompanyCar;
    private final BigDecimal dailyExtraOrderRateOwnCar;

    // --- Fuel allowance ---
    private final boolean fuelAllowanceEnabled;
    private final BigDecimal fuelAllowanceDaily;
    private final BigDecimal fuelHoursThreshold;

    // --- Car allowance: the vehicle itself, switched and priced apart from the fuel ---
    private final boolean carAllowanceEnabled;
    private final BigDecimal carAllowanceDaily;
    private final BigDecimal carHoursThreshold;

    // --- Monthly target ---
    private final int monthlyOrderTarget;
    private final BigDecimal targetAchievementBonus;
    private final BigDecimal postTargetRateEmployee;
    private final BigDecimal postTargetRateFreelanceCompanyCar;
    private final BigDecimal postTargetRateFreelanceOwnCar;

    // --- Supplier revenue side ---
    private final BigDecimal fullShiftRate;
    private final BigDecimal hoursThreshold;
    private final BigDecimal utilizationThreshold;
    private final BigDecimal perOrderRate;
    private final BigDecimal supplierExtraOrderRate;

    /** effective_from of the newest row in this snapshot — stamped onto computed shifts. */
    private final LocalDate version;

    /** The raw key/value pairs behind this snapshot, stored with an approved payroll run. */
    private final Map<String, String> rawValues;

    public record LateTier(BigDecimal minHours, BigDecimal deduction) {}

    private EffectiveSettings(Map<String, String> values, LocalDate version) {
        this.captainDailyRate = decimal(values, "CAPTAIN_DAILY_RATE");
        this.standardWorkDays = integer(values, "STANDARD_WORK_DAYS");
        this.minAttendedHours = decimal(values, "MIN_ATTENDED_HOURS");
        this.lateTiers = readLadder(values);

        this.dailyOrderThreshold = integer(values, "DAILY_ORDER_THRESHOLD");
        this.dailyExtraOrderRate = optionalDecimal(values, "DAILY_EXTRA_ORDER_RATE", new BigDecimal("5"));
        this.dailyExtraOrderRateCompanyCar = optionalDecimal(values, "DAILY_EXTRA_ORDER_RATE_COMPANY_CAR",
                this.dailyExtraOrderRate != null ? this.dailyExtraOrderRate : new BigDecimal("3"));
        this.dailyExtraOrderRateOwnCar = optionalDecimal(values, "DAILY_EXTRA_ORDER_RATE_OWN_CAR",
                this.dailyExtraOrderRate != null ? this.dailyExtraOrderRate : new BigDecimal("5"));

        this.fuelAllowanceDaily = decimal(values, "FUEL_ALLOWANCE_DAILY");
        this.fuelHoursThreshold = decimal(values, "FUEL_HOURS_THRESHOLD");

        // The allowance switches and the car keys arrived with V19. Dates before that seed
        // must still resolve, so they fall back to what was in force then: fuel paid, car not.
        this.fuelAllowanceEnabled = flag(values, "FUEL_ALLOWANCE_ENABLED", true);
        this.carAllowanceEnabled = flag(values, "CAR_ALLOWANCE_ENABLED", false);
        this.carAllowanceDaily = optionalDecimal(values, "CAR_ALLOWANCE_DAILY", BigDecimal.ZERO);
        this.carHoursThreshold = optionalDecimal(values, "CAR_ALLOWANCE_HOURS_THRESHOLD",
                this.fuelHoursThreshold);

        this.monthlyOrderTarget = integer(values, "MONTHLY_ORDER_TARGET");
        this.targetAchievementBonus = decimal(values, "TARGET_ACHIEVEMENT_BONUS");
        this.postTargetRateEmployee = decimal(values, "POST_TARGET_RATE_EMPLOYEE");
        this.postTargetRateFreelanceCompanyCar = decimal(values, "POST_TARGET_RATE_FREELANCE_COMPANY_CAR");
        this.postTargetRateFreelanceOwnCar = decimal(values, "POST_TARGET_RATE_FREELANCE_OWN_CAR");

        this.fullShiftRate = decimal(values, "FULL_SHIFT_RATE");
        this.hoursThreshold = decimal(values, "HOURS_THRESHOLD");
        this.utilizationThreshold = decimal(values, "UTILIZATION_THRESHOLD");
        this.perOrderRate = decimal(values, "PER_ORDER_RATE");
        this.supplierExtraOrderRate = decimal(values, "SUPPLIER_EXTRA_ORDER_RATE");

        this.version = version;
        this.rawValues = Map.copyOf(values);
    }

    public static EffectiveSettings of(Map<String, String> values, LocalDate version) {
        return new EffectiveSettings(values, version);
    }

    /**
     * The daily per-extra-order incentive rate a captain earns.
     * Employee captains or freelancers using company car earn the company car rate (3 SAR),
     * while freelancers using their own vehicle earn the own car rate (5 SAR).
     */
    public BigDecimal dailyExtraOrderRateFor(String captainType, boolean usesOwnVehicle) {
        if ("EMPLOYEE".equalsIgnoreCase(captainType)) {
            return dailyExtraOrderRateCompanyCar;
        }
        return usesOwnVehicle ? dailyExtraOrderRateOwnCar : dailyExtraOrderRateCompanyCar;
    }

    /**
     * The per-order rate a captain earns past the monthly target. Freelancers driving
     * their own vehicle carry the fuel cost, so they earn the higher rate.
     */
    public BigDecimal postTargetRateFor(String captainType, boolean usesOwnVehicle) {
        if (!"FREELANCE".equalsIgnoreCase(captainType)) {
            return postTargetRateEmployee;
        }
        return usesOwnVehicle ? postTargetRateFreelanceOwnCar : postTargetRateFreelanceCompanyCar;
    }

    /** The deduction for a shift of {@code hours}; the ladder's last tier is the catch-all. */
    public BigDecimal lateDeductionFor(BigDecimal hours) {
        BigDecimal actual = hours != null ? hours : BigDecimal.ZERO;
        for (LateTier tier : lateTiers) {
            if (actual.compareTo(tier.minHours()) >= 0) {
                return tier.deduction();
            }
        }
        // No tier matched (ladder misconfigured without a 0-hour floor) — deduct the full day.
        return captainDailyRate;
    }

    /**
     * Reads LATE_TIER_MIN_HOURS_n / LATE_TIER_DEDUCTION_n pairs into a descending ladder.
     * Numbering is open-ended so a tier can be inserted from the settings screen.
     */
    private static List<LateTier> readLadder(Map<String, String> values) {
        List<LateTier> tiers = new ArrayList<>();
        for (int n = 1; n <= 20; n++) {
            String hours = values.get("LATE_TIER_MIN_HOURS_" + n);
            String deduction = values.get("LATE_TIER_DEDUCTION_" + n);
            if (hours == null || deduction == null) continue;
            tiers.add(new LateTier(new BigDecimal(hours.trim()), new BigDecimal(deduction.trim())));
        }
        if (tiers.isEmpty()) {
            throw new BusinessException("CONFIG_ERROR", "سلّم خصم التأخير غير مضبوط في الإعدادات");
        }
        tiers.sort(Comparator.comparing(LateTier::minHours).reversed());
        return List.copyOf(tiers);
    }

    private static BigDecimal decimal(Map<String, String> values, String key) {
        return new BigDecimal(require(values, key));
    }

    private static BigDecimal optionalDecimal(Map<String, String> values, String key, BigDecimal fallback) {
        String raw = values.get(key);
        return (raw == null || raw.isBlank()) ? fallback : new BigDecimal(raw.trim());
    }

    /** A BOOLEAN setting; anything but "true" reads as off, a missing key as {@code fallback}. */
    private static boolean flag(Map<String, String> values, String key, boolean fallback) {
        String raw = values.get(key);
        return (raw == null || raw.isBlank()) ? fallback : Boolean.parseBoolean(raw.trim());
    }

    private static int integer(Map<String, String> values, String key) {
        return new BigDecimal(require(values, key)).intValueExact();
    }

    private static String require(Map<String, String> values, String key) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("CONFIG_ERROR", "الإعداد المطلوب غير موجود: " + key);
        }
        return raw.trim();
    }
}
