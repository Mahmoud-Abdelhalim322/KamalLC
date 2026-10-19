package com.kamal.lc.shift.service;

import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.settings.service.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-shift pay rules, checked against rows taken from Kamal's own
 * "تقارير الشهري للكباتن" sheet — that sheet is the agreed reference for the ladder.
 */
class ShiftCalculatorTest {

    private final ShiftCalculator calculator = new ShiftCalculator();
    private final EffectiveSettings settings = TestSettings.effective();

    private ShiftCalculator.Input shift(String hours, int delivered) {
        return new ShiftCalculator.Input(new BigDecimal(hours), delivered,
                "Working more 8 Hour", new BigDecimal("0.80"), false, false, false, "FREELANCE", true);
    }

    @Nested
    @DisplayName("late-hour deduction ladder")
    class LateDeduction {

        @ParameterizedTest(name = "{0} hours worked → {1} SAR deducted")
        @CsvSource({
                // Rows lifted from the reference sheet
                "10.21, 25",   // AD3 in the sheet
                "10.64, 25",   // AD4
                "9.85,  35",   // AD6
                "5.30,  57",   // AD2 — full day lost
                "11.01,  0",   // AD7 — met the shift, nothing deducted
                // Boundaries: the ladder is continuous, so there are no gaps to fall into
                "11.00,  0",
                "10.99, 25",
                "10.00, 25",
                "9.99,  35",
                "8.00,  35",
                "7.99,  45",
                "7.00,  45",
                "6.99,  57",
                "1.00,  57",
        })
        void followsTheAgreedLadder(String hours, int expected) {
            ShiftCalculator.Result result = calculator.compute(shift(hours, 10), settings);

            assertThat(result.attended()).isTrue();
            assertThat(result.lateDeduction()).isEqualByComparingTo(BigDecimal.valueOf(expected));
        }

        @Test
        @DisplayName("a full-length shift keeps the whole daily rate")
        void fullShiftKeepsFullWage() {
            ShiftCalculator.Result result = calculator.compute(shift("11.48", 12), settings);

            assertThat(result.dailyWage()).isEqualByComparingTo("57");
            assertThat(result.netShiftPay()).isEqualByComparingTo("57");
        }

