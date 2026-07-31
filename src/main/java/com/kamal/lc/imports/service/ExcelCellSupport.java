package com.kamal.lc.imports.service;

import org.apache.poi.ss.usermodel.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Cell- and header-reading helpers shared by the Excel parsers.
 *
 * <p>Extracted from {@code ExcelParserService} when the Ninja shift file needed the same
 * header normalisation and numeric-cell handling; both parsers now read cells identically.
 */
public final class ExcelCellSupport {

    /** Renders cells the way Excel displays them — keeps leading zeros in phone numbers. */
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    /**
     * Display forms we must not trust for IDs/phones: scientific notation, thousands
     * separators, embedded spaces, or an overflowed (####) column.
     */
    private static final Pattern UNUSABLE_NUMERIC_DISPLAY = Pattern.compile("[Ee#,\\s]");

    /** Arabic-Indic and extended Arabic-Indic digits, mapped onto 0-9 by index. */
    private static final String ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩";
    private static final String EXTENDED_ARABIC_INDIC_DIGITS = "۰۱۲۳۴۵۶۷۸۹";

    /** Values suppliers use for "no data" that must not be read as zero. */
    private static final java.util.Set<String> NULL_TOKENS =
            java.util.Set.of("-", "--", "nan", "n/a", "na", "null", "#n/a", "#div/0!", "");

    private ExcelCellSupport() {}

    /**
     * Folds away the spelling differences that show up between files: letter case,
     * repeated spaces, tatweel, hamza forms (إقامة/اقامة), and ى/ة endings.
     */
    public static String normalizeHeader(String raw) {
        String value = raw.trim().toLowerCase()
                .replace("ـ", "")          // tatweel
                .replaceAll("[\\u064B-\\u065F]", "") // harakat
                .replaceAll("[\\u200B-\\u200F\\u202A-\\u202E]", "") // bidi/zero-width marks
                .replaceAll("\\s+", " ");
        value = value.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ة', 'ه').replace('ى', 'ي')
                .replace('ئ', 'ي').replace('ؤ', 'و');
        return value;
    }

    /**
     * Finds the first row within the top 10 that carries at least one recognised header.
     * Files often start with a title block, so row 0 is not a safe assumption.
     */
    public static int findHeaderRowIndex(Sheet sheet, Map<String, String> columnMap) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 10); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = getCellString(row, cell.getColumnIndex());
                if (val != null && columnMap.containsKey(normalizeHeader(val))) {
                    return i;
                }
            }
        }
        return 0;
    }

    /** Maps logical field names onto the column indexes they occupy in this sheet. */
    public static Map<String, Integer> detectHeaders(Sheet sheet, Map<String, String> columnMap) {
        int headerIdx = findHeaderRowIndex(sheet, columnMap);
        Row headerRow = sheet.getRow(headerIdx);
        if (headerRow == null) {
            return Map.of();
        }

        Map<String, Integer> fieldToCol = new HashMap<>();
        for (Cell cell : headerRow) {
            String val = getCellString(headerRow, cell.getColumnIndex());
            if (val == null) continue;
            String field = columnMap.get(normalizeHeader(val));
            if (field != null && !fieldToCol.containsKey(field)) {
                fieldToCol.put(field, cell.getColumnIndex());
            }
        }
        return fieldToCol;
    }

    public static String getCellString(Row row, int colIdx) {
        if (colIdx < 0) return null;
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> numericToString(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    /** Trimmed text for a mapped field, or null when the column is absent or blank. */
    public static String text(Row row, Map<String, Integer> fieldToCol, String field) {
        Integer col = fieldToCol.get(field);
        if (col == null) return null;
        String value = getCellString(row, col);
        if (value == null) return null;
        value = value.trim();
        if (value.isEmpty() || NULL_TOKENS.contains(value.toLowerCase())) return null;
        return value;
    }

    /**
     * Reads a numeric field, treating the supplier's placeholder tokens ("-", "NaN")
     * as missing rather than zero — a shift with no utilisation is not a shift with
     * zero utilisation.
     */
    public static BigDecimal decimal(Row row, Map<String, Integer> fieldToCol, String field) {
        Integer col = fieldToCol.get(field);
        if (col == null) return null;

        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        if (type == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return BigDecimal.valueOf(value);
        }

        String raw = text(row, fieldToCol, field);
        if (raw == null) return null;
        try {
            return new BigDecimal(toWesternDigits(raw).replace(",", "").replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer integer(Row row, Map<String, Integer> fieldToCol, String field) {
        BigDecimal value = decimal(row, fieldToCol, field);
        return value != null ? value.intValue() : null;
    }

    /**
     * Reads a date-time column. Supplier exports carry these as raw Excel serial numbers
     * (46224.75 = 2026-07-24 18:00), which POI converts using the workbook's 1900/1904
     * epoch; the result is interpreted in {@code zone} because the supplier reports in
     * local time.
     */
    public static java.time.OffsetDateTime dateTime(Row row, Map<String, Integer> fieldToCol,
                                                    String field, ZoneId zone) {
        Integer col = fieldToCol.get(field);
        if (col == null) return null;

        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        if (type == CellType.NUMERIC) {
            double serial = cell.getNumericCellValue();
            if (serial <= 0 || Double.isNaN(serial)) return null;
            java.time.LocalDateTime local = DateUtil.getLocalDateTime(serial);
            return local != null ? local.atZone(zone).toOffsetDateTime() : null;
        }

        String raw = text(row, fieldToCol, field);
        if (raw == null) return null;
        try {
            // A text cell holding the serial number, which happens after a CSV round-trip.
            double serial = Double.parseDouble(toWesternDigits(raw).trim());
            java.time.LocalDateTime local = DateUtil.getLocalDateTime(serial);
            return local != null ? local.atZone(zone).toOffsetDateTime() : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String toWesternDigits(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            int arabicIndic = ARABIC_INDIC_DIGITS.indexOf(c);
            int extended = EXTENDED_ARABIC_INDIC_DIGITS.indexOf(c);
            if (arabicIndic >= 0) {
                sb.append((char) ('0' + arabicIndic));
            } else if (extended >= 0) {
                sb.append((char) ('0' + extended));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Renders a numeric cell as Excel displays it, so text-formatted values such as
     * phone numbers keep their leading zero. Falls back to a plain integral rendering
     * when the display form is unusable (e.g. scientific notation for long IDs).
     */
    public static String numericToString(Cell cell) {
        // Date-formatted cells must keep their separators, which the unusable-display
        // check below would otherwise reject.
        if (DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
            return date.toString();
        }

        String formatted = DATA_FORMATTER.formatCellValue(cell);
        if (formatted != null && !formatted.isBlank() && !UNUSABLE_NUMERIC_DISPLAY.matcher(formatted).find()) {
            return formatted;
        }

        double d = cell.getNumericCellValue();
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    public static boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellString(row, cell.getColumnIndex());
                if (val != null && !val.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }
}
