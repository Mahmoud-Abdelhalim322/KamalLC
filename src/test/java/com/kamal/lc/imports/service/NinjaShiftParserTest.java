package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.NinjaShiftRow;
import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.settings.service.TestSettings;
import com.kamal.lc.shift.service.ShiftCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses the real supplier export shipped with the repo, so a change to the file's shape
 * shows up here rather than in production.
 */
class NinjaShiftParserTest {

    private static List<NinjaShiftRow> rows;

    private final ShiftCalculator calculator = new ShiftCalculator();
    private final EffectiveSettings settings = TestSettings.effective();

    @BeforeAll
    static void parseSampleFile() throws Exception {
        byte[] content = new ClassPathResource("ninja_sample.xlsx").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile(
                "file", "ninja_sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        rows = new NinjaShiftParser().parse(file);
    }

    @Test
    @DisplayName("reads every data row in the file")
    void readsAllRows() {
        assertThat(rows).hasSize(141);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getExternalShiftId()).isNotBlank();
            assertThat(row.getNinjaCaptainId()).isNotBlank();
            assertThat(row.getShiftDate()).isNotNull();
        });
    }

    @Test
    @DisplayName("resolves Excel serial timestamps into KSA calendar dates")
    void resolvesShiftDates() {
        Set<LocalDate> dates = rows.stream()
                .map(NinjaShiftRow::getShiftDate)
                .collect(Collectors.toSet());

        // Serials 46222/46223/46224, each starting at 18:00 KSA.
        assertThat(dates).containsExactlyInAnyOrder(
                LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 21));
    }

    @Test
    @DisplayName("a shift id is shared by everyone rostered on it, so rows key on the pair")
    void shiftIdAloneIsNotUnique() {
        long distinctShiftIds = rows.stream().map(NinjaShiftRow::getExternalShiftId).distinct().count();
        long distinctPairs = rows.stream()
                .map(r -> r.getExternalShiftId() + "|" + r.getNinjaCaptainId())
                .distinct().count();

        assertThat(distinctShiftIds).isLessThan(rows.size());
        assertThat(distinctPairs).isEqualTo(rows.size());
    }

    @Test
    @DisplayName("maps every column of the first row")
    void mapsAllColumns() {
        NinjaShiftRow row = find("3043452", "95516");

        assertThat(row.getSupplierExternalId()).isEqualTo("2399");
        assertThat(row.getSupplierName()).isEqualTo("Kamal_Food_ftr(car)");
        assertThat(row.getCity()).isEqualTo("Riyadh");
        assertThat(row.getShiftName()).isEqualTo("Riyadh West");
        assertThat(row.getNinjaCaptainId()).isEqualTo("95516");
        assertThat(row.getNinjaCaptainName()).isEqualTo("Alaelden omer");
        assertThat(row.getAttendanceFlag()).isEqualTo("Working more 8 Hour");
        assertThat(row.getTotalDelivered()).isEqualTo(8);
        assertThat(row.getWorkingHours()).isCloseTo(new BigDecimal("11.0331"), withinCents());
        assertThat(row.getBusyUtilization()).isCloseTo(new BigDecimal("0.4385"), withinCents());
        assertThat(row.getHourlyUtr()).isCloseTo(new BigDecimal("0.7251"), withinCents());
        assertThat(row.getScheduledStart()).isNotNull();
        assertThat(row.getScheduledEnd()).isNotNull();
        assertThat(row.getActualCheckin()).isNotNull();
    }

    @Test
    @DisplayName("the six absent rows are priced as absences, not deducted days")
    void absentRowsEarnNothing() {
        List<NinjaShiftRow> absent = rows.stream()
                .filter(r -> "Absent".equals(r.getAttendanceFlag()))
                .toList();

        assertThat(absent).hasSize(6);
        assertThat(absent).allSatisfy(row -> {
            ShiftCalculator.Result result = calculator.compute(toInput(row, false), settings);
            assertThat(result.attended()).isFalse();
            assertThat(result.netShiftPay()).isEqualByComparingTo("0");
            assertThat(result.lateDeduction()).isEqualByComparingTo("0");
        });
    }

    @Test
    @DisplayName("the file's three high-order shifts earn the daily incentive")
    void highOrderShiftsEarnIncentive() {
        // shift 3042361 / captain 223618: 11.55 h, 20 orders → no deduction, 2 extra orders × 5
        NinjaShiftRow row = find("3042361", "223618");

        ShiftCalculator.Result result = calculator.compute(toInput(row, true), settings);

        assertThat(result.attended()).isTrue();
        assertThat(result.lateDeduction()).isEqualByComparingTo("0");
        assertThat(result.extraOrders()).isEqualTo(2);
        assertThat(result.dailyIncentive()).isEqualByComparingTo("10");
        assertThat(result.fuelAllowance()).isEqualByComparingTo("35");
        assertThat(result.netShiftPay()).isEqualByComparingTo("102"); // 57 + 10 + 35
    }

    @Test
    @DisplayName("a shift ten minutes short of eleven hours costs the first tier")
    void slightlyShortShiftLosesFirstTier() {
        // shift 3042358 / captain 251134: 10.71 h, 19 orders
        NinjaShiftRow row = find("3042358", "251134");

        ShiftCalculator.Result result = calculator.compute(toInput(row, true), settings);

        assertThat(result.lateDeduction()).isEqualByComparingTo("25");
        assertThat(result.dailyIncentive()).isEqualByComparingTo("5");
        assertThat(result.fuelAllowance()).isEqualByComparingTo("0"); // under 11 hours
        assertThat(result.netShiftPay()).isEqualByComparingTo("37");  // 57 − 25 + 5
    }

    /** Looks a row up by its natural key — the pair, never the shift id alone. */
    private NinjaShiftRow find(String shiftId, String captainId) {
        return rows.stream()
                .filter(r -> shiftId.equals(r.getExternalShiftId())
                        && captainId.equals(r.getNinjaCaptainId()))
                .findFirst().orElseThrow();
    }

    private ShiftCalculator.Input toInput(NinjaShiftRow row, boolean fuelEligible) {
        return new ShiftCalculator.Input(row.getWorkingHours(), row.getTotalDelivered(),
                row.getAttendanceFlag(), row.getBusyUtilization(), fuelEligible, false, false);
    }

    private static org.assertj.core.data.Offset<BigDecimal> withinCents() {
        return org.assertj.core.data.Offset.offset(new BigDecimal("0.0001"));
    }
}
