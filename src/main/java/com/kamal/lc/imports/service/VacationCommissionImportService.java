package com.kamal.lc.imports.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.security.TenantContext;
import com.kamal.lc.imports.dto.VacationCommissionImportPreviewResponse;
import com.kamal.lc.imports.entity.ImportBatch;
import com.kamal.lc.imports.entity.ImportBatchType;
import com.kamal.lc.imports.entity.VacationCommission;
import com.kamal.lc.imports.repository.ImportBatchRepository;
import com.kamal.lc.imports.repository.VacationCommissionRepository;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import com.kamal.lc.supplier.entity.SupplierIdSlot;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import com.kamal.lc.supplier.repository.SupplierIdSlotRepository;
import com.kamal.lc.supplier.repository.SupplierRepository;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.EntityManager;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacationCommissionImportService {

    private final VacationCommissionRepository vacationCommissionRepo;
    private final CaptainRepository captainRepo;
    private final SupplierRepository supplierRepo;
    private final SupplierIdSlotRepository slotRepo;
    private final SupplierIdAssignmentRepository assignmentRepo;
    private final ImportBatchRepository batchRepo;
    private final ImportFileDetector fileDetector;
    private final EntityManager entityManager;

    @Data
    @Builder
    private static class CaptainMatchResult {
        private String scope;
        private Captain captain;
    }

    @Transactional
    public VacationCommissionImportPreviewResponse previewVacationCommission(MultipartFile file, UUID supplierId) {
        fileDetector.requireNot(file, ImportBatchType.CAPTAINS);

        String fileHash = computeHash(file);
        UUID tenantId = TenantContext.getCurrentTenantId();
        boolean exists = tenantId != null
                ? batchRepo.existsByFileHashAndBatchTypeAndStatusAndTenantId(fileHash, ImportBatchType.VACATION_COMMISSION, "COMMITTED", tenantId)
                : batchRepo.existsByFileHashAndBatchTypeAndStatus(fileHash, ImportBatchType.VACATION_COMMISSION, "COMMITTED");

        if (exists) {
            throw new BusinessException("DUPLICATE_FILE", "هذا الملف تم استيراده وتأكيده من قبل");
        }

        Supplier selectedSupplier = supplierId != null ? supplierRepo.findById(supplierId).orElse(null) : null;

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
            if (cols.daysCol == -1 || cols.captainIdCol == -1 || cols.amountCol == -1) {
                throw new BusinessException("MISSING_COLUMNS",
                        "يجب أن يحتوي الملف على عمود رقم الشيفت (Days ksa start)، عمود معرف الكابتن (CAPTAIN ID)، وعمود المبلغ (Amount)");
            }

            List<VacationCommissionImportPreviewResponse.VacationRowPreview> rowPreviews = new ArrayList<>();
            List<VacationCommissionImportPreviewResponse.ImportIssue> issues = new ArrayList<>();

            int totalRows = 0;
            int matchedRows = 0;
            int unmatchedRows = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;
            LocalDate todayDate = LocalDate.now();

            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                totalRows++;
                int rowNumber = r + 1;

                String daysCode = cellText(row.getCell(cols.daysCol)).trim();
                String extCaptainId = ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.captainIdCol))).trim();
                String supplierName = cols.supplierNameCol != -1 ? cellText(row.getCell(cols.supplierNameCol)).trim() : "";
                String zoneName = cols.zoneNameCol != -1 ? cellText(row.getCell(cols.zoneNameCol)).trim() : "";
                String flag = cols.flagCol != -1 ? cellText(row.getCell(cols.flagCol)).trim() : "";
                String note = cols.noteCol != -1 ? cellText(row.getCell(cols.noteCol)).trim() : "";
                BigDecimal amount = cellDecimal(row.getCell(cols.amountCol));

                if (extCaptainId.isEmpty()) {
                    unmatchedRows++;
                    issues.add(VacationCommissionImportPreviewResponse.ImportIssue.builder()
                            .rowNumber(rowNumber)
                            .severity("ERROR")
                            .issueCode("MISSING_CAPTAIN_ID")
                            .message("معرف الكابتن مفقود")
                            .build());
                    rowPreviews.add(buildRowPreview(rowNumber, daysCode, extCaptainId, null, null, supplierName, zoneName, flag, note, amount, todayDate, false, "UNMATCHED", "معرف الكابتن مفقود"));
                    continue;
                }

                if (amount == null) {
                    unmatchedRows++;
                    issues.add(VacationCommissionImportPreviewResponse.ImportIssue.builder()
                            .rowNumber(rowNumber)
                            .severity("ERROR")
                            .issueCode("INVALID_AMOUNT")
                            .message("مبلغ عمولة الإجازة مفقود أو غير صالح")
                            .build());
                    rowPreviews.add(buildRowPreview(rowNumber, daysCode, extCaptainId, null, null, supplierName, zoneName, flag, note, null, todayDate, false, "UNMATCHED", "المبلغ غير صالح"));
                    continue;
                }

                CaptainMatchResult match = matchCaptain(extCaptainId);
                if (match.getCaptain() == null) {
                    unmatchedRows++;
                    issues.add(VacationCommissionImportPreviewResponse.ImportIssue.builder()
                            .rowNumber(rowNumber)
                            .severity("WARNING")
                            .issueCode("UNMATCHED_CAPTAIN")
                            .message("لم يتم العثور على كابتن مطابق للمعرف: " + extCaptainId)
                            .build());
                    rowPreviews.add(buildRowPreview(rowNumber, daysCode, extCaptainId, null, null, supplierName, zoneName, flag, note, amount, todayDate, false, "UNMATCHED", "لم يتم العثور على كابتن مطابق"));
                } else {
                    matchedRows++;
                    totalAmount = totalAmount.add(amount);
                    Captain c = match.getCaptain();
                    rowPreviews.add(buildRowPreview(rowNumber, daysCode, extCaptainId, c.getKamalCode(), c.getFullNameAr(),
                            selectedSupplier != null ? selectedSupplier.getName() : supplierName, zoneName, flag, note, amount, todayDate, true, match.getScope(), "مطابق على الكابتن (" + scopeLabel(match.getScope()) + ")"));
                }
            }

            Optional<ImportBatch> existingBatch = tenantId != null
                    ? batchRepo.findFirstByFileHashAndBatchTypeAndStatusAndTenantIdOrderByUploadedAtDesc(
                    fileHash, ImportBatchType.VACATION_COMMISSION, "PREVIEW", tenantId)
                    : batchRepo.findFirstByFileHashAndBatchTypeAndStatusOrderByUploadedAtDesc(
                    fileHash, ImportBatchType.VACATION_COMMISSION, "PREVIEW");

            ImportBatch batch = existingBatch
                    .orElseGet(() -> ImportBatch.builder()
                            .fileName(file.getOriginalFilename())
                            .fileHash(fileHash)
                            .batchType(ImportBatchType.VACATION_COMMISSION)
                            .status("PREVIEW")
                            .build());

            batch.setRowCount(totalRows);
            batch.setImportedCount(matchedRows);
            batch.setSkippedCount(unmatchedRows);
            batch.setErrorCount((int) issues.stream().filter(i -> "ERROR".equals(i.getSeverity())).count());
            batch.setWarningCount((int) issues.stream().filter(i -> "WARNING".equals(i.getSeverity())).count());
            batch = batchRepo.save(batch);

            return VacationCommissionImportPreviewResponse.builder()
                    .batchId(batch.getId())
                    .fileName(file.getOriginalFilename())
                    .totalRows(totalRows)
                    .matchedRows(matchedRows)
                    .unmatchedRows(unmatchedRows)
                    .totalAmount(totalAmount)
                    .issues(issues)
                    .rows(rowPreviews)
                    .build();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to preview vacation commission file: {}", e.getMessage(), e);
            throw new BusinessException("PREVIEW_ERROR", "فشل في تحليل ملف عمولة أوقات الإجازة: " + e.getMessage());
        }
    }

    @Transactional
    public VacationCommissionImportPreviewResponse commitVacationCommission(UUID batchId, MultipartFile file, UUID supplierId) {
        ImportBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "دفعة الاستيراد غير موجودة"));

        if (batch.getBatchType() != ImportBatchType.VACATION_COMMISSION) {
            throw new BusinessException("INVALID_BATCH", "نوع الدفعة غير مطابق لعمولة أوقات الإجازة");
        }

        Supplier selectedSupplier = supplierId != null ? supplierRepo.findById(supplierId).orElse(null) : null;
        Tenant currentTenant = entityManager.getReference(Tenant.class, TenantContext.requireCurrentTenantId());

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int headerIdx = findHeaderRowIndex(sheet);
            Row headerRow = sheet.getRow(headerIdx);
            ColumnIndices cols = detectColumns(headerRow);

            int totalRows = 0;
            int matchedRows = 0;
            int unmatchedRows = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;
            LocalDate importDate = LocalDate.now();

            List<VacationCommissionImportPreviewResponse.VacationRowPreview> rowPreviews = new ArrayList<>();
            List<VacationCommissionImportPreviewResponse.ImportIssue> issues = new ArrayList<>();
            List<VacationCommission> entitiesToSave = new ArrayList<>();

            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) continue;

                totalRows++;
                int rowNumber = r + 1;

                String daysCode = cellText(row.getCell(cols.daysCol)).trim();
                String extCaptainId = ExcelCellSupport.toWesternDigits(cellText(row.getCell(cols.captainIdCol))).trim();
                String supplierName = cols.supplierNameCol != -1 ? cellText(row.getCell(cols.supplierNameCol)).trim() : "";
                String zoneName = cols.zoneNameCol != -1 ? cellText(row.getCell(cols.zoneNameCol)).trim() : "";
                String flag = cols.flagCol != -1 ? cellText(row.getCell(cols.flagCol)).trim() : "";
                String note = cols.noteCol != -1 ? cellText(row.getCell(cols.noteCol)).trim() : "";
                BigDecimal amount = cellDecimal(row.getCell(cols.amountCol));

                if (extCaptainId.isEmpty() || amount == null) {
                    unmatchedRows++;
                    continue;
                }

                CaptainMatchResult match = matchCaptain(extCaptainId);
                Captain captain = match.getCaptain();

                VacationCommission vc = VacationCommission.builder()
                        .daysCode(daysCode)
                        .supplier(selectedSupplier)
                        .supplierName(selectedSupplier != null ? selectedSupplier.getName() : supplierName)
                        .externalCaptainId(extCaptainId)
                        .captain(captain)
                        .zoneName(zoneName)
                        .flag(flag)
                        .note(note)
                        .amount(amount)
                        .vacationDate(importDate)
                        .importBatch(batch)
                        .tenant(currentTenant)
                        .build();

                entitiesToSave.add(vc);
                matchedRows++;
                totalAmount = totalAmount.add(amount);

                rowPreviews.add(buildRowPreview(rowNumber, daysCode, extCaptainId,
                        captain != null ? captain.getKamalCode() : null,
                        captain != null ? captain.getFullNameAr() : null,
                        selectedSupplier != null ? selectedSupplier.getName() : supplierName,
                        zoneName, flag, note, amount, importDate, captain != null, match.getScope(), "تم حفظ عمولة الإجازة بنجاح"));
            }

            vacationCommissionRepo.saveAll(entitiesToSave);

            batch.setStatus("COMMITTED");
            batch.setImportedCount(matchedRows);
            batch.setSkippedCount(unmatchedRows);
            batchRepo.save(batch);

            log.info("Committed vacation commission batch {}: {} total, {} committed", batchId, totalRows, matchedRows);

            return VacationCommissionImportPreviewResponse.builder()
                    .batchId(batch.getId())
                    .fileName(batch.getFileName())
                    .totalRows(totalRows)
                    .matchedRows(matchedRows)
                    .unmatchedRows(unmatchedRows)
                    .totalAmount(totalAmount)
                    .issues(issues)
                    .rows(rowPreviews)
                    .build();

        } catch (Exception e) {
            log.error("Failed to commit vacation commission batch {}: {}", batchId, e.getMessage(), e);
            throw new BusinessException("COMMIT_ERROR", "فشل في تأكيد استيراد عمولة أوقات الإجازة: " + e.getMessage());
        }
    }

    private CaptainMatchResult matchCaptain(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return CaptainMatchResult.builder().scope("UNMATCHED").captain(null).build();
        }

        // Scope 1: Match by Kamal Code (KSLSC)
        Optional<Captain> byKslsc = captainRepo.findByKamalCode(externalId);
        if (byKslsc.isPresent()) {
            return CaptainMatchResult.builder().scope("KSLSC_ID").captain(byKslsc.get()).build();
        }

        // Scope 2: Match by Supplier ID Slot external ID
        List<SupplierIdSlot> slots = slotRepo.findByExternalIdContainingIgnoreCase(externalId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        for (SupplierIdSlot slot : slots) {
            if (externalId.equalsIgnoreCase(slot.getExternalId())) {
                Optional<SupplierIdAssignment> assignment = assignmentRepo.findBySupplierIdSlotIdAndValidToIsNull(slot.getId());
                if (assignment.isPresent()) {
                    return CaptainMatchResult.builder().scope("NINJA_ID").captain(assignment.get().getCaptain()).build();
                }
            }
        }

        // Scope 3: Match by Iqama Number or Phone
        Optional<Captain> byIqamaOrPhone = captainRepo.findByIqamaNumber(externalId)
                .or(() -> captainRepo.findByPhone(externalId));
        if (byIqamaOrPhone.isPresent()) {
            return CaptainMatchResult.builder().scope("PHONE_OR_NATIONAL_ID").captain(byIqamaOrPhone.get()).build();
        }

        return CaptainMatchResult.builder().scope("UNMATCHED").captain(null).build();
    }

    private String scopeLabel(String scope) {
        return switch (scope) {
            case "NINJA_ID" -> "مطابقة برقم المورد";
            case "KSLSC_ID" -> "مطابقة برقم KSLSC";
            case "PHONE_OR_NATIONAL_ID" -> "مطابقة برقم الهوية/الهاتف";
            default -> "غير مطابق";
        };
    }

    private VacationCommissionImportPreviewResponse.VacationRowPreview buildRowPreview(
            int rowNumber, String daysCode, String extId, String captainCode, String captainName,
            String supplierName, String zoneName, String flag, String note, BigDecimal amount,
            LocalDate vacationDate, boolean matched, String scope, String status) {
        return VacationCommissionImportPreviewResponse.VacationRowPreview.builder()
                .rowNumber(rowNumber)
                .daysCode(daysCode)
                .externalCaptainId(extId)
                .captainCode(captainCode)
                .captainName(captainName)
                .supplierName(supplierName)
                .zoneName(zoneName)
                .flag(flag)
                .note(note)
                .amount(amount != null ? amount.abs() : BigDecimal.ZERO)
                .vacationDate(vacationDate)
                .matched(matched)
                .matchingScope(scope)
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
                if (normalized.contains("days") || normalized.contains("ksa") || normalized.contains("captain") ||
                        normalized.contains("amount") || normalized.contains("مبلغ") || normalized.contains("عمولة")) {
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

            if (normalized.contains("days") || normalized.contains("ksa") || normalized.contains("يوم") || normalized.contains("أيام")) {
                if (cols.daysCol == -1) cols.daysCol = idx;
            } else if (normalized.contains("captain") || normalized.contains("kslsc") || normalized.contains("كابتن") || normalized.contains("id")) {
                if (cols.captainIdCol == -1) cols.captainIdCol = idx;
            } else if (normalized.contains("amount") || normalized.contains("مبلغ") || normalized.contains("قيمة") || normalized.contains("عمولة") || normalized.contains("بونص")) {
                if (cols.amountCol == -1) cols.amountCol = idx;
            } else if (normalized.contains("supplier") || normalized.contains("مورد")) {
                if (cols.supplierNameCol == -1) cols.supplierNameCol = idx;
            } else if (normalized.contains("zone") || normalized.contains("منطقة")) {
                if (cols.zoneNameCol == -1) cols.zoneNameCol = idx;
            } else if (normalized.contains("flag") || normalized.contains("وسم") || normalized.contains("علامة")) {
                if (cols.flagCol == -1) cols.flagCol = idx;
            } else if (normalized.contains("note") || normalized.contains("ملاحظ") || normalized.contains("بيان") || normalized.contains("شرح")) {
                if (cols.noteCol == -1) cols.noteCol = idx;
            }
        }
        return cols;
    }

    private static class ColumnIndices {
        int daysCol = -1;
        int captainIdCol = -1;
        int amountCol = -1;
        int supplierNameCol = -1;
        int zoneNameCol = -1;
        int flagCol = -1;
        int noteCol = -1;
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
        } catch (Exception ignored) {}
        return null;
    }
}
