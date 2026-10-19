package com.kamal.lc.payroll.service;

import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.settings.service.TestSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The monthly pay rules, anchored on real rows from "مسيرات رواتب شهر". */
class PayrollCalculatorTest {

    private final PayrollCalculator calculator = new PayrollCalculator();
    private final EffectiveSettings settings = TestSettings.effective();

    private PayrollCalculator.Input input(String type, boolean ownVehicle, int shifts, int orders) {
        return new PayrollCalculator.Input(type, ownVehicle, shifts, orders,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Nested
    @DisplayName("base pay")
    class BasePay {

        @ParameterizedTest(name = "{0} attended shifts → {1} SAR")
        @CsvSource({
                "30, 1710",   // KSC0020 in the payroll sheet
                "27, 1539",   // KSC0001
                "26, 1482",
                "23, 1311",   // KSC0013
                "0,     0",
        })
        @DisplayName("is the daily rate for every attended shift, with no monthly floor")
        void isDailyRateTimesShifts(int shifts, int expected) {
            PayrollCalculator.Result result = calculator.compute(
                    input("EMPLOYEE", false, shifts, 0), settings);

            assertThat(result.basePay()).isEqualByComparingTo(BigDecimal.valueOf(expected));
        }

        @Test
        @DisplayName("working past the standard month simply keeps earning the daily rate")
        void overtimeShiftsEarnTheSameRate() {
            BigDecimal at26 = calculator.compute(input("EMPLOYEE", false, 26, 0), settings).basePay();
            BigDecimal at30 = calculator.compute(input("EMPLOYEE", false, 30, 0), settings).basePay();

            assertThat(at30.subtract(at26)).isEqualByComparingTo("228"); // 4 × 57
        }
    }

    @Nested
    @DisplayName("monthly order target")
    class Target {

        @Test
        @DisplayName("below the target neither the bonus nor the per-order rate applies")
        void belowTargetPaysNothingExtra() {
            PayrollCalculator.Result result = calculator.compute(
                    input("EMPLOYEE", false, 20, 349), settings);

            assertThat(result.targetBonus()).isEqualByComparingTo("0");
            assertThat(result.extraOrderPay()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("hitting the target exactly pays the bonus and nothing per order")
        void atTargetPaysBonusOnly() {
            PayrollCalculator.Result result = calculator.compute(
                    input("EMPLOYEE", false, 26, 350), settings);

            assertThat(result.targetBonus()).isEqualByComparingTo("1000");
            assertThat(result.extraOrderPay()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("a sponsored captain earns 3 SAR for each order past the target")
        void employeeEarnsThreePerExtraOrder() {
            // KSC0001: 27 shifts, 421 orders
            PayrollCalculator.Result result = calculator.compute(
                    input("EMPLOYEE", false, 27, 421), settings);

            assertThat(result.basePay()).isEqualByComparingTo("1539");
            assertThat(result.targetBonus()).isEqualByComparingTo("1000");
            assertThat(result.extraOrderPay()).isEqualByComparingTo("213"); // 71 × 3
        }

        @Test
        @DisplayName("a freelancer on a company car earns the same 3 SAR")
        void freelanceOnCompanyCarEarnsThree() {
            PayrollCalculator.Result result = calculator.compute(
                    input("FREELANCE", false, 27, 421), settings);

            assertThat(result.extraOrderPay()).isEqualByComparingTo("213"); // 71 × 3
        }

        @Test
        @DisplayName("a freelancer on their own car earns 5 SAR, carrying the vehicle cost")
        void freelanceOnOwnCarEarnsFive() {
            PayrollCalculator.Result result = calculator.compute(
                    input("FREELANCE", true, 27, 421), settings);

            assertThat(result.extraOrderPay()).isEqualByComparingTo("355"); // 71 × 5
        }
    }

    @Nested
    @DisplayName("totals")
    class Totals {

        @Test
        @DisplayName("gross adds the incentives and subtracts the deductions")
        void grossCombinesEveryComponent() {
            PayrollCalculator.Result result = calculator.compute(new PayrollCalculator.Input(
                    "EMPLOYEE", false, 27, 421,
                    new BigDecimal("120"),   // late deductions
                    new BigDecimal("50"),    // supplier deductions
                    new BigDecimal("240"),   // daily incentives
                    new BigDecimal("350"),   // fuel allowances
                    new BigDecimal("310"),   // car allowances
                    BigDecimal.ZERO,
                    BigDecimal.ZERO), settings);

            // 1539 − 120 − 50 + 240 + 0 + 310 + 1000 + 213 = 3132
            assertThat(result.gross()).isEqualByComparingTo("3132");
        }

        @Test
        @DisplayName("the car allowance is added apart from the fuel allowance")
        void carAllowanceIsItsOwnComponent() {
            PayrollCalculator.Result result = calculator.compute(new PayrollCalculator.Input(
                    "FREELANCE", true, 26, 300,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    new BigDecimal("350"),   // fuel allowances
                    new BigDecimal("310"),   // car allowances
                    BigDecimal.ZERO,
                    BigDecimal.ZERO), settings);

            assertThat(result.fuelAllowances()).isEqualByComparingTo("0");
            assertThat(result.carAllowances()).isEqualByComparingTo("310");
            // 1482 + 0 + 310 = 1792, below target so nothing else applies
            assertThat(result.gross()).isEqualByComparingTo("1792");
        }

        @Test
        @DisplayName("the daily incentive stacks on the target bonus rather than replacing it")
        void dailyIncentivesStackWithTheTargetBonus() {
            PayrollCalculator.Result withIncentives = calculator.compute(new PayrollCalculator.Input(
                    "EMPLOYEE", false, 27, 421, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("240"),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), settings);
            PayrollCalculator.Result without = calculator.compute(
                    input("EMPLOYEE", false, 27, 421), settings);

            assertThat(withIncentives.targetBonus()).isEqualByComparingTo(without.targetBonus());
            assertThat(withIncentives.gross().subtract(without.gross())).isEqualByComparingTo("240");
        }

        @Test
        @DisplayName("net applies the manual adjustments, which may be negative overall")
        void netAppliesAdjustments() {
            PayrollCalculator.Result result = calculator.compute(new PayrollCalculator.Input(
                    "EMPLOYEE", false, 27, 421,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, new BigDecimal("118")), settings); // +200 housing +135 reward −217 loan

            assertThat(result.net()).isEqualByComparingTo(result.gross().add(new BigDecimal("118")));
        }
    }
}
