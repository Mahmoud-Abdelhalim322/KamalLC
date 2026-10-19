package com.kamal.lc.imports.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.imports.dto.ExcelCaptainRow;
import com.kamal.lc.imports.dto.ImportBatchResponse;
import com.kamal.lc.imports.dto.ImportPreviewResponse;
import com.kamal.lc.imports.entity.ImportBatch;
import com.kamal.lc.imports.entity.ImportBatchType;
import com.kamal.lc.imports.entity.ImportRowIssue;
import com.kamal.lc.imports.repository.ImportBatchRepository;
import com.kamal.lc.imports.repository.ImportRowIssueRepository;
import com.kamal.lc.staff.repository.StaffRepository;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import com.kamal.lc.supplier.entity.SupplierIdSlot;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import com.kamal.lc.supplier.repository.SupplierIdSlotRepository;
import com.kamal.lc.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final ExcelParserService excelParser;
    private final ImportFileDetector fileDetector;
    private final ImportRowWriter rowWriter;
    private final CaptainRepository captainRepo;
    private final StaffRepository staffRepo;
    private final SupplierRepository supplierRepo;
    private final SupplierIdSlotRepository slotRepo;
    private final SupplierIdAssignmentRepository assignmentRepo;
    private final ImportBatchRepository batchRepo;
    private final ImportRowIssueRepository issueRepo;

    /**
     * Step 1: Parse the file and return a preview without persisting captain/slot data.
     * The ImportBatch is saved in PREVIEW status so the user can later commit.
     */
    @Transactional
    public ImportPreviewResponse preview(MultipartFile file, UUID supplierId) {
        // 0. A shift report here is a wrong-screen mistake, not a malformed file.
        fileDetector.requireNot(file, ImportBatchType.SHIFTS);

        // 1. Check duplicate by file hash
        String fileHash = computeHash(file);
        UUID tenantId = com.kamal.lc.common.security.TenantContext.getCurrentTenantId();
        boolean exists = tenantId != null
                ? batchRepo.existsByFileHashAndBatchTypeAndStatusAndTenantId(fileHash, ImportBatchType.CAPTAINS, "COMMITTED", tenantId)
                : batchRepo.existsByFileHashAndBatchTypeAndStatus(fileHash, ImportBatchType.CAPTAINS, "COMMITTED");

        if (exists) {
            throw new BusinessException("DUPLICATE_FILE", "هذا الملف تم استيراده من قبل");
        }

        // 2. Parse Excel
        List<ExcelCaptainRow> rows;
        try {
            rows = excelParser.parse(file);
        } catch (ExcelParserService.ExcelParseException e) {
            throw new BusinessException("PARSE_ERROR", e.getMessage());
        }

        if (rows.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "الملف لا يحتوي على بيانات");
        }

        Supplier supplier = resolveSupplier(supplierId);

        // 3. Put the file in PREVIEW status. Re-uploading a file that is still awaiting a
        //    commit refreshes that attempt instead of adding another history row for it.
        ImportBatch batch = reusablePreview(fileHash);
        batch.setFileName(file.getOriginalFilename());
        batch.setRowCount(rows.size());
        batch.setStatus("PREVIEW");
        batch.setSupplier(supplier);
        batch = batchRepo.save(batch);

        // 4. Analyze rows
        int newCaptains = 0, existingCaptains = 0;
        int newNinjaIds = 0, existingNinjaIds = 0;
        List<ImportPreviewResponse.ImportIssue> issues = new ArrayList<>();

        // A supplier ID listed against two captains is a mistake in the sheet, and it is
        // invisible to the per-row checks below because they only compare against the
        // database. Only the first row can get the ID, so say so before anything is written.
        Map<String, ExcelCaptainRow> firstRowPerNinjaId = new HashMap<>();

        for (ExcelCaptainRow row : rows) {
            // Validate required field: only kamalCode is mandatory
            String rawCode = row.getKamalCode();
            if (rawCode == null || rawCode.trim().isEmpty()) {
                issues.add(issue(row.getRowNumber(), "ERROR", "MISSING_KAMAL_CODE", "الرقم الوظيفي مفقود"));
                continue;
            }

            String code = rawCode.trim().toUpperCase();
            boolean isCaptain = code.startsWith("KSC") || code.startsWith("KSCFL");
            boolean isWorker = code.startsWith("KSE");
            boolean isManager = code.startsWith("KSM");

            if (!isCaptain && !isWorker && !isManager) {
                issues.add(issue(row.getRowNumber(), "ERROR", "INVALID_CODE_PREFIX",
                        "الرقم الوظيفي " + code + " غير صالح. يجب أن يبدأ بـ KSC أو KSCFL (كابتن)، KSE (عمالة)، أو KSM (إداريين)"));
                continue;
            }

            // Cell-level problems (unreadable dates) never block the row — flag them only.
            for (String warning : row.getWarnings()) {
                issues.add(issue(row.getRowNumber(), "WARNING", "UNREADABLE_CELL", warning));
            }

            // Identifier and Slot checks are only relevant for Captains when appId/ninjaId is provided
            String externalId = row.getEffectiveIdentifier();
            if (isCaptain && externalId != null && !externalId.isEmpty()) {
                ExcelCaptainRow firstUse = firstRowPerNinjaId.putIfAbsent(externalId, row);
                if (firstUse != null && !firstUse.getKamalCode().trim().equalsIgnoreCase(row.getKamalCode().trim())) {
                    String firstCaptainName = firstUse.getCaptainName() != null && !firstUse.getCaptainName().isBlank()
                            ? firstUse.getCaptainName() : firstUse.getKamalCode();
                    issues.add(issue(row.getRowNumber(), "WARNING", "DUPLICATE_NINJA_ID",
                            "معرف المورد " + externalId + " مستخدم أيضًا في الصف " + firstUse.getRowNumber()
                                    + " لكابتن " + firstCaptainName
                                    + " — سيبقى المعرف مربوطًا بالصف الأول فقط"));
                }

                if (firstUse == null && supplier != null) {
                    if (slotRepo.existsByExternalIdAndSupplierId(externalId, supplier.getId())) {
                        existingNinjaIds++;
                        // Check if assigned to a different captain
                        slotRepo.findByExternalIdAndSupplierId(externalId, supplier.getId()).ifPresent(slot -> {
                            assignmentRepo.findBySupplierIdSlotIdAndValidToIsNull(slot.getId()).ifPresent(assignment -> {
                                Captain assignedCaptain = assignment.getCaptain();
                                if (!assignedCaptain.getKamalCode().equalsIgnoreCase(code)) {
                                    issues.add(issue(row.getRowNumber(), "WARNING", "NINJA_ID_REASSIGNMENT",
                                            "معرف المورد " + externalId + " مربوط حاليًا بكابتن " + assignedCaptain.getFullNameAr()));
                                }
                            });
                        });
                    } else {
                        newNinjaIds++;
                    }
                }
            }

            // Check existence
            if (isCaptain) {
                if (captainRepo.existsByKamalCode(code)) {
                    existingCaptains++;
                } else {
                    newCaptains++;
                }
            } else {
                if (staffRepo.existsByStaffCode(code)) {
                    existingCaptains++;
                } else {
                    newCaptains++;
                }
            }
        }

        // 5. Save issues
        for (ImportPreviewResponse.ImportIssue iss : issues) {
            issueRepo.save(ImportRowIssue.builder()
                    .importBatch(batch)
                    .rowNumber(iss.getRowNumber())
                    .severity(iss.getSeverity())
                    .issueCode(iss.getIssueCode())
                    .messageAr(iss.getMessage())
                    .build());
        }

        long errorCount = issues.stream().filter(i -> "ERROR".equals(i.getSeverity())).count();
        long warningCount = issues.stream().filter(i -> "WARNING".equals(i.getSeverity())).count();
        batch.setWarningCount((int) warningCount);
        batch.setErrorCount((int) errorCount);
        batchRepo.save(batch);

        return ImportPreviewResponse.builder()
                .batchId(batch.getId())
                .fileName(file.getOriginalFilename())
                .totalRows(rows.size())
                .newCaptains(newCaptains)
                .existingCaptains(existingCaptains)
                .newNinjaIds(newNinjaIds)
                .existingNinjaIds(existingNinjaIds)
                .issues(issues)
                .build();
    }

    /**
     * Step 2: Commit the import — actually create captains and assign ninja IDs.
     * Re-parses the file (which must be re-uploaded) or uses the batch ID from preview.
     */
    @Transactional
    public ImportPreviewResponse commit(MultipartFile file, UUID batchId, UUID supplierId) {
        ImportBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "عملية الاستيراد غير موجودة"));

        if ("COMMITTED".equals(batch.getStatus())) {
            throw new BusinessException("ALREADY_COMMITTED", "تم تأكيد هذا الاستيراد من قبل");
        }
        // The preview and the commit must be the same kind of import.
        if (batch.getBatchType() != ImportBatchType.CAPTAINS) {
            throw new BusinessException("WRONG_IMPORT_TYPE",
                    "هذه العملية ليست استيراد بيانات كباتن");
        }

        // Parse file again
        List<ExcelCaptainRow> rows;
        try {
            rows = excelParser.parse(file);
        } catch (ExcelParserService.ExcelParseException e) {
            batch.setStatus("FAILED");
            batchRepo.save(batch);
            throw new BusinessException("PARSE_ERROR", e.getMessage());
        }

        Supplier supplier = supplierId != null
                ? resolveSupplier(supplierId)
                : (batch.getSupplier() != null ? batch.getSupplier() : resolveSupplier(null));

        int imported = 0, skipped = 0;
        int newSlots = 0, existingSlots = 0;
        Set<String> processedExternalIds = new HashSet<>();
        List<ImportPreviewResponse.ImportIssue> issues = new ArrayList<>();

        for (ExcelCaptainRow row : rows) {
            // Skip invalid rows: only kamalCode with valid prefix is required
            String rawCode = row.getKamalCode();
            if (rawCode == null || rawCode.trim().isEmpty()) {
                skipped++;
                continue;
            }
            String code = rawCode.trim().toUpperCase();
            boolean isCaptain = code.startsWith("KSC") || code.startsWith("KSCFL");
            boolean isWorker = code.startsWith("KSE");
            boolean isManager = code.startsWith("KSM");
            if (!isCaptain && !isWorker && !isManager) {
                skipped++;
                continue;
            }

            for (String warning : row.getWarnings()) {
                issues.add(issue(row.getRowNumber(), "WARNING", "UNREADABLE_CELL", warning));
            }

            // Track slots if supplier and identifier are present
            String externalId = row.getEffectiveIdentifier();
            if (isCaptain && supplier != null && externalId != null && !externalId.isEmpty() && !processedExternalIds.contains(externalId)) {
                processedExternalIds.add(externalId);
                if (slotRepo.existsByExternalIdAndSupplierId(externalId, supplier.getId())) {
                    existingSlots++;
                } else {
                    newSlots++;
                }
            }

            // Each row is written in its own transaction, so one that hits a constraint is
            // skipped and reported instead of aborting the transaction the rest depends on.
            try {
                issues.addAll(rowWriter.write(row, supplier));
                imported++;
            } catch (Exception e) {
                skipped++;
                issues.add(issue(row.getRowNumber(), "ERROR", "IMPORT_FAILED",
                        "فشل استيراد الصف: " + rootMessage(e)));
                log.error("Failed to import row {}: {}", row.getRowNumber(), e.getMessage(), e);
            }
        }

        // Update batch
        batch.setImportedCount(imported);
        batch.setSkippedCount(skipped);
        batch.setStatus("COMMITTED");
        batch.setSupplier(supplier);
        batch.setWarningCount((int) issues.stream().filter(i -> "WARNING".equals(i.getSeverity())).count());
        batch.setErrorCount((int) issues.stream().filter(i -> "ERROR".equals(i.getSeverity())).count());
        batchRepo.save(batch);

        return ImportPreviewResponse.builder()
                .batchId(batch.getId())
                .fileName(batch.getFileName())
                .totalRows(rows.size())
                .newCaptains(imported)
                .existingCaptains(skipped)
                .newNinjaIds(newSlots)
                .existingNinjaIds(existingSlots)
                .issues(issues)
                .build();
    }

    private Supplier resolveSupplier(UUID supplierId) {
        if (supplierId != null) {
            return supplierRepo.findById(supplierId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "المورد المحدد غير موجود"));
        }
        return supplierRepo.findByCode("NINJA")
                .or(() -> supplierRepo.findAll().stream().findFirst())
                .orElse(null);
    }

    /**
     * The batch this preview should write to: the file's pending preview if it has one,
     * otherwise a fresh batch. Reusing it keeps one history row per uncommitted file, so
     * a hash never ends up on more than one PREVIEW batch.
     */
    private ImportBatch reusablePreview(String fileHash) {
        UUID tenantId = com.kamal.lc.common.security.TenantContext.getCurrentTenantId();
        Optional<ImportBatch> existing = tenantId != null
                ? batchRepo.findFirstByFileHashAndBatchTypeAndStatusAndTenantIdOrderByUploadedAtDesc(
                        fileHash, ImportBatchType.CAPTAINS, "PREVIEW", tenantId)
                : batchRepo.findFirstByFileHashAndBatchTypeAndStatusOrderByUploadedAtDesc(
                        fileHash, ImportBatchType.CAPTAINS, "PREVIEW");

        ImportBatch batch = existing.orElseGet(() -> ImportBatch.builder()
                .fileHash(fileHash)
                .batchType(ImportBatchType.CAPTAINS)
                .build());

        if (batch.getId() != null) {
            // This run replaces the earlier one — its findings must not be counted twice.
            issueRepo.deleteByImportBatchId(batch.getId());
            batch.setUploadedAt(OffsetDateTime.now());
        }
        return batch;
    }

    /**
     * The database's own message, not the layers of wrapping around it. Persistence
     * exceptions nest the useful sentence several causes deep, and it is what tells the
     * user which row detail the database rejected.
     */
    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : e.toString();
    }

    /** History for one import screen; {@code null} keeps the old combined listing. */
    public Page<ImportBatchResponse> getHistory(ImportBatchType type, Pageable pageable) {
        UUID tenantId = com.kamal.lc.common.security.TenantContext.getCurrentTenantId();
        Page<ImportBatch> page;
        if (tenantId != null) {
            page = type == null
                    ? batchRepo.findAllByTenantIdOrderByUploadedAtDesc(tenantId, pageable)
                    : batchRepo.findAllByBatchTypeAndTenantIdOrderByUploadedAtDesc(type, tenantId, pageable);
        } else {
            page = type == null
                    ? batchRepo.findAllByOrderByUploadedAtDesc(pageable)
                    : batchRepo.findAllByBatchTypeOrderByUploadedAtDesc(type, pageable);
        }
        return page.map(this::toResponse);
    }

    public ImportBatchResponse getBatch(UUID id) {
        ImportBatch batch = batchRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "عملية الاستيراد غير موجودة"));
        return toResponse(batch);
    }

    private ImportBatchResponse toResponse(ImportBatch b) {
        return ImportBatchResponse.builder()
                .id(b.getId())
                .fileName(b.getFileName())
                .uploadedAt(b.getUploadedAt().toString())
                .rowCount(b.getRowCount())
                .importedCount(b.getImportedCount())
                .skippedCount(b.getSkippedCount())
                .warningCount(b.getWarningCount())
                .errorCount(b.getErrorCount())
                .status(b.getStatus())
                .batchType(b.getBatchType() != null ? b.getBatchType().name() : null)
                .periodFrom(b.getPeriodFrom() != null ? b.getPeriodFrom().toString() : null)
                .periodTo(b.getPeriodTo() != null ? b.getPeriodTo().toString() : null)
                .build();
    }

    private ImportPreviewResponse.ImportIssue issue(int row, String severity, String code, String message) {
        return ImportPreviewResponse.ImportIssue.builder()
                .rowNumber(row)
                .severity(severity)
                .issueCode(code)
                .message(message)
                .build();
    }

    private String computeHash(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
