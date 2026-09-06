package com.kamal.lc.report.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.common.export.ExcelStyles;
import com.kamal.lc.report.dto.CaptainReportResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Renders one captain's individual performance report as a workbook.
 *
 * <p>Deliberately narrower than the screen: the per-shift list and the profitability
 * figures are left out, so the file can be handed to the captain as a statement of his
 * own performance and pay without exposing what the shift earned the company.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptainReportExportService {

    private final ExcelBranding branding;

    /** Sheet width — the widest block in it is the three-column summary table. */
    private static final int COLUMNS = 4;
    private static final int[] WIDTHS = {34, 16, 18, 16};

    public byte[] build(CaptainReportResponse report) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("الأداء الفردي");
            sheet.setRightToLeft(true);
            for (int i = 0; i < WIDTHS.length; i++) {
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }

            Styles styles = new Styles(workbook);

            int r = branding.writeMasthead(sheet, COLUMNS, "تقرير الأداء الفردي",
                    report.getCaptainName() + "   —   " + report.getCaptainCode()
                            + "   (" + typeLabel(report.getCaptainType()) + ")",
                    "الفترة من " + report.getPeriodFrom() + " إلى " + report.getPeriodTo());
            r++; // blank spacer row

            r = writeIdentity(sheet, styles, report, r);
            r = writeTarget(sheet, styles, report, r);
            r = writePerformance(sheet, styles, report, r);
            r = writePay(sheet, styles, report, r);
            r = writeDeductions(sheet, styles, report, r);
            r = writeDaily(sheet, styles, report, r);
            writeNote(sheet, styles, r);

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build the captain report export: {}", e.getMessage(), e);
            throw new BusinessException("EXPORT_ERROR", "فشل في إنشاء ملف تقرير الكابتن");
        }
    }

    // ---------------------------------------------------------------- sections

    private int writeIdentity(Sheet sheet, Styles styles, CaptainReportResponse report, int r) {
        r = section(sheet, styles, "بيانات الكابتن", r);
        r = text(sheet, styles, "الكود", report.getCaptainCode(), r);
        r = text(sheet, styles, "اسم الكابتن", report.getCaptainName(), r);
        r = text(sheet, styles, "النوع", typeLabel(report.getCaptainType()), r);
        r = text(sheet, styles, "معرفات المورد", report.getSupplierExternalIds(), r);
        r = text(sheet, styles, "المركبة", report.isUsesOwnVehicle() ? "سيارته الخاصة" : "مركبة الشركة", r);
        r = text(sheet, styles, "بدل البنزين", report.isFuelAllowanceEligible() ? "مستحق" : "غير مستحق", r);
        r = text(sheet, styles, "بدل السيارة", report.isUsesOwnVehicle() ? "مستحق" : "غير مستحق", r);
        return r + 1;
    }

    private int writeTarget(Sheet sheet, Styles styles, CaptainReportResponse report, int r) {
        r = section(sheet, styles, "هدف الشهر", r);
        r = value(sheet, styles, "هدف الشهر (طلب)", BigDecimal.valueOf(report.getMonthlyOrderTarget()), styles.integer, r);
        r = value(sheet, styles, "الطلبات المنفذة", BigDecimal.valueOf(report.getTotalOrders()), styles.integer, r);
        r = value(sheet, styles, "المتبقي للهدف", BigDecimal.valueOf(report.getOrdersToTarget()), styles.integer, r);
        r = value(sheet, styles, "نسبة التقدم", report.getTargetProgress(), styles.percent, r);
        r = value(sheet, styles, "قيمة الطلب الإضافي الآن", report.getNextOrderValue(), styles.money, r);
        return r + 1;
    }

    /** The KPI strip, with the team's own average beside each figure that has one. */
    private int writePerformance(Sheet sheet, Styles styles, CaptainReportResponse report, int r) {
        r = section(sheet, styles, "ملخص الأداء", r);

        Row head = sheet.createRow(r++);
        String[] headers = {"البيان", "القيمة", "متوسط الفريق"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = head.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        r = value(sheet, styles, "شفتات الحضور", BigDecimal.valueOf(report.getAttendedShifts()), styles.integer, r);
        r = value(sheet, styles, "إجمالي الشفتات", BigDecimal.valueOf(report.getTotalShifts()), styles.integer, r);
        r = value(sheet, styles, "أيام الغياب", BigDecimal.valueOf(report.getAbsentShifts()), styles.integer, r);
        r = compared(sheet, styles, "نسبة الالتزام", report.getAttendanceRate(),
                report.getTeamAvgAttendanceRate(), styles.percent, r);
        r = value(sheet, styles, "عدد الطلبات", BigDecimal.valueOf(report.getTotalOrders()), styles.integer, r);
        r = compared(sheet, styles, "متوسط الطلبات لكل شفت", report.getAvgOrdersPerShift(),
                report.getTeamAvgOrdersPerShift(), styles.decimal, r);
        r = value(sheet, styles, "عدد الساعات", report.getTotalHours(), styles.decimal, r);
        r = value(sheet, styles, "متوسط الساعات لكل شفت", report.getAvgHoursPerShift(), styles.decimal, r);
        r = compared(sheet, styles, "نسبة الانشغال", report.getAvgBusyUtilization(),
                report.getTeamAvgBusyUtilization(), styles.percent, r);
        r = value(sheet, styles, "التسليم في الوقت", report.getAvgDeliveryOntime(), styles.percent, r);
        r = value(sheet, styles, "UTR بالساعة", report.getAvgHourlyUtr(), styles.decimal, r);
        return r + 1;
    }

    private int writePay(Sheet sheet, Styles styles, CaptainReportResponse report, int r) {
        r = section(sheet, styles, "تفصيل المستحق", r);
        r = value(sheet, styles, "مجموع الدخل (" + report.getAttendedShifts() + " شفت)",
                report.getBasePay(), styles.money, r);
        r = value(sheet, styles, "خصم التأخير", negate(report.getLateDeductions()), styles.money, r);
        r = value(sheet, styles, "الحوافز اليومية", report.getDailyIncentives(), styles.money, r);
        r = value(sheet, styles, "بدل البنزين", report.getFuelAllowances(), styles.money, r);
        r = value(sheet, styles, "بدل السيارة", report.getCarAllowances(), styles.money, r);
        r = value(sheet, styles, "مكافأة تحقيق الهدف", report.getTargetBonus(), styles.money, r);
        r = value(sheet, styles, "طلبات بعد الهدف", report.getExtraOrderPay(), styles.money, r);
        r = value(sheet, styles, "مجموع الراتب", report.getGross(), styles.money, r);
        r = value(sheet, styles, "البنود اليدوية", report.getAdjustmentsTotal(), styles.money, r);

        Row row = sheet.createRow(r++);
        Cell label = row.createCell(0);
        label.setCellValue("صافي الراتب (ريال)");
        label.setCellStyle(styles.total);
        Cell net = row.createCell(1);
        net.setCellValue(nz(report.getNet()).doubleValue());
        net.setCellStyle(styles.total);
        return r + 1;
    }

    private int writeDeductions(Sheet sheet, Styles styles, CaptainReportResponse report, int r) {
        List<CaptainReportResponse.DeductionBreakdown> items =
                report.getDeductionBreakdown() != null ? report.getDeductionBreakdown() : List.of();
        if (items.isEmpty()) return r;

        r = section(sheet, styles, "أين ذهبت الخصومات", r);

        Row head = sheet.createRow(r++);
        String[] headers = {"قيمة الخصم", "عدد الشفتات", "الإجمالي"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = head.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        for (CaptainReportResponse.DeductionBreakdown item : items) {
            Row row = sheet.createRow(r++);
            number(row, 0, item.getDeductionAmount(), styles.money);
            number(row, 1, BigDecimal.valueOf(item.getShiftCount()), styles.integer);
            number(row, 2, negate(item.getTotal()), styles.money);
        }
        return r + 1;
    }

    /**
     * The daily orders-and-hours series behind the on-screen chart. The per-shift money
     * columns stay out of the file — this is the shape of the month, not a shift ledger.
     */
    private int writeDaily(Sheet sheet, Styles styles, CaptainReportResponse report, int r) {
        List<CaptainReportResponse.DailyPoint> points =
                report.getDaily() != null ? report.getDaily() : List.of();
        if (points.isEmpty()) return r;

        r = section(sheet, styles, "الأداء اليومي", r);

        Row head = sheet.createRow(r++);
        String[] headers = {"التاريخ", "الحالة", "عدد الطلبات", "عدد الساعات"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = head.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }

        for (CaptainReportResponse.DailyPoint point : points) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(point.getDate() != null ? point.getDate() : "");
            row.createCell(1).setCellValue(point.isAttended() ? "حضور" : "غياب");
            number(row, 2, point.getOrders() != null ? BigDecimal.valueOf(point.getOrders()) : null, styles.integer);
            number(row, 3, point.getHours(), styles.decimal);
        }
        return r + 1;
    }

    private void writeNote(Sheet sheet, Styles styles, int r) {
        Row row = sheet.createRow(r);
        Cell cell = row.createCell(0);
        cell.setCellValue("أرقام تقديرية بالأسعار السارية — مسير الرواتب المعتمد هو المرجع لما صُرف فعلًا.");
        cell.setCellStyle(styles.note);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, COLUMNS - 1));
    }

    // ---------------------------------------------------------------- row helpers

    private int section(Sheet sheet, Styles styles, String title, int r) {
        Row row = sheet.createRow(r);
        row.setHeightInPoints(20);
        for (int i = 0; i < COLUMNS; i++) {
            row.createCell(i).setCellStyle(styles.section);
        }
        row.getCell(0).setCellValue(title);
        sheet.addMergedRegion(new CellRangeAddress(r, r, 0, COLUMNS - 1));
        return r + 1;
    }

    private int text(Sheet sheet, Styles styles, String label, String value, int r) {
        Row row = sheet.createRow(r);
        Cell name = row.createCell(0);
        name.setCellValue(label);
        name.setCellStyle(styles.label);
        row.createCell(1).setCellValue(value != null ? value : "—");
        return r + 1;
    }

    private int value(Sheet sheet, Styles styles, String label, BigDecimal value, CellStyle style, int r) {
        Row row = sheet.createRow(r);
        Cell name = row.createCell(0);
        name.setCellValue(label);
        name.setCellStyle(styles.label);
        number(row, 1, value, style);
        return r + 1;
    }

    /** Same as {@link #value} plus the team's average in the third column. */
    private int compared(Sheet sheet, Styles styles, String label, BigDecimal value,
                         BigDecimal teamValue, CellStyle style, int r) {
        Row row = sheet.createRow(r);
        Cell name = row.createCell(0);
        name.setCellValue(label);
        name.setCellStyle(styles.label);
        number(row, 1, value, style);
        number(row, 2, teamValue, style);
        return r + 1;
    }

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private static BigDecimal negate(BigDecimal value) {
        return value != null ? value.negate() : null;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String typeLabel(String captainType) {
        return "FREELANCE".equalsIgnoreCase(captainType) ? "KSCFL" : "KSC";
    }

    /** The workbook's styles, built once per file. */
    private static final class Styles {
        private final CellStyle header;
        private final CellStyle section;
        private final CellStyle label;
        private final CellStyle money;
        private final CellStyle integer;
        private final CellStyle percent;
        private final CellStyle decimal;
        private final CellStyle total;
        private final CellStyle note;

        private Styles(Workbook workbook) {
            header = ExcelStyles.header(workbook);
            section = ExcelStyles.section(workbook);
            label = ExcelStyles.label(workbook);
            money = ExcelStyles.money(workbook);
            integer = ExcelStyles.integer(workbook);
            percent = ExcelStyles.percent(workbook);
            decimal = ExcelStyles.number(workbook, "0.00");
            total = ExcelStyles.total(workbook);
            note = noteStyle(workbook);
        }

        private static CellStyle noteStyle(Workbook workbook) {
            Font font = workbook.createFont();
            font.setItalic(true);
            font.setFontHeightInPoints((short) 9);
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            return style;
        }
    }
}
