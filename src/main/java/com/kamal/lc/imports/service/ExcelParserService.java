package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.ExcelCaptainRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Parses captain Excel files using Apache POI.
 * Flexibly matches column headers (Arabic/English, with/without spaces).
 */
@Slf4j
@Service
public class ExcelParserService {

    /** Date layouts seen in the sheets, tried in order against text cells. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"));

    /** Arabic labels of the date columns, used in the "unreadable date" warning. */
    private static final Map<String, String> FIELD_LABELS_AR = Map.of(
            "dateOfBirth", "تاريخ الميلاد",
            "iqamaExpiry", "انتهاء الإقامة",
            "passportExpiry", "انتهاء الجواز",
            "licenceExpiry", "انتهاء الرخصة",
            "workPermitExpiry", "انتهاء رخصة العمل",
            "insuranceExpiry", "انتهاء التأمين الطبي");

    // Column name → field mapping (normalized for case/spelling-insensitive matching)
    private static final Map<String, String> COLUMN_MAP;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        // kamalCode
        m.put("id-kslsc", "kamalCode");
        m.put("id - kslsc", "kamalCode");
        m.put("kslsc", "kamalCode");
        m.put("كود كمال", "kamalCode");
        // ninjaId
        m.put("id - ninja", "ninjaId");
        m.put("id-ninja", "ninjaId");
        m.put("ninja", "ninjaId");
        m.put("معرف نينجا", "ninjaId");
        // captainName
        m.put("اسم الكابتن", "captainName");
        m.put("الاسم", "captainName");
        m.put("اسم", "captainName");
        m.put("name", "captainName");
        // phone (optional)
        m.put("رقم الهاتف", "phone");
        m.put("الهاتف", "phone");
        m.put("الجوال", "phone");
        m.put("رقم الجوال", "phone");
        m.put("phone", "phone");
        // nationality (optional)
        m.put("الجنسية", "nationality");
        m.put("nationality", "nationality");
        // dateOfBirth (optional)
        m.put("تاريخ الميلاد", "dateOfBirth");
        m.put("الميلاد", "dateOfBirth");
        m.put("date of birth", "dateOfBirth");
        m.put("dob", "dateOfBirth");
        // email (optional)
        m.put("البريد الالكتروني", "email");
        m.put("البريد", "email");
        m.put("الايميل", "email");
        m.put("email", "email");
        // iqama (optional)
        m.put("رقم الاقامة", "iqamaNumber");
        m.put("الاقامة", "iqamaNumber");
        m.put("iqama", "iqamaNumber");
        m.put("انتهاء الاقامة", "iqamaExpiry");
        m.put("تاريخ انتهاء الاقامة", "iqamaExpiry");
        m.put("iqama expiry", "iqamaExpiry");
        // passport (optional)
        m.put("رقم الجواز", "passportNumber");
        m.put("الجواز", "passportNumber");
        m.put("جواز السفر", "passportNumber");
        m.put("passport", "passportNumber");
        m.put("انتهاء الجواز", "passportExpiry");
        m.put("تاريخ انتهاء الجواز", "passportExpiry");
        m.put("passport expiry", "passportExpiry");
        // driving licence (optional)
        m.put("رقم الرخصة", "drivingLicenceNo");
        m.put("الرخصة", "drivingLicenceNo");
        m.put("رخصة القيادة", "drivingLicenceNo");
        m.put("licence", "drivingLicenceNo");
        m.put("license", "drivingLicenceNo");
        m.put("انتهاء الرخصة", "licenceExpiry");
        m.put("تاريخ انتهاء الرخصة", "licenceExpiry");
        m.put("تاريخ انتهاء الرخصه", "licenceExpiry");
        m.put("licence expiry", "licenceExpiry");
        m.put("license expiry", "licenceExpiry");
        // work permit (optional) — the Qiwa permit, not the driving licence above
        m.put("تاريخ انتهاء رخصة العمل", "workPermitExpiry");
        m.put("انتهاء رخصة العمل", "workPermitExpiry");
        m.put("رخصة العمل", "workPermitExpiry");
        m.put("work permit expiry", "workPermitExpiry");
        m.put("work permit", "workPermitExpiry");
        // medical insurance (optional)
        m.put("رقم التأمين", "insuranceNumber");
        m.put("insurance no", "insuranceNumber");
        m.put("insurance number", "insuranceNumber");
        m.put("اسم شركة التأمين الطبي", "insuranceCompany");
        m.put("شركة التأمين", "insuranceCompany");
        m.put("insurance company", "insuranceCompany");
        m.put("انتهاء التأمين", "insuranceExpiry");
        m.put("تاريخ انتهاء التأمين", "insuranceExpiry");
        m.put("انتهاء التأمين الطبي", "insuranceExpiry");
        m.put("insurance expiry", "insuranceExpiry");

