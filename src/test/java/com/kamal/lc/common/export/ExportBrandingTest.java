package com.kamal.lc.common.export;

import com.kamal.lc.report.dto.CaptainReportResponse;
import com.kamal.lc.report.service.CaptainReportExportService;
import com.kamal.lc.shift.dto.ShiftFilter;
import com.kamal.lc.shift.dto.ShiftResponse;
import com.kamal.lc.shift.service.ShiftExportService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The masthead is what makes an exported file recognisable as the company's own, so both
 * halves of it — the embedded logo and the company name — are asserted on every sheet
 * the system hands out rather than trusted to the shared helper alone.
 */
class ExportBrandingTest {

    private final ExcelBranding branding = new ExcelBranding();
    private final ShiftExportService shiftExport = new ShiftExportService(branding);
    private final CaptainReportExportService captainExport = new CaptainReportExportService(branding);

    @Test
    @DisplayName("the shifts sheet opens with the logo, the company name, and the filters used")
    void shiftsExportIsBranded() throws IOException {
        ShiftFilter filter = new ShiftFilter();
        filter.setFrom(LocalDate.of(2026, 7, 1));
        filter.setTo(LocalDate.of(2026, 7, 31));
        filter.setZoneName("Riyadh West");

        try (Workbook workbook = read(shiftExport.build(List.of(shift()), filter))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.isRightToLeft()).isTrue();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo(ExcelBranding.COMPANY_NAME);
            assertThat(pictures(sheet)).isEqualTo(1);
            assertThat(textOf(sheet)).contains("تقرير الشيفتات")
                    .contains("الفترة من 2026-07-01 إلى 2026-07-31")
                    .contains("المنطقة: Riyadh West")
                    .contains("عدد الشيفتات: 1");
        }
    }

    @Test
    @DisplayName("the individual report is branded and keeps the pay figures")
    void captainReportExportIsBranded() throws IOException {
        try (Workbook workbook = read(captainExport.build(captainReport()))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo(ExcelBranding.COMPANY_NAME);
            assertThat(pictures(sheet)).isEqualTo(1);
            assertThat(textOf(sheet)).contains("تقرير الأداء الفردي")
                    .contains("محمد أحمد")
                    .contains("تفصيل المستحق")
                    .contains("صافي الراتب (ريال)");
        }
    }

    @Test
    @DisplayName("the individual report leaves out the shift list and the profitability")
    void captainReportExportOmitsProfitabilityAndShiftList() throws IOException {
        try (Workbook workbook = read(captainExport.build(captainReport()))) {
            String text = textOf(workbook.getSheetAt(0));

            assertThat(text).doesNotContain("الربحية")
                    .doesNotContain("إيراد المورد")
                    .doesNotContain("الهامش")
                    .doesNotContain("شيفتات الفترة")
                    .doesNotContain("صافي الشفت");
        }
    }

    // ---------------------------------------------------------------- helpers

    private static Workbook read(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static int pictures(Sheet sheet) {
        XSSFDrawing drawing = ((XSSFSheet) sheet).getDrawingPatriarch();
        return drawing == null ? 0 : drawing.getShapes().size();
    }

    /** Every string on the sheet, so a section can be asserted present or absent by name. */
    private static String textOf(Sheet sheet) {
        StringBuilder text = new StringBuilder();
        sheet.rowIterator().forEachRemaining(row -> row.cellIterator().forEachRemaining(cell -> {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                text.append(cell.getStringCellValue()).append('\n');
            }
        }));
        return text.toString();
    }

    private static ShiftResponse shift() {
        return ShiftResponse.builder()
                .id(UUID.randomUUID())
                .shiftDate("2026-07-24")
                .captainCode("KSC0001")
                .captainName("محمد أحمد")
                .ninjaCaptainId("95516")
                .zoneName("Riyadh West")
                .attended(true)
                .workingHours(new BigDecimal("11.03"))
                .totalDelivered(24)
                .busyUtilization(new BigDecimal("0.44"))
                .dailyWage(new BigDecimal("100.00"))
                .lateDeduction(BigDecimal.ZERO)
                .dailyIncentive(new BigDecimal("20.00"))
                .fuelAllowance(BigDecimal.ZERO)
                .netShiftPay(new BigDecimal("120.00"))
                .supplierRevenue(new BigDecimal("150.00"))
                .supplierExtraRevenue(BigDecimal.ZERO)
                .shiftMargin(new BigDecimal("30.00"))
                .build();
    }

    private static CaptainReportResponse captainReport() {
        return CaptainReportResponse.builder()
                .captainId(UUID.randomUUID())
                .captainCode("KSC0001")
                .captainName("محمد أحمد")
                .captainType("EMPLOYEE")
                .periodFrom("2026-07-01")
                .periodTo("2026-07-31")
                .attendedShifts(24)
                .absentShifts(1)
                .totalShifts(25)
                .attendanceRate(new BigDecimal("0.96"))
                .totalOrders(560)
                .totalHours(new BigDecimal("264.5"))
                .avgOrdersPerShift(new BigDecimal("23.3"))
                .avgHoursPerShift(new BigDecimal("11.02"))
                .avgBusyUtilization(new BigDecimal("0.44"))
                .avgHourlyUtr(new BigDecimal("2.12"))
                .avgDeliveryOntime(new BigDecimal("0.91"))
                .basePay(new BigDecimal("2400.00"))
                .lateDeductions(new BigDecimal("50.00"))
                .dailyIncentives(new BigDecimal("120.00"))
                .fuelAllowances(BigDecimal.ZERO)
                .targetBonus(new BigDecimal("1000.00"))
                .extraOrderPay(new BigDecimal("80.00"))
                .gross(new BigDecimal("3550.00"))
                .adjustmentsTotal(new BigDecimal("-100.00"))
                .net(new BigDecimal("3450.00"))
                .monthlyOrderTarget(600)
                .ordersToTarget(40)
                .targetProgress(new BigDecimal("0.93"))
                .nextOrderValue(BigDecimal.ZERO)
                .supplierRevenueTotal(new BigDecimal("4200.00"))
                .margin(new BigDecimal("750.00"))
                .teamAvgOrdersPerShift(new BigDecimal("21.0"))
                .teamAvgBusyUtilization(new BigDecimal("0.41"))
                .teamAvgAttendanceRate(new BigDecimal("0.93"))
                .daily(List.of(CaptainReportResponse.DailyPoint.builder()
                        .date("2026-07-24").attended(true).orders(24)
                        .hours(new BigDecimal("11.03")).build()))
                .deductionBreakdown(List.of(CaptainReportResponse.DeductionBreakdown.builder()
                        .deductionAmount(new BigDecimal("25.00")).shiftCount(2)
                        .total(new BigDecimal("50.00")).build()))
                .build();
    }
}
