package com.kamal.lc.report.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.common.export.ExcelStyles;
import com.kamal.lc.report.dto.OrdersDashboardResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Renders the Daily Delivery Per Captain grid as a colour-coded Excel workbook,
 * replicating the conditional-formatting scheme from the operations spreadsheet:
 *
 * <ul>
 *   <li>1–4 → grey</li>
 *   <li>5–8 → red</li>
 *   <li>9–12 → orange</li>
 *   <li>13–18 → yellow-green</li>
 *   <li>≥19 → green</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrdersDashboardExportService {

    private final ExcelBranding branding;

    /** Fixed columns: name, code, rate%, target, actual. */
    private static final int FIXED_COLS = 5;

    public byte[] build(OrdersDashboardResponse report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("تحقيق الطلبات");
            sheet.setRightToLeft(true);

            List<String> dates = report.getDates();
            int totalCols = FIXED_COLS + dates.size();

            // Column widths: name wide, rest narrow
            sheet.setColumnWidth(0, 30 * 256);   // اسم الكابتن
            sheet.setColumnWidth(1, 12 * 256);   // الكود
            sheet.setColumnWidth(2, 10 * 256);   // النسبة
            sheet.setColumnWidth(3, 8 * 256);    // الهدف
            sheet.setColumnWidth(4, 8 * 256);    // الفعلي
            for (int i = 0; i < dates.size(); i++) {
                sheet.setColumnWidth(FIXED_COLS + i, 5 * 256);
            }

            Styles styles = new Styles(workbook);

            // Masthead
            String typeLabel = "";
            int r = branding.writeMasthead(sheet, totalCols,
                    "لوحة تحكم تحقيق الطلبات" + typeLabel,
                    "Daily Delivery Per Captain Report",
                    "الفترة من " + report.getPeriodFrom() + " إلى " + report.getPeriodTo());
            r++; // spacer

            // Legend row
            Row legendRow = sheet.createRow(r);
            Cell legendCell = legendRow.createCell(0);
            legendCell.setCellValue("قواعد التنسيق الشرطي:");
            legendCell.setCellStyle(styles.label);

            putLegendSwatch(legendRow, 2, "1-4", styles.grey, styles);
            putLegendSwatch(legendRow, 4, "5-8", styles.red, styles);
            putLegendSwatch(legendRow, 6, "9-12", styles.orange, styles);
            putLegendSwatch(legendRow, 8, "13-18", styles.yellowGreen, styles);
            putLegendSwatch(legendRow, 10, "≥19", styles.green, styles);
            r += 2;

            // Header row 1: day-of-week
            Row dowRow = sheet.createRow(r++);
            for (int i = 0; i < FIXED_COLS; i++) {
                dowRow.createCell(i).setCellStyle(styles.header);
            }
            for (int i = 0; i < dates.size(); i++) {
                LocalDate d = LocalDate.parse(dates.get(i));
                Cell cell = dowRow.createCell(FIXED_COLS + i);
                cell.setCellValue(dayOfWeekAr(d.getDayOfWeek()));
                cell.setCellStyle(styles.header);
            }

            // Header row 2: day number + fixed headers
            Row headerRow = sheet.createRow(r++);
            String[] fixedHeaders = {"اسم الكابتن", "الكود", "النسبة%", "الهدف", "الفعلي"};
            for (int i = 0; i < fixedHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(fixedHeaders[i]);
                cell.setCellStyle(styles.header);
            }
            for (int i = 0; i < dates.size(); i++) {
                LocalDate d = LocalDate.parse(dates.get(i));
                Cell cell = headerRow.createCell(FIXED_COLS + i);
                cell.setCellValue(d.getDayOfMonth());
                cell.setCellStyle(styles.header);
            }

            // Captain rows
            for (OrdersDashboardResponse.CaptainRow captain : report.getCaptainRows()) {
                Row row = sheet.createRow(r++);

                row.createCell(0).setCellValue(captain.getCaptainName());
                Cell codeCell = row.createCell(1);
                codeCell.setCellValue(captain.getCaptainCode());
                codeCell.setCellStyle(styles.label);

                Cell rateCell = row.createCell(2);
                rateCell.setCellValue(captain.getAchievementRate().doubleValue());
                rateCell.setCellStyle(styles.percent);

                Cell targetCell = row.createCell(3);
                targetCell.setCellValue(captain.getTarget());
                targetCell.setCellStyle(styles.integer);

                Cell actualCell = row.createCell(4);
                actualCell.setCellValue(captain.getActual());
                actualCell.setCellStyle(styles.integer);

                for (int i = 0; i < dates.size(); i++) {
                    String date = dates.get(i);
                    java.util.List<Integer> shiftOrders = captain.getDailyOrders().getOrDefault(date, java.util.List.of());
                    int orders = shiftOrders.stream().mapToInt(Integer::intValue).sum();
                    Cell cell = row.createCell(FIXED_COLS + i);
                    if (orders > 0) {
                        cell.setCellValue(orders);
                    }
                    cell.setCellStyle(conditionalStyle(orders, styles));
                }
            }

            // Totals row
            Row totalsRow = sheet.createRow(r++);
            Cell totLabel = totalsRow.createCell(0);
            totLabel.setCellValue("المجموع");
            totLabel.setCellStyle(styles.total);

            totalsRow.createCell(1).setCellStyle(styles.total);

            Cell totRate = totalsRow.createCell(2);
            totRate.setCellValue(report.getDailySummary().getTotalRate().doubleValue());
            totRate.setCellStyle(styles.totalPercent);

            Cell totTarget = totalsRow.createCell(3);
            totTarget.setCellValue(report.getDailySummary().getTotalTarget());
            totTarget.setCellStyle(styles.totalInt);

            Cell totActual = totalsRow.createCell(4);
            totActual.setCellValue(report.getDailySummary().getTotalActual());
            totActual.setCellStyle(styles.totalInt);

            for (int i = 0; i < dates.size(); i++) {
                String date = dates.get(i);
                int dayTotal = report.getDailySummary().getDailyTotals().getOrDefault(date, 0);
                Cell cell = totalsRow.createCell(FIXED_COLS + i);
                cell.setCellValue(dayTotal);
                cell.setCellStyle(styles.totalInt);
            }

            // Captain count row
            r++;
            Row countRow = sheet.createRow(r);
            Cell countLabel = countRow.createCell(0);
            countLabel.setCellValue("عدد الكباتن على الشاحنة");
            countLabel.setCellStyle(styles.label);
            Cell countVal = countRow.createCell(4);
            countVal.setCellValue(report.getDailySummary().getTotalCaptains());
            countVal.setCellStyle(styles.integer);

            // Freeze panes: fix the first 5 columns and the header rows
            sheet.createFreezePane(FIXED_COLS, r - report.getCaptainRows().size() - 1);

            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build the orders dashboard export: {}", e.getMessage(), e);
            throw new BusinessException("EXPORT_ERROR", "فشل في إنشاء ملف تقرير الطلبات");
        }
    }

    private CellStyle conditionalStyle(int orders, Styles styles) {
        if (orders >= 19) return styles.green;
        if (orders >= 13) return styles.yellowGreen;
        if (orders >= 9)  return styles.orange;
        if (orders >= 5)  return styles.red;
        if (orders >= 1)  return styles.grey;
        return styles.blank;
    }

    private void putLegendSwatch(Row row, int col, String label, CellStyle fill, Styles styles) {
        Cell swatch = row.createCell(col);
        swatch.setCellValue("■");
        swatch.setCellStyle(fill);
        Cell text = row.createCell(col + 1);
        text.setCellValue(label);
    }

    private static String dayOfWeekAr(DayOfWeek dow) {
        return switch (dow) {
            case SATURDAY  -> "سبت";
            case SUNDAY    -> "أحد";
            case MONDAY    -> "إثن";
            case TUESDAY   -> "ثلا";
            case WEDNESDAY -> "أرب";
            case THURSDAY  -> "خمي";
            case FRIDAY    -> "جمع";
        };
    }

    /** All styles for the orders dashboard workbook, built once per file. */
    private static final class Styles {
        final CellStyle header;
        final CellStyle label;
        final CellStyle integer;
        final CellStyle percent;
        final CellStyle total;
        final CellStyle totalInt;
        final CellStyle totalPercent;
        final CellStyle blank;
        final CellStyle grey;
        final CellStyle red;
        final CellStyle orange;
        final CellStyle yellowGreen;
        final CellStyle green;

        Styles(XSSFWorkbook workbook) {
            header   = ExcelStyles.header(workbook);
            label    = ExcelStyles.label(workbook);
            integer  = ExcelStyles.integer(workbook);
            percent  = ExcelStyles.percent(workbook);
            total    = ExcelStyles.total(workbook);
            totalInt = totalInteger(workbook);
            totalPercent = totalPercent(workbook);
            blank = cellBase(workbook);

            grey        = coloured(workbook, 0x80, 0x80, 0x80);
            red         = coloured(workbook, 0xFF, 0x00, 0x00);
            orange      = coloured(workbook, 0xFF, 0x8C, 0x00);
            yellowGreen = coloured(workbook, 0xFF, 0xD7, 0x00);
            green       = coloured(workbook, 0x22, 0x8B, 0x22);
        }

        private static XSSFCellStyle coloured(XSSFWorkbook workbook, int r, int g, int b) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);

            Font font = workbook.createFont();
            font.setFontHeightInPoints((short) 9);
            if (r == 0xFF && g == 0x00) {
                font.setColor(IndexedColors.WHITE.getIndex());
            }
            style.setFont(font);
            style.setDataFormat(workbook.createDataFormat().getFormat("0"));
            return style;
        }

        private static XSSFCellStyle cellBase(XSSFWorkbook workbook) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        private static CellStyle totalInteger(Workbook workbook) {
            Font font = workbook.createFont();
            font.setBold(true);
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setDataFormat(workbook.createDataFormat().getFormat(ExcelStyles.INT_FORMAT));
            style.setBorderTop(BorderStyle.DOUBLE);
            return style;
        }

        private static CellStyle totalPercent(Workbook workbook) {
            Font font = workbook.createFont();
            font.setBold(true);
            CellStyle style = workbook.createCellStyle();
            style.setFont(font);
            style.setDataFormat(workbook.createDataFormat().getFormat(ExcelStyles.PERCENT_FORMAT));
            style.setBorderTop(BorderStyle.DOUBLE);
            return style;
        }
    }
}
