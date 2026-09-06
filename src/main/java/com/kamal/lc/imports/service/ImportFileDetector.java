package com.kamal.lc.imports.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.imports.entity.ImportBatchType;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Optional;

/**
 * Works out which importer an uploaded file belongs to, by scoring its header row
 * against parsers' column maps and specific domain indicators.
 *
 * <p>It exists for one reason: the files share nothing but a file extension, and
 * dropping one on the wrong uploader used to fail with confusing error messages.
 * Detection turns that into a message naming the right screen.
 *
 * <p>Advisory only. A file it cannot classify is passed through to the parser, which
 * is still the authority on whether the file can actually be read.
 */
@Slf4j
@Service
public class ImportFileDetector {

    /**
     * Below this many recognised headers a sheet is not confidently either kind —
     * a captains file, for instance, contributes one match to the shift map because
     * both know the "ID - NINJA" column.
     */
    private static final int MIN_MATCHES = 3;

    public Optional<ImportBatchType> detect(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return Optional.empty();

            if (isVacationCommission(sheet)) {
                return Optional.of(ImportBatchType.VACATION_COMMISSION);
            }

            if (isSupplierDeductions(sheet)) {
                return Optional.of(ImportBatchType.SUPPLIER_DEDUCTIONS);
            }

            int captains = ExcelCellSupport.detectHeaders(sheet, ExcelParserService.columnMap()).size();
            int shifts = ExcelCellSupport.detectHeaders(sheet, NinjaShiftParser.columnMap()).size();

            if (shifts >= MIN_MATCHES && shifts > captains) return Optional.of(ImportBatchType.SHIFTS);
            if (captains >= MIN_MATCHES && captains > shifts) return Optional.of(ImportBatchType.CAPTAINS);
            return Optional.empty();

        } catch (Exception e) {
            log.debug("Could not classify '{}': {}", file.getOriginalFilename(), e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isVacationCommission(Sheet sheet) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 10); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            boolean hasDays = false;
            boolean hasCaptainId = false;

            for (Cell cell : row) {
                String val = ExcelCellSupport.getCellString(row, cell.getColumnIndex());
                if (val == null || val.isBlank()) continue;

                String normalized = ExcelCellSupport.normalizeHeader(val);
                if (normalized.contains("days")) {
                    hasDays = true;
                }
                if (normalized.contains("captain id") || (normalized.contains("captain") && normalized.contains("id"))) {
                    hasCaptainId = true;
                }
            }

            if (hasDays && hasCaptainId) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupplierDeductions(Sheet sheet) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 10); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            boolean hasAmount = false;
            boolean hasDate = false;
            boolean hasCaptainInfo = false;

            for (Cell cell : row) {
                String val = ExcelCellSupport.getCellString(row, cell.getColumnIndex());
                if (val == null || val.isBlank()) continue;

                String normalized = ExcelCellSupport.normalizeHeader(val);

                if (!normalized.startsWith("سبب") && !normalized.contains("سبب الخصم")) {
                    if (normalized.contains("خصم") || normalized.contains("سعر") || normalized.contains("مبلغ") ||
                            normalized.contains("قيمة") || normalized.contains("deduction") || normalized.contains("amount") ||
                            normalized.contains("penalty") || normalized.contains("غرامة")) {
                        hasAmount = true;
                    }
                }

                if (normalized.contains("تاريخ") || normalized.contains("date") || normalized.contains("يوم")) {
                    hasDate = true;
                }

                if (normalized.contains("ninja") || normalized.contains("kslsc") || normalized.contains("ksc") ||
                        normalized.contains("هاتف") || normalized.contains("phone") || normalized.contains("إقامة") ||
                        normalized.contains("اقامة") || normalized.contains("هوية") || normalized.contains("iqama") ||
                        normalized.contains("جوال") || normalized.contains("اسم") || normalized.contains("كابتن") ||
                        normalized.contains("name")) {
                    hasCaptainInfo = true;
                }
            }

            if (hasAmount && (hasDate || hasCaptainInfo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rejects a file that plainly belongs to the wrong importer, naming the screen the
     * user should have used. Silent when the file is the expected kind or unclassifiable.
     */
    public void requireNot(MultipartFile file, ImportBatchType wrongKind) {
        detect(file)
                .filter(kind -> kind == wrongKind)
                .ifPresent(kind -> {
                    throw new BusinessException("WRONG_IMPORT_TYPE", messageFor(kind));
                });
    }

    private String messageFor(ImportBatchType detected) {
        return switch (detected) {
            case SHIFTS -> "هذا الملف تقرير شيفتات من المورد — استورده من تبويب «استيراد تقارير الموردين»";
            case CAPTAINS -> "هذا الملف يحتوي بيانات كباتن ومعرفات — استورده من تبويب «استيراد بيانات الكباتن والموردين»";
            case SUPPLIER_DEDUCTIONS -> "هذا الملف يحتوي خصومات المورد — استورده من تبويب «استيراد خصومات المورد»";
            case VACATION_COMMISSION -> "هذا الملف يحتوي عمولة أوقات الإجازة — استورده من تبويب «أستيراد عمولة اوقات الاجازة»";
        };
    }
}

