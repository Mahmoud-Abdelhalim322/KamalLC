package com.kamal.lc.imports.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.imports.dto.ImportPreviewResponse.ImportIssue;
import com.kamal.lc.imports.dto.NinjaShiftRow;
import com.kamal.lc.imports.dto.ShiftImportPreviewResponse;
import com.kamal.lc.imports.entity.ImportBatch;
import com.kamal.lc.imports.entity.ImportBatchType;
import com.kamal.lc.imports.entity.ImportRowIssue;
import com.kamal.lc.imports.repository.ImportBatchRepository;
import com.kamal.lc.imports.repository.ImportRowIssueRepository;
import com.kamal.lc.payroll.entity.PayrollRun;
import com.kamal.lc.payroll.repository.PayrollRunRepository;
import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import com.kamal.lc.shift.service.ShiftCalculator;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import com.kamal.lc.supplier.entity.SupplierIdSlot;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import com.kamal.lc.supplier.repository.SupplierIdSlotRepository;
import com.kamal.lc.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Imports the supplier's shift file — the daily job that feeds reports and payroll.
 *
 * <p>Three properties matter more than anything else here:
 * <ul>
 *   <li><b>Idempotent.</b> Files overlap day to day, so a row already seen is updated in
 *       place on its natural key rather than inserted again.</li>
 *   <li><b>Date-scoped matching.</b> A shift is attributed to whoever held the supplier
 *       ID <em>on the day of the shift</em>, not to whoever holds it now.</li>
 *   <li><b>Nothing is dropped.</b> An unrecognised supplier ID still gets its shift
 *       stored, unlinked, and surfaced for someone to resolve.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftImportService {

    private final NinjaShiftParser parser;
    private final ImportFileDetector fileDetector;
    private final ShiftRepository shiftRepo;
    private final ShiftCalculator calculator;
    private final SettingsResolver settingsResolver;
    private final SupplierRepository supplierRepo;
    private final SupplierIdSlotRepository slotRepo;
    private final SupplierIdAssignmentRepository assignmentRepo;
    private final ImportBatchRepository batchRepo;
    private final ImportRowIssueRepository issueRepo;
    private final PayrollRunRepository payrollRunRepo;

    /** Step 1: read the file and report what committing it would do. Nothing is persisted. */
    @Transactional
    public ShiftImportPreviewResponse preview(MultipartFile file) {
        // A captain roster here is a wrong-screen mistake, not a malformed file.
        fileDetector.requireNot(file, ImportBatchType.CAPTAINS);

        List<NinjaShiftRow> rows = parse(file);

        Supplier supplier = requireSupplier();
        LocalDate periodFrom = minDate(rows);
        LocalDate periodTo = maxDate(rows);

        // Re-uploading a file that is still awaiting a commit refreshes that attempt rather
        // than adding another history row for it.
        ImportBatch batch = reusablePreview(computeHash(file));
        batch.setFileName(file.getOriginalFilename());
        batch.setRowCount(rows.size());
        batch.setStatus("PREVIEW");
        batch.setPeriodFrom(periodFrom);
        batch.setPeriodTo(periodTo);
        batch = batchRepo.save(batch);

        List<ImportIssue> issues = new ArrayList<>();
        Set<String> unmatchedIds = new LinkedHashSet<>();
        int newShifts = 0, updatedShifts = 0, matched = 0, unmatched = 0, absent = 0;

        // A locked payroll covering this period is a blocking problem, not a per-row one.
        for (PayrollRun locked : lockedRunsFor(periodFrom, periodTo)) {
            issues.add(issue(0, "ERROR", "PERIOD_LOCKED",
                    "الفترة " + locked.getPeriodFrom() + " إلى " + locked.getPeriodTo()
                            + " مقفلة بمسير رواتب معتمد — لا يمكن تعديل شفتاتها"));
        }

        for (NinjaShiftRow row : rows) {
            List<String> problems = validate(row);
            if (!problems.isEmpty()) {
                problems.forEach(p -> issues.add(issue(row.getRowNumber(), "ERROR", "INVALID_ROW", p)));
                continue;
            }
            row.getWarnings().forEach(w ->
                    issues.add(issue(row.getRowNumber(), "WARNING", "ROW_WARNING", w)));

            if (shiftRepo.findByExternalShiftIdAndNinjaCaptainIdAndShiftDate(
                    row.getExternalShiftId(), row.getNinjaCaptainId(), row.getShiftDate()).isPresent()) {
                updatedShifts++;
            } else {
                newShifts++;
            }

            Captain captain = resolveCaptain(supplier, row.getNinjaCaptainId(), row.getShiftDate());
            if (captain != null) {
                matched++;
            } else {
                unmatched++;
                unmatchedIds.add(row.getNinjaCaptainId());
            }

            if (row.getAttendanceFlag() != null
                    && row.getAttendanceFlag().toLowerCase().contains("absent")) {
                absent++;
            }
        }

        for (String externalId : unmatchedIds) {
            issues.add(issue(0, "WARNING", "UNMATCHED_NINJA_ID",
                    "معرف المورد " + externalId + " غير مربوط بأي كابتن في هذه الفترة"));
        }

        persistIssues(batch, issues);
        batch.setWarningCount(count(issues, "WARNING"));
        batch.setErrorCount(count(issues, "ERROR"));
        batchRepo.save(batch);

        return ShiftImportPreviewResponse.builder()
                .batchId(batch.getId())
                .fileName(file.getOriginalFilename())
                .totalRows(rows.size())
                .periodFrom(periodFrom != null ? periodFrom.toString() : null)
                .periodTo(periodTo != null ? periodTo.toString() : null)
                .newShifts(newShifts)
                .updatedShifts(updatedShifts)
                .matchedCaptains(matched)
                .unmatchedRows(unmatched)
                .unmatchedExternalIds(List.copyOf(unmatchedIds))
                .absentRows(absent)
                .issues(issues)
                .build();
    }

    /** Step 2: write the shifts. Safe to run on a file that was already partly imported. */
    @Transactional
    public ShiftImportPreviewResponse commit(MultipartFile file, UUID batchId) {
        ImportBatch batch = batchRepo.findById(batchId)
                .orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND", "عملية الاستيراد غير موجودة"));
        if ("COMMITTED".equals(batch.getStatus())) {
            throw new BusinessException("ALREADY_COMMITTED", "تم تأكيد هذا الاستيراد من قبل");
        }
        // The preview and the commit must be the same kind of import.
        if (batch.getBatchType() != ImportBatchType.SHIFTS) {
            throw new BusinessException("WRONG_IMPORT_TYPE",
                    "هذه العملية ليست استيراد تقرير شيفتات");
        }

        List<NinjaShiftRow> rows;
        try {
            rows = parser.parse(file);
        } catch (ExcelParserService.ExcelParseException e) {
            batch.setStatus("FAILED");
            batchRepo.save(batch);
            throw new BusinessException("PARSE_ERROR", e.getMessage());
        }

        LocalDate periodFrom = minDate(rows);
        LocalDate periodTo = maxDate(rows);
        List<PayrollRun> locked = lockedRunsFor(periodFrom, periodTo);
        if (!locked.isEmpty()) {
            PayrollRun run = locked.get(0);
            throw new BusinessException("PERIOD_LOCKED",
                    "لا يمكن الاستيراد: الفترة " + run.getPeriodFrom() + " إلى " + run.getPeriodTo()
                            + " مقفلة بمسير رواتب معتمد");
        }

        Supplier supplier = requireSupplier();
        List<ImportIssue> issues = new ArrayList<>();
        Set<String> unmatchedIds = new LinkedHashSet<>();
        int imported = 0, skipped = 0, newShifts = 0, updatedShifts = 0, matched = 0, absent = 0;

        for (NinjaShiftRow row : rows) {
            List<String> problems = validate(row);
            if (!problems.isEmpty()) {
                skipped++;
                problems.forEach(p -> issues.add(issue(row.getRowNumber(), "ERROR", "INVALID_ROW", p)));
                continue;
            }
            // A date this import corrected must be as visible on the committed batch as it
            // was on the preview — the batch is what someone audits later.
            row.getWarnings().forEach(w ->
                    issues.add(issue(row.getRowNumber(), "WARNING", "ROW_WARNING", w)));

            try {
                // Registering the ID up front means an unknown captain still shows up in the
                // IDs screen ready to be linked, instead of being invisible until someone asks.
                SupplierIdSlot slot = findOrCreateSlot(supplier, row.getNinjaCaptainId());
                Captain captain = holderOn(slot, row.getShiftDate());

                if (captain != null) {
                    matched++;
                } else {
                    unmatchedIds.add(row.getNinjaCaptainId());
                }

                Optional<Shift> existing = shiftRepo.findByExternalShiftIdAndNinjaCaptainIdAndShiftDate(
                        row.getExternalShiftId(), row.getNinjaCaptainId(), row.getShiftDate());
                Shift shift = existing.orElseGet(Shift::new);
                boolean isNew = existing.isEmpty();

                applyRow(shift, row, supplier, captain, batch);

                // Rates are resolved as of the shift's own date, so importing an old file
                // prices it under the rules that were in force back then.
                EffectiveSettings settings = settingsResolver.resolve(row.getShiftDate());
                calculator.apply(shift, captain, settings);

                shiftRepo.save(shift);

                if (isNew) newShifts++; else updatedShifts++;
                if (Boolean.FALSE.equals(shift.getAttended())) absent++;
                imported++;

            } catch (Exception e) {
                skipped++;
                issues.add(issue(row.getRowNumber(), "ERROR", "IMPORT_FAILED",
                        "فشل استيراد الصف: " + e.getMessage()));
                log.error("Failed to import shift row {}: {}", row.getRowNumber(), e.getMessage(), e);
            }
        }

        for (String externalId : unmatchedIds) {
            issues.add(issue(0, "WARNING", "UNMATCHED_NINJA_ID",
                    "معرف المورد " + externalId + " غير مربوط بكابتن — شفتاته محفوظة بانتظار الربط"));
        }

        persistIssues(batch, issues);
        batch.setImportedCount(imported);
        batch.setSkippedCount(skipped);
        batch.setRowCount(rows.size());
        batch.setPeriodFrom(periodFrom);
        batch.setPeriodTo(periodTo);
        batch.setStatus("COMMITTED");
        batch.setWarningCount(count(issues, "WARNING"));
        batch.setErrorCount(count(issues, "ERROR"));
        batchRepo.save(batch);

        return ShiftImportPreviewResponse.builder()
                .batchId(batch.getId())
                .fileName(batch.getFileName())
                .totalRows(rows.size())
                .periodFrom(periodFrom != null ? periodFrom.toString() : null)
                .periodTo(periodTo != null ? periodTo.toString() : null)
                .newShifts(newShifts)
                .updatedShifts(updatedShifts)
                .matchedCaptains(matched)
                .unmatchedRows(unmatchedIds.size())
                .unmatchedExternalIds(List.copyOf(unmatchedIds))
                .absentRows(absent)
                .committedRows(imported)
                .skippedRows(skipped)
                .issues(issues)
                .build();
    }

    /**
     * Re-runs matching and pricing over a period. Needed after a supplier ID is linked to a
     * captain, or after a rate is corrected — the stored shifts are the source of truth, so
     * the file does not have to be uploaded again.
     */
    @Transactional
    public RecalculationResult recalculate(LocalDate from, LocalDate to) {
        List<PayrollRun> locked = lockedRunsFor(from, to);
        if (!locked.isEmpty()) {
            PayrollRun run = locked.get(0);
            throw new BusinessException("PERIOD_LOCKED",
                    "لا يمكن إعادة الحساب: الفترة " + run.getPeriodFrom() + " إلى " + run.getPeriodTo()
                            + " مقفلة بمسير رواتب معتمد");
        }

        Supplier supplier = requireSupplier();
        List<Shift> shifts = shiftRepo.findForRecalculation(from, to);
        int relinked = 0;

        for (Shift shift : shifts) {
            Captain before = shift.getCaptain();
            Captain captain = resolveCaptain(supplier, shift.getNinjaCaptainId(), shift.getShiftDate());
            if (captain != null && (before == null || !before.getId().equals(captain.getId()))) {
                shift.setCaptain(captain);
                relinked++;
            }
            calculator.apply(shift, shift.getCaptain(), settingsResolver.resolve(shift.getShiftDate()));
        }
        shiftRepo.saveAll(shifts);

        log.info("Recalculated {} shifts between {} and {} ({} relinked)", shifts.size(), from, to, relinked);
        return new RecalculationResult(shifts.size(), relinked);
    }

    public record RecalculationResult(int recalculated, int relinked) {}

    // ---- helpers ----

    /**
     * The batch this preview should write to: the file's pending preview if it has one,
     * otherwise a fresh batch. A PREVIEW batch owns no shifts yet, so reusing it is safe.
     */
    private ImportBatch reusablePreview(String fileHash) {
        ImportBatch batch = batchRepo
                .findFirstByFileHashAndBatchTypeAndStatusOrderByUploadedAtDesc(
                        fileHash, ImportBatchType.SHIFTS, "PREVIEW")
                .orElseGet(() -> ImportBatch.builder()
                        .fileHash(fileHash)
                        .batchType(ImportBatchType.SHIFTS)
                        .build());

        if (batch.getId() != null) {
            // This run replaces the earlier one — its findings must not be counted twice.
            issueRepo.deleteByImportBatchId(batch.getId());
            batch.setUploadedAt(OffsetDateTime.now());
        }
        return batch;
    }

    private List<NinjaShiftRow> parse(MultipartFile file) {
        List<NinjaShiftRow> rows;
        try {
            rows = parser.parse(file);
        } catch (ExcelParserService.ExcelParseException e) {
            throw new BusinessException("PARSE_ERROR", e.getMessage());
        }
        if (rows.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "الملف لا يحتوي على بيانات");
        }
        return rows;
    }

    private void applyRow(Shift shift, NinjaShiftRow row, Supplier supplier,
                          Captain captain, ImportBatch batch) {
        shift.setExternalShiftId(row.getExternalShiftId());
        shift.setNinjaCaptainId(row.getNinjaCaptainId());
        shift.setCaptain(captain);
        shift.setSupplier(supplier);
        shift.setShiftDate(row.getShiftDate());
        shift.setSupplierExternalId(row.getSupplierExternalId());
        shift.setSupplierName(row.getSupplierName());
        shift.setCity(row.getCity());
        shift.setZoneName(row.getZoneName() != null ? row.getZoneName() : row.getShiftName());
        shift.setShiftName(row.getShiftName());
        shift.setNinjaCaptainName(row.getNinjaCaptainName());
        shift.setScheduledStart(row.getScheduledStart());
        shift.setScheduledEnd(row.getScheduledEnd());
        shift.setActualCheckin(row.getActualCheckin());
        shift.setAttendanceFlag(row.getAttendanceFlag());
        shift.setDelayMinutes(row.getDelayMinutes());
        shift.setWorkingHours(row.getWorkingHours());
        shift.setShiftTypeHours(row.getShiftTypeHours());
        shift.setTotalDelivered(row.getTotalDelivered());
        shift.setOfflineTime(row.getOfflineTime());
        shift.setFreeTime(row.getFreeTime());
        shift.setBusyTime(row.getBusyTime());
        shift.setAwayFromZone(row.getAwayFromZone());
        shift.setBusyUtilization(row.getBusyUtilization());
        shift.setDeliveryOntime(row.getDeliveryOntime());
        shift.setHourlyUtr(row.getHourlyUtr());
        shift.setImportBatch(batch);
        // A manual exclusion is a human decision about this shift — a re-import must not undo it.
    }

    private List<String> validate(NinjaShiftRow row) {
        List<String> problems = new ArrayList<>();
        if (row.getExternalShiftId() == null) problems.add("رقم الشفت مفقود");
        if (row.getNinjaCaptainId() == null) problems.add("معرف الكابتن لدى المورد مفقود");
        if (row.getShiftDate() == null) problems.add("تاريخ الشفت مفقود أو غير مقروء");
        return problems;
    }

    private Supplier requireSupplier() {
        return supplierRepo.findByCode("NINJA")
                .orElseThrow(() -> new BusinessException("CONFIG_ERROR", "لم يتم العثور على مورد نينجا"));
    }

    private SupplierIdSlot findOrCreateSlot(Supplier supplier, String externalId) {
        return slotRepo.findByExternalIdAndSupplierId(externalId, supplier.getId())
                .orElseGet(() -> slotRepo.save(SupplierIdSlot.builder()
                        .externalId(externalId)
                        .supplier(supplier)
                        .isActive(true)
                        .notes("أُنشئ تلقائيًا أثناء استيراد الشيفتات")
                        .build()));
    }

    /** Read-only lookup: who held this supplier ID on that date, if anyone. */
    private Captain resolveCaptain(Supplier supplier, String externalId, LocalDate date) {
        if (date == null) return null;
        return slotRepo.findByExternalIdAndSupplierId(externalId, supplier.getId())
                .map(slot -> holderOn(slot, date))
                .orElse(null);
    }

    /**
     * Who a shift on {@code date} belongs to.
     *
     * <p>Normally that is whoever the assignment covers. The second case exists because
     * linking happens after the fact: an ID is linked today, then last week's file is
     * imported, and a link starting today covers none of it — which is why an import can
     * report every row unmatched while every ID looks linked on screen.
     *
     * <p>So a shift dated before the ID was ever linked falls back to its holder, but only
     * when that holder is the ID's <em>only</em> assignment — nobody else has ever claimed
     * it, so there is no one to misattribute the shift to. An ID that has changed hands
     * carries a genuinely ambiguous past: those rows stay unmatched and are reported for
     * someone to resolve by hand. A closed assignment does not pull in later shifts either
     * — unlinking is a deliberate decision about what comes after it.
     */
    private Captain holderOn(SupplierIdSlot slot, LocalDate date) {
        if (date == null) return null;

        Optional<SupplierIdAssignment> covering = assignmentRepo.findActiveOn(slot.getId(), date);
        if (covering.isPresent()) {
            return covering.get().getCaptain();
        }

        List<SupplierIdAssignment> history =
                assignmentRepo.findBySupplierIdSlotIdOrderByValidFromDesc(slot.getId());
        if (history.size() != 1) return null;

        SupplierIdAssignment only = history.get(0);
        return date.isBefore(only.getValidFrom()) ? only.getCaptain() : null;
    }

    private List<PayrollRun> lockedRunsFor(LocalDate from, LocalDate to) {
        if (from == null || to == null) return List.of();
        return payrollRunRepo.findLockedOverlapping(from, to);
    }

    private void persistIssues(ImportBatch batch, List<ImportIssue> issues) {
        for (ImportIssue iss : issues) {
            issueRepo.save(ImportRowIssue.builder()
                    .importBatch(batch)
                    .rowNumber(iss.getRowNumber())
                    .severity(iss.getSeverity())
                    .issueCode(iss.getIssueCode())
                    .messageAr(iss.getMessage())
                    .build());
        }
    }

    private static int count(List<ImportIssue> issues, String severity) {
        return (int) issues.stream().filter(i -> severity.equals(i.getSeverity())).count();
    }

    private static LocalDate minDate(List<NinjaShiftRow> rows) {
        return rows.stream().map(NinjaShiftRow::getShiftDate)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }

    private static LocalDate maxDate(List<NinjaShiftRow> rows) {
        return rows.stream().map(NinjaShiftRow::getShiftDate)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    private static ImportIssue issue(int row, String severity, String code, String message) {
        return ImportIssue.builder()
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