        @Test
        @DisplayName("a shift short enough to lose the full rate nets nothing")
        void shortShiftNetsZero() {
            ShiftCalculator.Result result = calculator.compute(shift("5.30", 7), settings);

            assertThat(result.dailyWage()).isEqualByComparingTo("57");
            assertThat(result.lateDeduction()).isEqualByComparingTo("57");
            assertThat(result.netShiftPay()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("daily order incentive")
    class DailyIncentive {

        @ParameterizedTest(name = "{0} orders → {1} extra orders → {2} SAR")
        @CsvSource({
                "18, 0,  0",
                "19, 1,  5",
                "20, 2, 10",
                "22, 4, 20",
                "0,  0,  0",
        })
        void paysFiveForEachOrderAboveEighteen(int delivered, int extraOrders, int incentive) {
            ShiftCalculator.Result result = calculator.compute(shift("11.50", delivered), settings);

            assertThat(result.extraOrders()).isEqualTo(extraOrders);
            assertThat(result.dailyIncentive()).isEqualByComparingTo(BigDecimal.valueOf(incentive));
        }

        @Test
        @DisplayName("pays 3 SAR per extra order for company car / employee")
        void paysThreeSARForCompanyCarOrEmployee() {
            ShiftCalculator.Input companyCarInput = new ShiftCalculator.Input(
                    new BigDecimal("11.50"), 20, "Working more 8 Hour",
                    new BigDecimal("0.80"), false, false, false, "EMPLOYEE", false);

            ShiftCalculator.Result result = calculator.compute(companyCarInput, settings);

            assertThat(result.extraOrders()).isEqualTo(2);
            assertThat(result.dailyIncentive()).isEqualByComparingTo("6"); // 2 × 3 SAR
        }

        @Test
        @DisplayName("pays 5 SAR per extra order for freelance captain using own car")
        void paysFiveSARForFreelanceOwnCar() {
            ShiftCalculator.Input ownCarInput = new ShiftCalculator.Input(
                    new BigDecimal("11.50"), 20, "Working more 8 Hour",
                    new BigDecimal("0.80"), false, true, false, "FREELANCE", true);

            ShiftCalculator.Result result = calculator.compute(ownCarInput, settings);

            assertThat(result.extraOrders()).isEqualTo(2);
            assertThat(result.dailyIncentive()).isEqualByComparingTo("10"); // 2 × 5 SAR
        }

        @Test
        @DisplayName("still paid on a shift that lost hours")
        void appliesEvenWhenDeducted() {
            ShiftCalculator.Result result = calculator.compute(shift("9.50", 22), settings);

            assertThat(result.lateDeduction()).isEqualByComparingTo("35");
            assertThat(result.dailyIncentive()).isEqualByComparingTo("20");
            assertThat(result.netShiftPay()).isEqualByComparingTo("42"); // 57 − 35 + 20
        }
    }

    @Nested
    @DisplayName("fuel allowance")
    class FuelAllowance {

        private ShiftCalculator.Result computeFor(String hours, boolean eligible) {
            return calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal(hours), 10, "Working more 8 Hour",
                    new BigDecimal("0.80"), eligible, false, false), settings);
        }

        @Test
        @DisplayName("fuel allowance is zero because automatic calculation is disabled")
        void paidWhenEligibleAndElevenHoursReached() {
            assertThat(computeFor("11.00", true).fuelAllowance()).isEqualByComparingTo("0");
        }

        @Test
        void notPaidBelowElevenHours() {
            assertThat(computeFor("10.99", true).fuelAllowance()).isEqualByComparingTo("0");
        }

        @Test
        void notPaidWhenTheCaptainIsNotEligible() {
            assertThat(computeFor("11.50", false).fuelAllowance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("adds on top of wage and incentive (fuel is 0)")
        void addsToTheShiftTotal() {
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("11.48"), 22, "Working more 8 Hour",
                    new BigDecimal("0.80"), true, false, false), settings);

            // 57 − 0 + 20 + 0 = 77
            assertThat(result.netShiftPay()).isEqualByComparingTo("77");
        }
    }

    @Nested
    @DisplayName("car allowance")
    class CarAllowance {

        private ShiftCalculator.Result computeFor(String hours, boolean ownVehicle) {
            return calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal(hours), 10, "Working more 8 Hour",
                    new BigDecimal("0.80"), false, ownVehicle, false), settings);
        }

        @Test
        @DisplayName("31 SAR once the shift reaches eleven hours")
        void paidOnOwnVehicleWhenElevenHoursReached() {
            assertThat(computeFor("11.00", true).carAllowance()).isEqualByComparingTo("31");
        }

        @Test
        void notPaidBelowElevenHours() {
            assertThat(computeFor("10.99", true).carAllowance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("a company car earns nothing — the allowance compensates the captain's own vehicle")
        void notPaidOnACompanyCar() {
            assertThat(computeFor("11.50", false).carAllowance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("the switch in the settings turns it off on its own")
        void notPaidWhileTheSwitchIsOff() {
            EffectiveSettings carOff = settingsWith("CAR_ALLOWANCE_ENABLED", "false");

            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("11.50"), 10, "Working more 8 Hour",
                    new BigDecimal("0.80"), true, true, false), carOff);

            assertThat(result.carAllowance()).isEqualByComparingTo("0");
            assertThat(result.fuelAllowance()).isEqualByComparingTo("0"); // fuel is disabled
        }

        @Test
        @DisplayName("the fuel switch turns off the fuel alone, leaving the car allowance paid")
        void fuelCanBeSwitchedOffOnItsOwn() {
            EffectiveSettings fuelOff = settingsWith("FUEL_ALLOWANCE_ENABLED", "false");

            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("11.50"), 10, "Working more 8 Hour",
                    new BigDecimal("0.80"), true, true, false), fuelOff);

            assertThat(result.fuelAllowance()).isEqualByComparingTo("0");
            assertThat(result.carAllowance()).isEqualByComparingTo("31");
        }

