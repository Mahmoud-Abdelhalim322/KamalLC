package com.kamal.lc.imports.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.imports.dto.DeductionImportPreviewResponse;
import com.kamal.lc.imports.entity.ImportBatch;
import com.kamal.lc.imports.entity.ImportBatchType;
import com.kamal.lc.imports.repository.ImportBatchRepository;
import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Imports supplier (Ninja) deductions with support for 3 matching scopes:
 * <ul>
 *   <li><b>Scope 1 (Ninja ID)</b>: ID - NINJA + shift date</li>
 *   <li><b>Scope 2 (KSLSC ID)</b>: ID-KSLSC + shift date</li>
 *   <li><b>Scope 3 (Phone / National ID)</b>: Phone or Iqama + shift date</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierDeductionImportService {

    private final ShiftRepository shiftRepo;
    private final CaptainRepository captainRepo;
    private final ImportBatchRepository batchRepo;
    private final ImportFileDetector fileDetector;

    @Data
    @Builder
    public static class ImportResult {
        private int totalRows;
        private int matched;
        private int unmatched;
        private List<String> unmatchedRows;
    }

    @Data
    @Builder
    private static class DeductionMatchingResult {
        private String scope; // NINJA_ID, KSLSC_ID, PHONE_OR_NATIONAL_ID, UNMATCHED
        private List<Shift> shifts;
        private Captain captain;
    }

    @Transactional
    public DeductionImportPreviewResponse previewDeductions(MultipartFile file) {
        fileDetector.requireNot(file, ImportBatchType.CAPTAINS);

        String fileHash = computeHash(file);
        if (batchRepo.existsByFileHashAndBatchTypeAndStatus(fileHash, ImportBatchType.SUPPLIER_DEDUCTIONS, "COMMITTED")) {
            throw new BusinessException("DUPLICATE_FILE", "هذا الملف تم استيراده وتأكيده من قبل");
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("EMPTY_FILE", "الملف لا يحتوي على أي بيانات");
            }

            int headerIdx = findHeaderRowIndex(sheet);
            Row headerRow = sheet.getRow(headerIdx);
            if (headerRow == null) {
                throw new BusinessException("NO_HEADER", "الملف لا يحتوي على صف عناوين");
            }

            ColumnIndices cols = detectColumns(headerRow);
            if (cols.dateCol == -1 || cols.amountCol == -1 || (cols.ninjaIdCol == -1 && cols.kslscCol == -1 && cols.phoneOrIqamaCol == -1)) {
                throw new BusinessException("MISSING_COLUMNS",
                        "يجب أن يحتوي الملف على عمود التاريخ، عمود مبلغ الخصم، وعلى الأقل أحد معرفات الكابتن (ID - NINJA، ID-KSLSC، أو الهاتف/الهوية)");
            }

            List<DeductionImportPreviewResponse.DeductionRowPreview> rowPreviews = new ArrayList<>();
            List<DeductionImportPreviewResponse.ImportIssue> issues = new ArrayList<>();

            int totalRows = 0;
            int matchedRows = 0;
            int unmatchedRows = 0;
            BigDecimal totalDeductionAmount = BigDecimal.ZERO;

            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                totalRows++;
                int rowNumber = r + 1;

                String ninjaId = cols.ninjaIdCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.ninjaIdCol))).trim() : "";
                String kslscId = cols.kslscCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.kslscCol))).trim() : "";
                String phoneOrIqama = cols.phoneOrIqamaCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.phoneOrIqamaCol))).trim() : "";
                String captainName = cols.nameCol != -1 ? cellText(row.getCell(cols.nameCol)).trim() : "";
                String reason = cols.reasonCol != -1 ? cellText(row.getCell(cols.reasonCol)).trim() : "";

                LocalDate shiftDate = cellDate(row.getCell(cols.dateCol));
                BigDecimal amount = cellDecimal(row.getCell(cols.amountCol));

                if (shiftDate == null) {
                    issues.add(DeductionImportPreviewResponse.ImportIssue.builder()
                            .rowNumber(rowNumber)
                            .severity("ERROR")
                            .issueCode("INVALID_DATE")
                            .message("تاريخ الشيفت مفقود أو غير صالح")
                            .build());
                    unmatchedRows++;
                    rowPreviews.add(buildUnmatchedRow(rowNumber, ninjaId, kslscId, captainName, shiftDate, amount, reason, "تاريخ الشيفت غير صالح"));
                    continue;
                }

                if (amount == null) {
                    issues.add(DeductionImportPreviewResponse.ImportIssue.builder()
                            .rowNumber(rowNumber)
                            .severity("ERROR")
                            .issueCode("INVALID_AMOUNT")
                            .message("مبلغ الخصم مفقود")
                            .build());
                    unmatchedRows++;
                    rowPreviews.add(buildUnmatchedRow(rowNumber, ninjaId, kslscId, captainName, shiftDate, null, reason, "مبلغ الخصم غير صالح"));
                    continue;
                }

                BigDecimal absAmount = amount.abs();

                DeductionMatchingResult match = matchRow(ninjaId, kslscId, phoneOrIqama, shiftDate);
                if (match.getShifts().isEmpty()) {
                    unmatchedRows++;
                    issues.add(DeductionImportPreviewResponse.ImportIssue.builder()
                            .rowNumber(rowNumber)
                            .severity("WARNING")
                            .issueCode("UNMATCHED_SHIFT")
                            .message("لم يتم العثور على شيفت مطابق بتاريخ " + shiftDate + " للمعرف المرفق")
                            .build());
                    rowPreviews.add(buildUnmatchedRow(rowNumber, ninjaId, kslscId, captainName, shiftDate, absAmount, reason, "لم يتم العثور على شيفت مطابق"));
                } else {
                    matchedRows++;
                    totalDeductionAmount = totalDeductionAmount.add(absAmount);
                    rowPreviews.add(DeductionImportPreviewResponse.DeductionRowPreview.builder()
                            .rowNumber(rowNumber)
                            .ninjaId(ninjaId)
                            .kslscId(kslscId.isEmpty() && match.getCaptain() != null ? match.getCaptain().getKamalCode() : kslscId)
                            .captainName(captainName.isEmpty() && match.getCaptain() != null ? match.getCaptain().getFullNameAr() : captainName)
                            .shiftDate(shiftDate)
                            .amount(absAmount)
                            .reason(reason)
                            .matched(true)
                            .matchingScope(match.getScope())
                            .matchedShiftCount(match.getShifts().size())
                            .statusMessage("مطابق على " + match.getShifts().size() + " شيفت (" + scopeLabel(match.getScope()) + ")")
                            .build());
                }
            }

            ImportBatch batch = batchRepo.findFirstByFileHashAndBatchTypeAndStatusOrderByUploadedAtDesc(
                            fileHash, ImportBatchType.SUPPLIER_DEDUCTIONS, "PREVIEW")
                    .orElseGet(() -> ImportBatch.builder()
                            .fileName(file.getOriginalFilename())
                            .fileHash(fileHash)
                            .batchType(ImportBatchType.SUPPLIER_DEDUCTIONS)
                            .status("PREVIEW")
                            .build());

            batch.setRowCount(totalRows);
            batch.setImportedCount(matchedRows);
            batch.setSkippedCount(unmatchedRows);
            batch.setErrorCount((int) issues.stream().filter(i -> "ERROR".equals(i.getSeverity())).count());
            batch.setWarningCount((int) issues.stream().filter(i -> "WARNING".equals(i.getSeverity())).count());
            batch = batchRepo.save(batch);

            return DeductionImportPreviewResponse.builder()
                    .batchId(batch.getId())
                    .fileName(file.getOriginalFilename())
                    .totalRows(totalRows)
                    .matchedRows(matchedRows)
                    .unmatchedRows(unmatchedRows)
                    .totalDeductionAmount(totalDeductionAmount)
                    .issues(issues)
                    .rows(rowPreviews)
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to preview supplier deductions: {}", e.getMessage(), e);
            throw new BusinessException("PREVIEW_ERROR", "فشل في تحليل ملف خصومات المورد: " + e.getMessage());
        }
    }

    @Transactional
    public DeductionImportPreviewResponse commitDeductions(UUID batchId, MultipartFile file) {
        ImportBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "دفعة الاستيراد غير موجودة"));

        if (batch.getBatchType() != ImportBatchType.SUPPLIER_DEDUCTIONS) {
            throw new BusinessException("INVALID_BATCH", "نوع الدفعة غير مطابق لخصومات المورد");
        }

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int headerIdx = findHeaderRowIndex(sheet);
            Row headerRow = sheet.getRow(headerIdx);
            ColumnIndices cols = detectColumns(headerRow);

            int totalRows = 0;
            int matchedRows = 0;
            int unmatchedRows = 0;
            BigDecimal totalDeductionAmount = BigDecimal.ZERO;
            List<DeductionImportPreviewResponse.DeductionRowPreview> rowPreviews = new ArrayList<>();
            List<DeductionImportPreviewResponse.ImportIssue> issues = new ArrayList<>();

            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                totalRows++;
                int rowNumber = r + 1;

                String ninjaId = cols.ninjaIdCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.ninjaIdCol))).trim() : "";
                String kslscId = cols.kslscCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.kslscCol))).trim() : "";
                String phoneOrIqama = cols.phoneOrIqamaCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.phoneOrIqamaCol))).trim() : "";
                String captainName = cols.nameCol != -1 ? cellText(row.getCell(cols.nameCol)).trim() : "";
                String reason = cols.reasonCol != -1 ? cellText(row.getCell(cols.reasonCol)).trim() : "";

                LocalDate shiftDate = cellDate(row.getCell(cols.dateCol));
                BigDecimal amount = cellDecimal(row.getCell(cols.amountCol));

                if (shiftDate == null || amount == null) {
                    unmatchedRows++;
                    continue;
                }

                BigDecimal absAmount = amount.abs();

                DeductionMatchingResult match = matchRow(ninjaId, kslscId, phoneOrIqama, shiftDate);
                if (match.getShifts().isEmpty()) {
                    unmatchedRows++;
                } else {
                    matchedRows++;
                    totalDeductionAmount = totalDeductionAmount.add(absAmount);
                    for (Shift shift : match.getShifts()) {
                        BigDecimal current = shift.getSupplierDeduction() != null ? shift.getSupplierDeduction() : BigDecimal.ZERO;
                        shift.setSupplierDeduction(current.add(absAmount));
                        if (reason != null && !reason.trim().isEmpty()) {
                            String existing = shift.getSupplierDeductionReason();
                            if (existing == null || existing.isBlank()) {
                                shift.setSupplierDeductionReason(reason.trim());
                            } else if (!existing.contains(reason.trim())) {
                                shift.setSupplierDeductionReason(existing + " | " + reason.trim());
                            }
                        }
                        shiftRepo.save(shift);
                    }
                    rowPreviews.add(DeductionImportPreviewResponse.DeductionRowPreview.builder()
                            .rowNumber(rowNumber)
                            .ninjaId(ninjaId)
                            .kslscId(kslscId)
                            .captainName(captainName)
                            .shiftDate(shiftDate)
                            .amount(absAmount)
                            .reason(reason)
                            .matched(true)
                            .matchingScope(match.getScope())
                            .matchedShiftCount(match.getShifts().size())
                            .statusMessage("تم تطبيق الخصم بنجاح")
                            .build());
                }
            }

            batch.setStatus("COMMITTED");
            batch.setImportedCount(matchedRows);
            batch.setSkippedCount(unmatchedRows);
            batchRepo.save(batch);

            log.info("Committed supplier deduction batch {}: {} total, {} committed, {} skipped",
                    batchId, totalRows, matchedRows, unmatchedRows);

            return DeductionImportPreviewResponse.builder()
                    .batchId(batch.getId())
                    .fileName(batch.getFileName())
                    .totalRows(totalRows)
                    .matchedRows(matchedRows)
                    .unmatchedRows(unmatchedRows)
                    .totalDeductionAmount(totalDeductionAmount)
                    .issues(issues)
                    .rows(rowPreviews)
                    .build();

        } catch (Exception e) {
            log.error("Failed to commit supplier deduction batch {}: {}", batchId, e.getMessage(), e);
            throw new BusinessException("COMMIT_ERROR", "فشل في تأكيد استيراد الخصومات: " + e.getMessage());
        }
    }

    /** Legacy method used by ShiftController. */
    @Transactional
    public ImportResult importDeductions(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("EMPTY_FILE", "الملف لا يحتوي على أي بيانات");
            }
            int headerIdx = findHeaderRowIndex(sheet);
            Row headerRow = sheet.getRow(headerIdx);
            if (headerRow == null) {
                throw new BusinessException("NO_HEADER", "الملف لا يحتوي على صف عناوين");
            }
            ColumnIndices cols = detectColumns(headerRow);
            int totalRows = 0, matched = 0, unmatched = 0;
            List<String> unmatchedRows = new ArrayList<>();

            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                String ninjaId = cols.ninjaIdCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.ninjaIdCol))).trim() : "";
                String kslscId = cols.kslscCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.kslscCol))).trim() : "";
                String phoneOrIqama = cols.phoneOrIqamaCol != -1 ? ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.phoneOrIqamaCol))).trim() : "";
                String reason = cols.reasonCol != -1 ? cellText(row.getCell(cols.reasonCol)).trim() : "";
                LocalDate shiftDate = cellDate(row.getCell(cols.dateCol));
                BigDecimal amount = cellDecimal(row.getCell(cols.amountCol));

                if (shiftDate == null || amount == null) continue;

                BigDecimal absAmount = amount.abs();

                totalRows++;
                DeductionMatchingResult match = matchRow(ninjaId, kslscId, phoneOrIqama, shiftDate);
                if (match.getShifts().isEmpty()) {
                    unmatched++;
                    unmatchedRows.add("Row " + (r + 1) + ": " + ninjaId + " / " + shiftDate);
                    continue;
                }
                for (Shift shift : match.getShifts()) {
                    BigDecimal current = shift.getSupplierDeduction() != null ? shift.getSupplierDeduction() : BigDecimal.ZERO;
                    shift.setSupplierDeduction(current.add(absAmount));
                    if (reason != null && !reason.trim().isEmpty()) {
                        String existing = shift.getSupplierDeductionReason();
                        if (existing == null || existing.isBlank()) {
                            shift.setSupplierDeductionReason(reason.trim());
                        } else if (!existing.contains(reason.trim())) {
                            shift.setSupplierDeductionReason(existing + " | " + reason.trim());
                        }
                    }
                    shiftRepo.save(shift);
                }
                matched++;
            }

            return ImportResult.builder()
                    .totalRows(totalRows)
                    .matched(matched)
                    .unmatched(unmatched)
                    .unmatchedRows(unmatchedRows)
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to import supplier deductions: {}", e.getMessage(), e);
            throw new BusinessException("IMPORT_ERROR", "فشل في استيراد ملف خصومات المورد: " + e.getMessage());
        }
    }

    private DeductionMatchingResult matchRow(String ninjaId, String kslscId, String phoneOrIqama, LocalDate shiftDate) {
        // Scope 1: Ninja ID
        if (!ninjaId.isEmpty()) {
            List<Shift> shifts = shiftRepo.findByNinjaCaptainIdAndShiftDate(ninjaId, shiftDate);
            if (!shifts.isEmpty()) {
                Captain captain = shifts.get(0).getCaptain();
                return DeductionMatchingResult.builder()
                        .scope("NINJA_ID")
                        .shifts(shifts)
                        .captain(captain)
                        .build();
            }
        }

        // Scope 2: KSLSC ID
        if (!kslscId.isEmpty()) {
            Optional<Captain> captainOpt = captainRepo.findByKamalCode(kslscId);
            if (captainOpt.isPresent()) {
                List<Shift> shifts = shiftRepo.findByCaptainIdAndShiftDate(captainOpt.get().getId(), shiftDate);
                if (!shifts.isEmpty()) {
                    return DeductionMatchingResult.builder()
                            .scope("KSLSC_ID")
                            .shifts(shifts)
                            .captain(captainOpt.get())
                            .build();
                }
            }
        }

        // Scope 3: Phone / National ID (Iqama)
        if (!phoneOrIqama.isEmpty()) {
            Optional<Captain> captainOpt = captainRepo.findByIqamaNumber(phoneOrIqama)
                    .or(() -> captainRepo.findByPhone(phoneOrIqama));
            if (captainOpt.isPresent()) {
                List<Shift> shifts = shiftRepo.findByCaptainIdAndShiftDate(captainOpt.get().getId(), shiftDate);
                if (!shifts.isEmpty()) {
                    return DeductionMatchingResult.builder()
                            .scope("PHONE_OR_NATIONAL_ID")
                            .shifts(shifts)
                            .captain(captainOpt.get())
                            .build();
                }
            }
        }

        return DeductionMatchingResult.builder()
                .scope("UNMATCHED")
                .shifts(Collections.emptyList())
                .captain(null)
                .build();
    }

    private String scopeLabel(String scope) {
        return switch (scope) {
            case "NINJA_ID" -> "مطابقة برقم المورد Ninja ID";
            case "KSLSC_ID" -> "مطابقة برقم KSLSC";
            case "PHONE_OR_NATIONAL_ID" -> "مطابقة برقم الهاتف/الهوية";
            default -> "غير مطابق";
        };
    }

    private DeductionImportPreviewResponse.DeductionRowPreview buildUnmatchedRow(
            int rowNumber, String ninjaId, String kslscId, String name, LocalDate shiftDate, BigDecimal amount, String reason, String status) {
        return DeductionImportPreviewResponse.DeductionRowPreview.builder()
                .rowNumber(rowNumber)
                .ninjaId(ninjaId)
                .kslscId(kslscId)
                .captainName(name)
                .shiftDate(shiftDate)
                .amount(amount != null ? amount.abs() : BigDecimal.ZERO)
                .reason(reason)
                .matched(false)
                .matchingScope("UNMATCHED")
                .matchedShiftCount(0)
                .statusMessage(status)
                .build();
    }

    private static String computeHash(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return DigestUtils.sha256Hex(is);
        } catch (Exception e) {
            throw new BusinessException("HASH_ERROR", "فشل حساب البصمة الرقمية للملف");
        }
    }

    private static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !cellText(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static int findHeaderRowIndex(Sheet sheet) {
        if (sheet == null) return 0;
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 10); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                String raw = cellText(cell);
                if (raw.isBlank()) continue;
                String normalized = ExcelCellSupport.normalizeHeader(raw);
                if (normalized.contains("ninja") || normalized.contains("kslsc") || normalized.contains("ksc") ||
                        normalized.contains("تاريخ") || normalized.contains("date") || normalized.contains("خصم") ||
                        normalized.contains("سعر") || normalized.contains("مبلغ") || normalized.contains("قيمة") ||
                        normalized.contains("غرامة") || normalized.contains("deduction") || normalized.contains("penalty")) {
                    return i;
                }
            }
        }
        return 0;
    }

    private static ColumnIndices detectColumns(Row headerRow) {
        ColumnIndices cols = new ColumnIndices();
        if (headerRow == null) return cols;

        for (Cell cell : headerRow) {
            String raw = cellText(cell);
            if (raw.isBlank()) continue;

            String normalized = ExcelCellSupport.normalizeHeader(raw);
            int idx = cell.getColumnIndex();

            if (normalized.contains("ninja") || normalized.contains("id - ninja") || normalized.equals("id-ninja") || normalized.equals("id ninja")) {
                if (cols.ninjaIdCol == -1) cols.ninjaIdCol = idx;
            } else if (normalized.contains("kslsc") || normalized.contains("ksc") || normalized.contains("id-kslsc")) {
                if (cols.kslscCol == -1) cols.kslscCol = idx;
            } else if (normalized.contains("تاريخ") || normalized.contains("date") || normalized.contains("يوم")) {
                if (cols.dateCol == -1) cols.dateCol = idx;
            } else if (normalized.contains("سبب") || normalized.contains("بيان") || normalized.contains("ملاحظات") || normalized.contains("reason") || normalized.contains("note") || normalized.contains("تفاصيل") || normalized.contains("شرح")) {
                if (cols.reasonCol == -1) cols.reasonCol = idx;
            } else if (normalized.contains("خصم") || normalized.contains("سعر") || normalized.contains("مبلغ") || normalized.contains("قيمة") || normalized.contains("deduction") || normalized.contains("amount") || normalized.contains("penalty") || normalized.contains("غرامة") || normalized.contains("fine")) {
                if (cols.amountCol == -1) cols.amountCol = idx;
            } else if (normalized.contains("اسم") || normalized.contains("name") || normalized.contains("كابتن")) {
                if (cols.nameCol == -1) cols.nameCol = idx;
            } else if (normalized.contains("هاتف") || normalized.contains("phone") || normalized.contains("إقامة") || normalized.contains("اقامة") || normalized.contains("هوية") || normalized.contains("iqama") || normalized.contains("جوال")) {
                if (cols.phoneOrIqamaCol == -1) cols.phoneOrIqamaCol = idx;
            }
        }
        return cols;
    }

    private static class ColumnIndices {
        int ninjaIdCol = -1;
        int kslscCol = -1;
        int phoneOrIqamaCol = -1;
        int dateCol = -1;
        int amountCol = -1;
        int nameCol = -1;
        int reasonCol = -1;
    }

    private static String cellText(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) && !Double.isInfinite(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static LocalDate cellDate(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType() == CellType.FORMULA
                    ? cell.getCachedFormulaResultType()
                    : cell.getCellType();

            if (type == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate();
                }
                double d = cell.getNumericCellValue();
                if (d > 1000) {
                    return DateUtil.getLocalDateTime(d).toLocalDate();
                }
            }

            String text = cellText(cell).trim();
            if (text.isEmpty()) return null;
            text = ExcelCellSupport.toWesternDigits(text);

            if (text.contains(" ")) {
                text = text.substring(0, text.indexOf(" ")).trim();
            } else if (text.contains("T")) {
                text = text.substring(0, text.indexOf("T")).trim();
            }

            if (text.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$")) {
                String[] parts = text.split("-");
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }

            if (text.matches("^\\d{4}/\\d{1,2}/\\d{1,2}$")) {
                String[] parts = text.split("/");
                return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }

            if (text.matches("^\\d{1,2}/\\d{1,2}/\\d{4}$")) {
                String[] parts = text.split("/");
                return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            }

            if (text.matches("^\\d{1,2}-\\d{1,2}-\\d{4}$")) {
                String[] parts = text.split("-");
                return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            }

            try {
                double serial = Double.parseDouble(text);
                if (serial > 1000) {
                    return DateUtil.getLocalDateTime(serial).toLocalDate();
                }
            } catch (NumberFormatException ignored) {}

        } catch (Exception e) {
            log.debug("Failed to parse date cell: {}", e.getMessage());
        }
        return null;
    }

    private static BigDecimal cellDecimal(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType() == CellType.FORMULA
                    ? cell.getCachedFormulaResultType()
                    : cell.getCellType();

            if (type == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            String text = cellText(cell).trim();
            if (!text.isEmpty()) {
                text = ExcelCellSupport.toWesternDigits(text).replace(",", "").replace("%", "").trim();
                return new BigDecimal(text);
            }
        } catch (Exception e) {
            // ignore parse errors
        }
        return null;
    }
}
