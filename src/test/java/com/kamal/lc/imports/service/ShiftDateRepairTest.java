package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.NinjaShiftRow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the day/month repair against the real export that exposed it: a full June roster
 * where every shift on days 1–12 had been rewritten as the 6th of another month, and one
 * end date had been rewritten as 7 January. Thirty shifts reached the database as
 * eighteen — twelve scattered across the year, and one destroyed outright by a shift id
 * the supplier had reused.
 */
class ShiftDateRepairTest {

    private static List<NinjaShiftRow> rows;

    @BeforeAll
    static void parseFile() throws Exception {
        byte[] content = new ClassPathResource("ninja_swapped_dates.xlsx").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile(
                "file", "ninja_swapped_dates.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

        rows = new NinjaShiftParser().parse(file);
    }

    @Test
    @DisplayName("every swapped start is pulled back into the month the file actually covers")
    void repairsSwappedStarts() {
        assertThat(rows).hasSize(30);

        Set<LocalDate> dates = rows.stream()
                .map(NinjaShiftRow::getShiftDate)
                .collect(Collectors.toSet());

        // One captain, one shift a day, the whole of June — nothing in any other month.
        assertThat(dates).hasSize(30);
        assertThat(dates).allSatisfy(date -> {
            assertThat(date.getYear()).isEqualTo(2026);
            assertThat(date.getMonthValue()).isEqualTo(6);
        });
        assertThat(dates.stream().map(LocalDate::getDayOfMonth).sorted().toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 30).boxed().toList());
    }

    @Test
    @DisplayName("a shift that appeared to end six months before it started is put back on July 1")
    void repairsSwappedEnd() {
        NinjaShiftRow lastOfJune = find("2897638");

        assertThat(lastOfJune.getShiftDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(lastOfJune.getScheduledEnd().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(lastOfJune.getScheduledEnd()).isAfter(lastOfJune.getScheduledStart());
    }

    @Test
    @DisplayName("no shift ends before it starts, and no captain checks in a month early")
    void timestampsAreInternallyConsistent() {
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getScheduledEnd()).isAfter(row.getScheduledStart());
            assertThat(row.getActualCheckin().toLocalDate())
                    .isBetween(row.getShiftDate(), row.getShiftDate().plusDays(1));
        });
    }

    @Test
    @DisplayName("each repair is reported, so a corrected date is never applied silently")
    void repairsAreReported() {
        List<NinjaShiftRow> repaired = rows.stream()
                .filter(r -> !r.getWarnings().isEmpty())
                .toList();

        // Eleven swapped starts — June 6 survived the swap unchanged, day and month being
        // the same — plus the row whose end alone was swapped.
        assertThat(repaired).hasSize(12);
        assertThat(repaired).allSatisfy(row ->
                assertThat(row.getWarnings()).allMatch(w -> w.contains("اليوم والشهر مقلوبان")));
    }

    @Test
    @DisplayName("the supplier reuses a shift id, so the id and captain alone cannot key a row")
    void shiftIdIsReusedAcrossDays() {
        long distinctPairs = rows.stream()
                .map(r -> r.getExternalShiftId() + "|" + r.getNinjaCaptainId())
                .distinct().count();
        long distinctTriples = rows.stream()
                .map(r -> r.getExternalShiftId() + "|" + r.getNinjaCaptainId() + "|" + r.getShiftDate())
                .distinct().count();

        assertThat(distinctPairs).isEqualTo(27);          // three collisions — three lost shifts
        assertThat(distinctTriples).isEqualTo(rows.size());
    }

    @Test
    @DisplayName("a clean file is left exactly as it was read")
    void leavesCorrectFilesAlone() throws Exception {
        byte[] content = new ClassPathResource("ninja_sample.xlsx").getContentAsByteArray();
        List<NinjaShiftRow> clean = new NinjaShiftParser().parse(new MockMultipartFile(
                "file", "ninja_sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content));

        assertThat(clean).hasSize(141);
        assertThat(clean).allSatisfy(row -> assertThat(row.getWarnings()).isEmpty());
    }

    private NinjaShiftRow find(String shiftId) {
        return rows.stream()
                .filter(r -> shiftId.equals(r.getExternalShiftId()))
                .findFirst().orElseThrow();
    }
}
