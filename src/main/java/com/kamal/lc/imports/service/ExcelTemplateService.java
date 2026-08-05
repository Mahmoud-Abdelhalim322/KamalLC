package com.kamal.lc.imports.service;

import com.kamal.lc.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds the blank import sheets so users start from the exact column layout the
 * parsers expect — the captain roster for {@link ExcelParserService}, and the
 * supplier shift report for {@link NinjaShiftParser}.
 */
@Slf4j
@Service
public class ExcelTemplateService {

    /**
     * Same order as the reference sheet; the parser matches by header name, not position.
     * The work permit and insurance columns are appended rather than slotted in beside the
     * other documents, so a sheet someone already filled in keeps every column where it was.
     */
    public static final List<String> TEMPLATE_HEADERS = List.of(
            "ID-KSLSC", "ID - NINJA", "اسم الكابتن", "الهاتف", "الجنسية",
            "تاريخ الميلاد", "البريد الالكتروني", "رقم الإقامة", "انتهاء الإقامة",
            "رقم الجواز", "انتهاء الجواز", "رقم الرخصة", "انتهاء الرخصة",
            "انتهاء رخصة العمل", "رقم التأمين", "شركة التأمين", "انتهاء التأمين");

    /** One filled-in row so the expected date and ID formats are obvious. */
    private static final List<String> SAMPLE_ROW = List.of(
            "KSC0001", "1234567", "محمد أحمد", "0501234567", "مصري",
            "1990-05-12", "captain@example.com", "2412345678", "2026-08-30",
            "A12345678", "2028-01-15", "L9876543", "2027-03-01",
            "2026-11-20", "INS-556677", "شركة أمانة للتأمين التعاوني", "2027-01-31");

    /**
     * The supplier's own export layout, header for header. Users normally upload that
     * file untouched — this template is for the times they have to assemble it by hand,
     * so any deviation here would be a file the supplier's next export contradicts.
     */
    public static final List<String> SHIFT_TEMPLATE_HEADERS = List.of(
            "supplier_id", "supplier_name", "shift_id", "City", "shiftName",
            "captain_id", "captain_name", "Shift Start At", "Shift End At",
            "Captain Checked-in At", "Flag - attendance", "Delay Time (Minutes)",
            "Working Hour (Hour)", "Shift Type", "Total Delivered", "Offline Time",
            "Free Time", "Busy Time", "Away from Zone", "Busy Utilization %",
            "Delivery Ontime %", "Hourly UTR");

    /**
     * A sample shift taken from a real export. The three timestamps are written as
     * genuine date cells rather than text: the parser reads them as Excel serials, and
     * a hand-typed string in those columns is silently read as "no date".
     */
    private static final List<Object> SHIFT_SAMPLE_ROW = List.of(
            "2399", "Kamal_Food_ftr(car)", "3043452", "Riyadh", "Riyadh West",
            "95516", "Alaelden omer",
            LocalDateTime.of(2026, 7, 24, 18, 0),
            LocalDateTime.of(2026, 7, 25, 6, 0),
            LocalDateTime.of(2026, 7, 24, 18, 1),
            "Working more 8 Hour",
            1.03, 11.03, 12, 8, 0.96, 6.21, 4.82, 0, 0.44, 1.0, 0.73);

    /** Percent-formatted in the supplier's export — 0.44 displays as 44%. */
    private static final List<String> SHIFT_PERCENT_HEADERS =
            List.of("Busy Utilization %", "Delivery Ontime %");

    public byte[] buildCaptains() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("الكباتن");
            sheet.setRightToLeft(true);

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = textStyle(workbook);

            writeHeaderRow(sheet, TEMPLATE_HEADERS, headerStyle);

            Row sampleRow = sheet.createRow(1);
            for (int i = 0; i < SAMPLE_ROW.size(); i++) {
                Cell cell = sampleRow.createCell(i);
                cell.setCellValue(SAMPLE_ROW.get(i));
                // Text format keeps leading zeros in phone numbers and IDs intact.
                cell.setCellStyle(textStyle);
            }

            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build the import template: {}", e.getMessage(), e);
            throw new BusinessException("TEMPLATE_ERROR", "فشل في إنشاء ملف القالب");
        }
    }

    public static final List<String> DEDUCTION_TEMPLATE_HEADERS = List.of(
            "ID - NINJA", "ID-KSLSC", "تاريخ الشيفت", "سعر الخصم", "اسم الكابتن", "سبب الخصم");

    private static final List<Object> DEDUCTION_SAMPLE_ROW = List.of(
            "95516", "KSC0001", "2026-07-24", 50.00, "محمد أحمد", "خصم تأخير عن الشيفت");

    public byte[] buildDeductions() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("خصومات المورد");
            sheet.setRightToLeft(true);

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = textStyle(workbook);
            CellStyle dateStyle = dataFormatStyle(workbook, "yyyy-mm-dd");
            CellStyle currencyStyle = dataFormatStyle(workbook, "#,##0.00");

            writeHeaderRow(sheet, DEDUCTION_TEMPLATE_HEADERS, headerStyle);

            Row sampleRow = sheet.createRow(1);
            for (int i = 0; i < DEDUCTION_SAMPLE_ROW.size(); i++) {
                Cell cell = sampleRow.createCell(i);
                Object val = DEDUCTION_SAMPLE_ROW.get(i);
                if (val instanceof Number num) {
                    cell.setCellValue(num.doubleValue());
                    cell.setCellStyle(currencyStyle);
                } else {
                    cell.setCellValue(String.valueOf(val));
                    cell.setCellStyle(textStyle);
                }
            }

            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build the deduction import template: {}", e.getMessage(), e);
            throw new BusinessException("TEMPLATE_ERROR", "فشل في إنشاء ملف قالب خصومات المورد");
        }
    }

    public byte[] buildShifts() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Left-to-right: every header and most values in this file are English.
            Sheet sheet = workbook.createSheet("Shifts");

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = textStyle(workbook);
            CellStyle dateStyle = dataFormatStyle(workbook, "yyyy-mm-dd hh:mm");
            CellStyle numberStyle = dataFormatStyle(workbook, "0.00");
            CellStyle percentStyle = dataFormatStyle(workbook, "0%");

            writeHeaderRow(sheet, SHIFT_TEMPLATE_HEADERS, headerStyle);

            Row sampleRow = sheet.createRow(1);
            for (int i = 0; i < SHIFT_SAMPLE_ROW.size(); i++) {
                Cell cell = sampleRow.createCell(i);
                Object value = SHIFT_SAMPLE_ROW.get(i);
                boolean isPercent = SHIFT_PERCENT_HEADERS.contains(SHIFT_TEMPLATE_HEADERS.get(i));

                if (value instanceof LocalDateTime dateTime) {
                    cell.setCellValue(dateTime);
                    cell.setCellStyle(dateStyle);
                } else if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                    cell.setCellStyle(isPercent ? percentStyle : numberStyle);
                } else {
                    // IDs stay text so long supplier IDs never turn into 9.55E+04.
                    cell.setCellValue(String.valueOf(value));
                    cell.setCellStyle(textStyle);
                }
            }

            sheet.createFreezePane(0, 1);
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build the shift import template: {}", e.getMessage(), e);
            throw new BusinessException("TEMPLATE_ERROR", "فشل في إنشاء ملف القالب");
        }
    }

    private void writeHeaderRow(Sheet sheet, List<String> headers, CellStyle style) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
            sheet.setColumnWidth(i, 18 * 256);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle textStyle(Workbook workbook) {
        return dataFormatStyle(workbook, "@");
    }

    private CellStyle dataFormatStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }
}