        // Keys are stored normalized so lookups only ever normalize the sheet header.
        Map<String, String> normalized = new LinkedHashMap<>();
        m.forEach((key, field) -> normalized.put(normalizeHeader(key), field));
        COLUMN_MAP = Collections.unmodifiableMap(normalized);
    }

    /** Normalised header → field, exposed so {@link ImportFileDetector} can score a sheet. */
    static Map<String, String> columnMap() {
        return COLUMN_MAP;
    }

    /**
     * Parse an Excel file and return the list of captain rows.
     *
     * @param file the uploaded Excel file
     * @return list of parsed rows (may contain rows with null fields for optional columns)
     * @throws ExcelParseException if the file is unreadable or missing required columns
     */
    public List<ExcelCaptainRow> parse(MultipartFile file) {
        // WorkbookFactory handles both .xlsx (XSSF) and legacy .xls (HSSF)
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ExcelParseException("الملف لا يحتوي على أي أوراق عمل");
            }

            // 1. Find header row and map columns
            Map<String, Integer> fieldToCol = detectHeaders(sheet);

            // 2. Validate required columns
            if (!fieldToCol.containsKey("kamalCode")) {
                throw new ExcelParseException("عمود 'ID-KSLSC' غير موجود في الملف");
            }
            if (!fieldToCol.containsKey("ninjaId")) {
                throw new ExcelParseException("عمود 'ID - NINJA' غير موجود في الملف");
            }
            if (!fieldToCol.containsKey("captainName")) {
                throw new ExcelParseException("عمود 'اسم الكابتن' غير موجود في الملف");
            }

            // 3. Parse data rows
            int headerRowIdx = findHeaderRowIndex(sheet);
            List<ExcelCaptainRow> rows = new ArrayList<>();

            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) continue;

                List<String> warnings = new ArrayList<>();

                String kamalCode = text(row, fieldToCol, "kamalCode");
                String ninjaId = text(row, fieldToCol, "ninjaId");
                String captainName = text(row, fieldToCol, "captainName");

                rows.add(ExcelCaptainRow.builder()
                        .rowNumber(i + 1) // 1-based for user display
                        .kamalCode(kamalCode != null ? kamalCode.toUpperCase() : null)
                        .ninjaId(ninjaId)
                        .captainName(captainName)
                        .phone(text(row, fieldToCol, "phone"))
                        .nationality(text(row, fieldToCol, "nationality"))
                        .dateOfBirth(date(row, fieldToCol, "dateOfBirth", warnings))
                        .email(text(row, fieldToCol, "email"))
                        .iqamaNumber(text(row, fieldToCol, "iqamaNumber"))
                        .iqamaExpiry(date(row, fieldToCol, "iqamaExpiry", warnings))
                        .passportNumber(text(row, fieldToCol, "passportNumber"))
                        .passportExpiry(date(row, fieldToCol, "passportExpiry", warnings))
                        .drivingLicenceNo(text(row, fieldToCol, "drivingLicenceNo"))
                        .licenceExpiry(date(row, fieldToCol, "licenceExpiry", warnings))
                        .workPermitExpiry(date(row, fieldToCol, "workPermitExpiry", warnings))
                        .insuranceNumber(text(row, fieldToCol, "insuranceNumber"))
                        .insuranceCompany(text(row, fieldToCol, "insuranceCompany"))
                        .insuranceExpiry(date(row, fieldToCol, "insuranceExpiry", warnings))
                        .warnings(warnings)
                        .build());
            }

            log.info("Parsed {} data rows from '{}'", rows.size(), file.getOriginalFilename());
            return rows;

        } catch (ExcelParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Excel file: {}", e.getMessage(), e);
            throw new ExcelParseException("فشل في قراءة الملف: " + e.getMessage());
        }
    }

    private int findHeaderRowIndex(Sheet sheet) {
        return ExcelCellSupport.findHeaderRowIndex(sheet, COLUMN_MAP);
    }

    private Map<String, Integer> detectHeaders(Sheet sheet) {
        Map<String, Integer> fieldToCol = ExcelCellSupport.detectHeaders(sheet, COLUMN_MAP);
        if (fieldToCol.isEmpty()) {
            // No header matched at all — say what was looked for, not just that it failed.
            throw new ExcelParseException(
                    "لم يتم التعرف على أي عمود في الملف — يجب أن يحتوي صف العناوين على "
                            + "'ID-KSLSC' و 'ID - NINJA' و 'اسم الكابتن'");
        }
        return fieldToCol;
    }

    private static String normalizeHeader(String raw) {
        return ExcelCellSupport.normalizeHeader(raw);
    }

    private String text(Row row, Map<String, Integer> fieldToCol, String field) {
        return ExcelCellSupport.text(row, fieldToCol, field);
    }

    /** Reads a date column, adding an Arabic warning (and returning null) when it cannot be read. */
    private LocalDate date(Row row, Map<String, Integer> fieldToCol, String field, List<String> warnings) {
        Integer col = fieldToCol.get(field);
        if (col == null) return null;

        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        // Real Excel dates are numeric cells with a date format applied.
        if (type == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }

        String raw = getCellString(row, col);
        if (raw == null || raw.trim().isEmpty()) return null;

        LocalDate parsed = parseDate(raw.trim());
        if (parsed == null) {
            warnings.add("تعذّرت قراءة " + FIELD_LABELS_AR.getOrDefault(field, field) + " (" + raw.trim() + ")");
        }
        return parsed;
    }

    private LocalDate parseDate(String raw) {
        String value = toWesternDigits(raw).replaceAll("\\s+", "");
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // try the next layout
            }
        }
        return null;
    }

    private static String toWesternDigits(String value) {
        return ExcelCellSupport.toWesternDigits(value);
    }

    private String getCellString(Row row, int colIdx) {
        return ExcelCellSupport.getCellString(row, colIdx);
    }

    private boolean isEmptyRow(Row row) {
        return ExcelCellSupport.isEmptyRow(row);
    }

    /** Thrown when the Excel file cannot be parsed. */
    public static class ExcelParseException extends RuntimeException {
        public ExcelParseException(String message) {
            super(message);
        }
    }
}
