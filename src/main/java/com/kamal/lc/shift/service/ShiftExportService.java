package com.kamal.lc.shift.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.common.export.ExcelStyles;
import com.kamal.lc.shift.dto.ShiftFilter;
import com.kamal.lc.shift.dto.ShiftResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Renders the shifts screen as a workbook: the same rows the user is looking at,
 * with the filters they used printed under the company masthead so a saved file
 * still says what it is a month later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftExportService {

    private final ExcelBranding branding;

    /** Column order follows the shifts table, with the reconciliation columns added. */
    private static final List<String> HEADERS = List.of(
            "التاريخ",
            "ID-KSLSC",
            "اسم الكابتن",
            "ID - NINJA",
            "اسم المورد",
            "المدينة",
            "المنطقة",
            "الشفت",
            "الحالة",
            "التأخير\n(دقيقة)",
            "عدد الساعات",
            "عدد الطلبات",
            "نسبة الانشغال",
            "التسليم في الوقت",
            "UTR",
            "اليومية",
            "خصم تأخير",
            "حافز يومي",
            "بدل بنزين",
            "بدل سيارة",
            "صافي الشفت",
            "إيراد المورد",
            "خصم المورد\nSUPPLIER DEDUCTION",
            "الهامش",
            "سبب الاستبعاد");

    /** Widths in characters, per column above. */
    private static final int[] WIDTHS = {
            12, 12, 22, 12, 20, 12, 16, 14, 10, 10, 11, 11, 12, 13, 8, 11, 11, 11, 11, 11, 12, 12, 11, 11, 24};

    public byte[] build(List<ShiftResponse> shifts, ShiftFilter filter) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("الشيفتات");
            sheet.setRightToLeft(true);

            for (int i = 0; i < WIDTHS.length; i++) {
                sheet.setColumnWidth(i, WIDTHS[i] * 256);
            }

            CellStyle headerStyle = ExcelStyles.header(workbook);
            CellStyle moneyStyle = ExcelStyles.money(workbook);
            CellStyle intStyle = ExcelStyles.integer(workbook);
            CellStyle percentStyle = ExcelStyles.percent(workbook);
            CellStyle utrStyle = ExcelStyles.number(workbook, "0.00");
            CellStyle totalStyle = ExcelStyles.total(workbook);

            int r = branding.writeMasthead(sheet, HEADERS.size(), "تقرير الشيفتات",
                    periodLine(filter), filterLine(filter, shifts.size()));
            r++; // blank spacer row

            int headerRowIdx = r;
            Row headerRow = sheet.createRow(r++);
            headerRow.setHeightInPoints(32);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(headerStyle);
            }

            for (ShiftResponse shift : shifts) {
                Row row = sheet.createRow(r++);
                int c = 0;
                text(row, c++, shift.getShiftDate());
                text(row, c++, shift.getCaptainCode());
                text(row, c++, shift.getCaptainName() != null ? shift.getCaptainName() : "غير مربوط");
                text(row, c++, shift.getNinjaCaptainId());
                text(row, c++, shift.getSupplierName());
                text(row, c++, shift.getCity());
                text(row, c++, shift.getZoneName());
                text(row, c++, shift.getShiftName());
                text(row, c++, statusLabel(shift));
                number(row, c++, shift.getDelayMinutes(), intStyle);
                number(row, c++, shift.getWorkingHours(), utrStyle);
                number(row, c++, toDecimal(shift.getTotalDelivered()), intStyle);
                number(row, c++, shift.getBusyUtilization(), percentStyle);
                number(row, c++, shift.getDeliveryOntime(), percentStyle);
                number(row, c++, shift.getHourlyUtr(), utrStyle);
                number(row, c++, shift.getDailyWage(), moneyStyle);
                number(row, c++, shift.getLateDeduction(), moneyStyle);
                number(row, c++, shift.getDailyIncentive(), moneyStyle);
                number(row, c++, shift.getFuelAllowance(), moneyStyle);
                number(row, c++, shift.getCarAllowance(), moneyStyle);
                number(row, c++, shift.getNetShiftPay(), moneyStyle);
                number(row, c++, supplierRevenue(shift), moneyStyle);
                number(row, c++, shift.getSupplierDeduction(), moneyStyle);
                number(row, c++, shift.getShiftMargin(), moneyStyle);
                text(row, c, shift.getExclusionReason());
            }

            if (!shifts.isEmpty()) {
                writeTotalsRow(sheet, r, shifts, totalStyle);
            }

            sheet.createFreezePane(3, headerRowIdx + 1);
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build the shifts export: {}", e.getMessage(), e);
            throw new BusinessException("EXPORT_ERROR", "فشل في إنشاء ملف الشيفتات");
        }
    }

    private void writeTotalsRow(Sheet sheet, int rowIdx, List<ShiftResponse> shifts, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        Cell label = row.createCell(0);
        label.setCellValue("المجموع");
        label.setCellStyle(style);

        // Column index → how to pull the value out of a row; keeps the totals in step
        // with the data columns above them.
        record Total(int column, Function<ShiftResponse, BigDecimal> field) {}
        List<Total> totals = List.of(
                new Total(10, ShiftResponse::getWorkingHours),
                new Total(11, s -> toDecimal(s.getTotalDelivered())),
                new Total(15, ShiftResponse::getDailyWage),
                new Total(16, ShiftResponse::getLateDeduction),
                new Total(17, ShiftResponse::getDailyIncentive),
                new Total(18, ShiftResponse::getFuelAllowance),
                new Total(19, ShiftResponse::getCarAllowance),
                new Total(20, ShiftResponse::getNetShiftPay),
                new Total(21, ShiftExportService::supplierRevenue),
                new Total(22, ShiftResponse::getSupplierDeduction),
                new Total(23, ShiftResponse::getShiftMargin));

        for (Total total : totals) {
            BigDecimal sum = shifts.stream().map(total.field())
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Cell cell = row.createCell(total.column());
            cell.setCellValue(sum.doubleValue());
            cell.setCellStyle(style);
        }
    }

    private static String periodLine(ShiftFilter filter) {
        if (filter.getFrom() == null && filter.getTo() == null) return "كل الفترات";
        return "الفترة من " + (filter.getFrom() != null ? filter.getFrom() : "البداية")
                + " إلى " + (filter.getTo() != null ? filter.getTo() : "اليوم");
    }

    /** Spells out the filters behind the file, so the row count is never read out of context. */
    private static String filterLine(ShiftFilter filter, int rowCount) {
        List<String> parts = new ArrayList<>();
        parts.add("عدد الشيفتات: " + rowCount);

        if (Boolean.TRUE.equals(filter.getUnmatched())) parts.add("غير المربوطة بكابتن فقط");
        else if (Boolean.TRUE.equals(filter.getExcluded())) parts.add("المستبعدة فقط");
        else if (Boolean.TRUE.equals(filter.getAttended())) parts.add("الحضور فقط");
        else if (Boolean.FALSE.equals(filter.getAttended())) parts.add("الغياب فقط");

        if (filter.getZoneName() != null && !filter.getZoneName().isBlank()) {
            parts.add("المنطقة: " + filter.getZoneName());
        }
        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            parts.add("بحث: " + filter.getSearch().trim());
        }
        return String.join("   —   ", parts);
    }

    private static String statusLabel(ShiftResponse shift) {
        if (shift.isExcluded()) return "مستبعد";
        return Boolean.TRUE.equals(shift.getAttended()) ? "حضور" : "غياب";
    }

    private static BigDecimal supplierRevenue(ShiftResponse shift) {
        return nz(shift.getSupplierRevenue()).add(nz(shift.getSupplierExtraRevenue()));
    }

    private static BigDecimal toDecimal(Integer value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static void text(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    /** A blank cell rather than a zero when the supplier sent nothing for the column. */
    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }
}
