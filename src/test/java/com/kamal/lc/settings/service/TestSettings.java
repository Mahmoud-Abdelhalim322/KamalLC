package com.kamal.lc.settings.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * The seeded rates as a plain map, so calculator tests run without a database.
 * Keep in step with {@code V11__reseed_settings_new_rules.sql} and {@code V19__car_allowance.sql}.
 *
 * <p>Both allowance switches are on here so the rules under test are actually exercised;
 * V19 ships the car allowance off until it is enabled from the settings screen.
 */
public final class TestSettings {

    private TestSettings() {}

    public static Map<String, String> values() {
        Map<String, String> m = new HashMap<>();
        m.put("CAPTAIN_DAILY_RATE", "57");
        m.put("STANDARD_WORK_DAYS", "26");
        m.put("MIN_ATTENDED_HOURS", "1");

        m.put("LATE_TIER_MIN_HOURS_1", "11");
        m.put("LATE_TIER_DEDUCTION_1", "0");
        m.put("LATE_TIER_MIN_HOURS_2", "10");
        m.put("LATE_TIER_DEDUCTION_2", "25");
        m.put("LATE_TIER_MIN_HOURS_3", "8");
        m.put("LATE_TIER_DEDUCTION_3", "35");
        m.put("LATE_TIER_MIN_HOURS_4", "7");
        m.put("LATE_TIER_DEDUCTION_4", "45");
        m.put("LATE_TIER_MIN_HOURS_5", "0");
        m.put("LATE_TIER_DEDUCTION_5", "57");

        m.put("DAILY_ORDER_THRESHOLD", "18");
        m.put("DAILY_EXTRA_ORDER_RATE", "5");
        m.put("DAILY_EXTRA_ORDER_RATE_COMPANY_CAR", "3");
        m.put("DAILY_EXTRA_ORDER_RATE_OWN_CAR", "5");
        m.put("SUPPLIER_EXTRA_ORDER_RATE", "10");

        m.put("FUEL_ALLOWANCE_ENABLED", "true");
        m.put("FUEL_ALLOWANCE_DAILY", "35");
        m.put("FUEL_HOURS_THRESHOLD", "11");

        m.put("CAR_ALLOWANCE_ENABLED", "true");
        m.put("CAR_ALLOWANCE_DAILY", "31");
        m.put("CAR_ALLOWANCE_HOURS_THRESHOLD", "11");

        m.put("MONTHLY_ORDER_TARGET", "350");
        m.put("TARGET_ACHIEVEMENT_BONUS", "1000");
        m.put("POST_TARGET_RATE_EMPLOYEE", "3");
        m.put("POST_TARGET_RATE_FREELANCE_COMPANY_CAR", "3");
        m.put("POST_TARGET_RATE_FREELANCE_OWN_CAR", "5");

        m.put("FULL_SHIFT_RATE", "230");
        m.put("HOURS_THRESHOLD", "11");
        m.put("UTILIZATION_THRESHOLD", "0.75");
        m.put("PER_ORDER_RATE", "10");
        return m;
    }

    public static EffectiveSettings effective() {
        return EffectiveSettings.of(values(), LocalDate.of(2026, 1, 1));
    }
}