        @Test
        @DisplayName("both allowances stack on top of wage and incentive")
        void bothAddToTheShiftTotal() {
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("11.48"), 22, "Working more 8 Hour",
                    new BigDecimal("0.80"), true, true, false), settings);

            // 57 − 0 + 20 + 0 + 31 = 108
            assertThat(result.netShiftPay()).isEqualByComparingTo("108");
        }
    }

    /** The seeded rates with one key overridden — how a switch is flipped in these tests. */
    private static EffectiveSettings settingsWith(String key, String value) {
        java.util.Map<String, String> values = TestSettings.values();
        values.put(key, value);
        return EffectiveSettings.of(values, java.time.LocalDate.of(2026, 1, 1));
    }

    @Nested
    @DisplayName("attendance")
    class Attendance {

        @Test
        @DisplayName("a supplier 'Absent' flag zeroes the shift outright")
        void absentFlagZeroesEverything() {
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("0"), 0, "Absent", null, true, true, false), settings);

            assertThat(result.attended()).isFalse();
            assertThat(result.dailyWage()).isEqualByComparingTo("0");
            assertThat(result.lateDeduction()).isEqualByComparingTo("0");
            assertThat(result.netShiftPay()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("barely showing up is an absence, not a deducted day")
        void belowMinimumHoursIsAbsence() {
            // The 0.27-hour row in the reference sheet was paid nothing at all,
            // unlike the 5.3-hour row which was paid 57 and then deducted 57.
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("0.27"), 0, "Working Less 8 Hour", null, false, false, false), settings);

            assertThat(result.attended()).isFalse();
            assertThat(result.dailyWage()).isEqualByComparingTo("0");
            assertThat(result.lateDeduction()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("an excluded shift contributes nothing but says why")
        void excludedShiftIsSkipped() {
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("11.50"), 25, "Working more 8 Hour",
                    new BigDecimal("0.90"), true, true, true), settings);

            assertThat(result.attended()).isFalse();
            assertThat(result.netShiftPay()).isEqualByComparingTo("0");
            assertThat(result.calcNote()).contains("مستبعد");
        }
    }

    @Nested
    @DisplayName("supplier revenue")
    class SupplierRevenue {

        @Test
        @DisplayName("a qualifying shift earns the flat rate plus the per-extra-order bonus")
        void qualifyingShiftEarnsFullRate() {
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("11.01"), 22, "Working more 8 Hour",
                    new BigDecimal("0.842"), false, false, false), settings);

            assertThat(result.qualifiesFullRate()).isTrue();
            assertThat(result.supplierRevenue()).isEqualByComparingTo("230");
            assertThat(result.supplierExtraRevenue()).isEqualByComparingTo("40"); // 4 × 10
        }

        @ParameterizedTest(name = "not qualifying, {0} orders → {1} SAR revenue")
        @CsvSource({"7, 70", "15, 150", "10, 100"})
        void nonQualifyingShiftIsPaidPerOrder(int delivered, int revenue) {
            // Sheet rows Y2/Y3/Y6 — short shifts are paid per delivered order instead.
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("9.85"), delivered, "Working Less 8 Hour",
                    new BigDecimal("0.68"), false, false, false), settings);

            assertThat(result.qualifiesFullRate()).isFalse();
            assertThat(result.supplierRevenue()).isEqualByComparingTo(BigDecimal.valueOf(revenue));
            // Per-order pay already covers the extra orders, so no bonus on top.
            assertThat(result.supplierExtraRevenue()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("low utilisation disqualifies even a full-length shift")
        void lowUtilizationDisqualifies() {
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("11.03"), 8, "Working more 8 Hour",
                    new BigDecimal("0.4385"), false, false, false), settings);

            assertThat(result.qualifiesFullRate()).isFalse();
            assertThat(result.supplierRevenue()).isEqualByComparingTo("80");
        }

        @Test
        @DisplayName("margin is what the supplier pays minus what the captain earns")
        void marginIsRevenueMinusPay() {
            ShiftCalculator.Result result = calculator.compute(new ShiftCalculator.Input(
                    new BigDecimal("11.01"), 22, "Working more 8 Hour",
                    new BigDecimal("0.842"), false, false, false), settings);

            // (230 + 40) − (57 + 20)
            assertThat(result.shiftMargin()).isEqualByComparingTo("193");
        }
    }
}
