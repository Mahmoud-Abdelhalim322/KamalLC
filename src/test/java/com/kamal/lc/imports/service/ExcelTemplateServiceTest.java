package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.NinjaShiftRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A template that its own parser cannot read is worse than no template at all, so both
 * sheets are round-tripped here rather than only checked for their headers.
 */
class ExcelTemplateServiceTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExcelTemplateService templateService = new ExcelTemplateService();

    @Test
    @DisplayName("the shift template's sample row parses back out of the sheet")
    void shiftTemplateRoundTrips() {
        List<NinjaShiftRow> rows = new NinjaShiftParser().parse(shiftTemplate());

        assertThat(rows).hasSize(1);
        NinjaShiftRow row = rows.get(0);

        assertThat(row.getExternalShiftId()).isEqualTo("3043452");
        assertThat(row.getNinjaCaptainId()).isEqualTo("95516");
        assertThat(row.getSupplierExternalId()).isEqualTo("2399");
        assertThat(row.getNinjaCaptainName()).isEqualTo("Alaelden omer");
        assertThat(row.getAttendanceFlag()).isEqualTo("Working more 8 Hour");
        assertThat(row.getTotalDelivered()).isEqualTo(8);
        assertThat(row.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("the sample timestamps are real date cells, not text the parser drops")
    void shiftTemplateTimestampsAreReadable() {
        NinjaShiftRow row = new NinjaShiftParser().parse(shiftTemplate()).get(0);

        assertThat(row.getScheduledStart()).isNotNull();
        assertThat(row.getScheduledEnd()).isNotNull();
        assertThat(row.getActualCheckin()).isNotNull();
        // 18:00 KSA on the 24th — the shift's calendar day follows its start.
        assertThat(row.getShiftDate()).isEqualTo(LocalDate.of(2026, 7, 24));
    }

    @Test
    @DisplayName("the captain template's sample row parses back out of the sheet")
    void captainTemplateRoundTrips() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "captains-import-template.xlsx", XLSX, templateService.buildCaptains());

        assertThat(new ExcelParserService().parse(file))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getCaptainName()).isEqualTo("محمد أحمد");
                    assertThat(row.getAppId()).isEqualTo("1001");
                });
    }

    @Test
    @DisplayName("the deduction template builds without errors")
    void deductionTemplateBuilds() {
        byte[] content = templateService.buildDeductions();
        assertThat(content).isNotEmpty();
    }

    private MockMultipartFile shiftTemplate() {
        return new MockMultipartFile("file", "supplier-shifts-import-template.xlsx", XLSX,
                templateService.buildShifts());
    }
}
