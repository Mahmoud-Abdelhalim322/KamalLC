package com.kamal.lc.payroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kamal.lc.loan.service.LoanService;
import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.payroll.dto.StaffPayrollLineResponse;
import com.kamal.lc.payroll.entity.StaffPayrollLine;
import com.kamal.lc.payroll.repository.StaffPayrollLineRepository;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.repository.StaffRepository;
import com.kamal.lc.payroll.dto.AdjustmentRequest;
import com.kamal.lc.payroll.dto.AdjustmentResponse;
import com.kamal.lc.payroll.dto.PayrollLineResponse;
import com.kamal.lc.payroll.dto.PayrollRunResponse;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import com.kamal.lc.payroll.entity.PayrollLine;
import com.kamal.lc.payroll.entity.PayrollRun;
import com.kamal.lc.payroll.entity.StaffAdjustment;
import com.kamal.lc.payroll.repository.CaptainAdjustmentRepository;
import com.kamal.lc.payroll.repository.PayrollLineRepository;
import com.kamal.lc.payroll.repository.PayrollRunRepository;
import com.kamal.lc.payroll.repository.StaffAdjustmentRepository;
import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.repository.ShiftRepository;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds and freezes monthly payrolls.
 *
 * <p>A run is generated from the stored shifts, not recomputed on read: once approved it
 * must keep reporting what was actually paid, whatever happens to the shifts or the rates
 * afterwards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollService {

    private final PayrollRunRepository runRepo;
    private final PayrollLineRepository lineRepo;
    private final StaffPayrollLineRepository staffLineRepo;
    private final CaptainAdjustmentRepository adjustmentRepo;
    private final StaffAdjustmentRepository staffAdjustmentRepo;
    private final com.kamal.lc.supplier.repository.SupplierAdjustmentRepository supplierAdjustmentRepo;
    private final CaptainRepository captainRepo;
    private final StaffRepository staffRepo;
    private final ShiftRepository shiftRepo;
    private final SupplierIdAssignmentRepository assignmentRepo;
    private final SettingsResolver settingsResolver;
    private final PayrollCalculator calculator;
    private final ObjectMapper objectMapper;
    private final LoanService loanService;
    private final com.kamal.lc.imports.repository.VacationCommissionRepository vacationCommissionRepo;

    // ---- Runs ----

    @Transactional(readOnly = true)
    public Page<PayrollRunResponse> list(Pageable pageable) {
        return runRepo.findAllByOrderByPeriodFromDesc(pageable).map(this::toRunResponse);
    }

    @Transactional(readOnly = true)
    public PayrollRunResponse get(UUID runId) {
        return toRunResponse(requireRun(runId));
    }

    @Transactional(readOnly = true)
    public List<PayrollLineResponse> getLines(UUID runId) {
        PayrollRun run = requireRun(runId);

        Map<UUID, List<CaptainAdjustment>> adjustmentsByCaptain =
                adjustmentRepo.findInPeriod(run.getPeriodFrom(), run.getPeriodTo()).stream()
                        .collect(Collectors.groupingBy(a -> a.getCaptain().getId()));

        return lineRepo.findByPayrollRunIdOrderByCaptainCodeAsc(runId).stream()
                .map(line -> {
                    BigDecimal activeLoan = loanService.getActiveLoanBalance(line.getCaptain().getId(), "CAPTAIN");
                    return toLineResponse(line, adjustmentsByCaptain
                            .getOrDefault(line.getCaptain().getId(), List.of()), activeLoan);
                })
                .toList();
    }

    /** Efficient single-line retrieval for payslips — avoids loading all lines (#5). */
    @Transactional(readOnly = true)
    public PayrollLineResponse getLine(UUID runId, UUID captainId) {
        PayrollRun run = requireRun(runId);
        PayrollLine line = lineRepo.findByPayrollRunIdAndCaptainId(runId, captainId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود في المسير"));

        List<CaptainAdjustment> adjustments = adjustmentRepo.findForCaptainInPeriod(
                captainId, run.getPeriodFrom(), run.getPeriodTo());
        BigDecimal activeLoan = loanService.getActiveLoanBalance(captainId, "CAPTAIN");
        return toLineResponse(line, adjustments, activeLoan);
    }

    /** Efficient single staff line retrieval for payslips — avoids loading all lines (#5). */
    @Transactional(readOnly = true)
    public StaffPayrollLineResponse getStaffLine(UUID runId, UUID staffId) {
        PayrollRun run = requireRun(runId);
        StaffPayrollLine staffLine = staffLineRepo.findByPayrollRunIdAndStaffId(runId, staffId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود في المسير"));

        List<StaffAdjustment> adjustments = staffAdjustmentRepo.findForStaffInPeriod(
                staffId, run.getPeriodFrom(), run.getPeriodTo());
        BigDecimal activeLoan = loanService.getActiveLoanBalance(staffId, staffLine.getStaffType());
        return toStaffLineResponse(staffLine, adjustments, activeLoan);
    }

    /**
     * Builds (or rebuilds) the run for a period. Rebuilding a draft replaces its lines;
     * an approved run refuses, because its numbers have already been acted on.
     */
    @Transactional
    public PayrollRunResponse generate(LocalDate periodFrom, LocalDate periodTo, String title) {
        if (periodTo.isBefore(periodFrom)) {
            throw new BusinessException("INVALID_PERIOD", "تاريخ نهاية الفترة يجب ألا يسبق بدايتها");
        }

        // Prevent overlapping payroll periods (#10)
        PayrollRun run = runRepo.findByPeriodFromAndPeriodTo(periodFrom, periodTo).orElse(null);
        List<PayrollRun> overlapping = new ArrayList<>(runRepo.findOverlapping(periodFrom, periodTo));
        // Exclude the exact same period (that's a rebuild, not an overlap)
        overlapping.removeIf(r -> r.getPeriodFrom().equals(periodFrom) && r.getPeriodTo().equals(periodTo));
        if (!overlapping.isEmpty()) {
            PayrollRun first = overlapping.get(0);
            throw new BusinessException("OVERLAPPING_PERIOD",
                    "الفترة تتداخل مع مسير آخر: " + first.getPeriodFrom() + " إلى " + first.getPeriodTo()
                    + " (" + first.getTitle() + ")");
        }

        if (run != null && run.isLocked()) {
            throw new BusinessException("RUN_LOCKED",
                    "مسير هذه الفترة معتمد بالفعل — أنشئ فترة جديدة أو ألغِ الاعتماد");
        }

        // Rates are read as of the period end: one run, one rate set, recorded alongside it.
        EffectiveSettings settings = settingsResolver.resolve(periodTo);

        if (run == null) {
            run = PayrollRun.builder()
                    .periodFrom(periodFrom)
                    .periodTo(periodTo)
                    .title(title != null && !title.isBlank() ? title.trim() : defaultTitle(periodFrom))
                    .status(PayrollRun.DRAFT)
                    .generatedBy(currentUser())
                    .build();
        } else {
            // Reverse any loan deductions from the previous generation
            loanService.reversePayrollDeductions(run);
            lineRepo.deleteByPayrollRunId(run.getId());
            lineRepo.flush();
            run.setGeneratedAt(OffsetDateTime.now());
            run.setGeneratedBy(currentUser());
            if (title != null && !title.isBlank()) {
                run.setTitle(title.trim());
            }
        }
        run.setSettingsSnapshot(toJson(settings.getRawValues()));
        run = runRepo.save(run);

        Map<UUID, BigDecimal> adjustmentTotals = adjustmentTotals(periodFrom, periodTo);
        Map<UUID, BigDecimal> vacationCommissions = vacationCommissionRepo.aggregateByCaptain(periodFrom, periodTo).stream()
                .collect(Collectors.toMap(com.kamal.lc.imports.repository.VacationCommissionRepository.CaptainVacationCommissionTotals::getCaptainId,
                        com.kamal.lc.imports.repository.VacationCommissionRepository.CaptainVacationCommissionTotals::getTotalVacationCommission));

        // Batch-load captains and supplier IDs to avoid N+1 queries (#4)
        List<ShiftRepository.CaptainPeriodTotals> allTotals = shiftRepo.aggregateByCaptain(periodFrom, periodTo);
        Set<UUID> captainIds = allTotals.stream()
                .map(ShiftRepository.CaptainPeriodTotals::getCaptainId)
                .collect(Collectors.toSet());

        Map<UUID, Captain> captainsMap = captainRepo.findAllById(captainIds).stream()
                .collect(Collectors.toMap(Captain::getId, c -> c));

        // Pre-load supplier IDs for all captains at once
        Map<UUID, String> supplierIdsMap = new HashMap<>();
        if (!captainIds.isEmpty()) {
            assignmentRepo.findByCaptainIdIn(captainIds).stream()
                    .collect(Collectors.groupingBy(a -> a.getCaptain().getId()))
                    .forEach((capId, assignments) -> {
                        List<String> ids = assignments.stream()
                                .map(a -> a.getSupplierIdSlot().getExternalId())
                                .distinct()
                                .toList();
                        supplierIdsMap.put(capId, ids.isEmpty() ? null : String.join(", ", ids));
                    });
        }

        List<PayrollLine> lines = new ArrayList<>();
        for (ShiftRepository.CaptainPeriodTotals totals : allTotals) {
            Captain captain = captainsMap.get(totals.getCaptainId());
            if (captain == null) continue;

            BigDecimal adjustments = adjustmentTotals.getOrDefault(captain.getId(), BigDecimal.ZERO);
            BigDecimal vacationComm = vacationCommissions.getOrDefault(captain.getId(), BigDecimal.ZERO);

            PayrollCalculator.Result result = calculator.compute(new PayrollCalculator.Input(
                    captain.getCaptainType(),
                    captain.isUsesOwnVehicle(),
                    (int) totals.getAttendedShifts(),
                    (int) totals.getTotalOrders(),
                    totals.getLateDeductions(),
                    totals.getSupplierDeductions(),
                    totals.getDailyIncentives(),
                    totals.getFuelAllowances(),
                    totals.getCarAllowances(),
                    vacationComm,
                    adjustments
            ), settings);

            BigDecimal revenue = nz(totals.getSupplierRevenueTotal());

            lines.add(PayrollLine.builder()
                    .payrollRun(run)
                    .captain(captain)
                    .captainCode(captain.getKamalCode())
                    .captainName(captain.getFullNameAr())
                    .captainNameEn(captain.getFullNameEn())
                    .captainType(captain.getCaptainType())
                    .supplierExternalIds(supplierIdsMap.get(captain.getId()))
                    .attendedShifts((int) totals.getAttendedShifts())
                    .absentShifts((int) totals.getAbsentShifts())
                    .totalOrders((int) totals.getTotalOrders())
                    .totalHours(nz(totals.getTotalHours()))
                    .avgBusyUtilization(totals.getAvgBusyUtilization())
                    .avgHourlyUtr(totals.getAvgHourlyUtr())
                    .avgDeliveryOntime(totals.getAvgDeliveryOntime())
                    .basePay(result.basePay())
                    .lateDeductions(result.lateDeductions())
                    .supplierDeductions(result.supplierDeductions())
                    .dailyIncentives(result.dailyIncentives())
                    .fuelAllowances(result.fuelAllowances())
                    .carAllowances(result.carAllowances())
                    .targetBonus(result.targetBonus())
                    .extraOrderPay(result.extraOrderPay())
                    .vacationCommission(result.vacationCommission())
                    .gross(result.gross())
                    .adjustmentsTotal(result.adjustmentsTotal())
                    .net(result.net())
                    .supplierRevenueTotal(revenue)
                    .margin(revenue.subtract(result.net()))
                    .build());
        }

        lineRepo.saveAll(lines);

        // Generate staff payroll lines (workers + managers)
        generateStaffLines(run);

        // Auto-create loan deductions for all active loans
        loanService.processPayrollDeductions(run);

        log.info("Generated payroll {} → {} with {} captain lines", periodFrom, periodTo, lines.size());

        return toRunResponse(run);
    }

    /**
     * Generates payroll lines for all active staff (workers + managers).
     * Their salary is simple: basic + housing + other allowances.
     */
    private void generateStaffLines(PayrollRun run) {
        staffLineRepo.deleteByPayrollRunId(run.getId());
        staffLineRepo.flush();

        List<Staff> activeStaff = new java.util.ArrayList<>(staffRepo.findByStaffTypeAndStatus("WORKER", "ACTIVE"));
        activeStaff.addAll(staffRepo.findByStaffTypeAndStatus("MANAGER", "ACTIVE"));

        Map<UUID, BigDecimal> staffAdjs = staffAdjustmentRepo.findInPeriod(run.getPeriodFrom(), run.getPeriodTo())
                .stream().collect(Collectors.groupingBy(a -> a.getStaff().getId(),
                        Collectors.reducing(BigDecimal.ZERO, (StaffAdjustment a) -> signedAmount(a), BigDecimal::add)));

        List<StaffPayrollLine> staffLines = new ArrayList<>();
        for (Staff s : activeStaff) {
            BigDecimal basic = nz(s.getBasicSalary());
            BigDecimal housing = nz(s.getHousingAllowance());
            BigDecimal other = nz(s.getOtherAllowances());
            BigDecimal gross = basic.add(housing).add(other);
            BigDecimal adjustments = staffAdjs.getOrDefault(s.getId(), BigDecimal.ZERO);

            staffLines.add(StaffPayrollLine.builder()
                    .payrollRun(run)
                    .staff(s)
                    .staffCode(s.getStaffCode())
                    .staffName(s.getFullNameAr())
                    .staffType(s.getStaffType())
                    .jobTitle(s.getJobTitle())
                    .basicSalary(basic)
                    .housingAllowance(housing)
                    .otherAllowances(other)
                    .adjustmentsTotal(adjustments)
                    .gross(gross)
                    .net(gross.add(adjustments))
                    .build());
        }

        staffLineRepo.saveAll(staffLines);
        log.info("Generated {} staff payroll lines", staffLines.size());
    }

    /** Freezes the run. Shift imports and edits touching its period are refused afterwards. */
    @Transactional
    public PayrollRunResponse approve(UUID runId) {
        PayrollRun run = requireRun(runId);
        if (run.isLocked()) {
            throw new BusinessException("ALREADY_APPROVED", "هذا المسير معتمد بالفعل");
        }
        if (lineRepo.findByPayrollRunIdOrderByCaptainCodeAsc(runId).isEmpty()) {
            throw new BusinessException("EMPTY_RUN", "لا يمكن اعتماد مسير بدون أسطر");
        }

        run.setStatus(PayrollRun.APPROVED);
        run.setApprovedAt(OffsetDateTime.now());
        run.setApprovedBy(currentUser());
        return toRunResponse(runRepo.save(run));
    }

    @Transactional
    public PayrollRunResponse markPaid(UUID runId) {
        PayrollRun run = requireRun(runId);
        if (!PayrollRun.APPROVED.equals(run.getStatus())) {
            throw new BusinessException("NOT_APPROVED", "يجب اعتماد المسير أولًا قبل تسجيله كمدفوع");
        }
        run.setStatus(PayrollRun.PAID);
        run.setPaidAt(OffsetDateTime.now());
        run.setPaidBy(currentUser());
        return toRunResponse(runRepo.save(run));
    }

    /** Reopens an approved run for correction. Deliberately separate from approving. */
    @Transactional
    public PayrollRunResponse reopen(UUID runId, String reason) {
        PayrollRun run = requireRun(runId);
        if (!run.isLocked()) {
            throw new BusinessException("NOT_LOCKED", "هذا المسير مسودة بالفعل");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "سبب إعادة الفتح مطلوب");
        }

        String trail = "أُعيد فتحه بواسطة " + currentUser() + " بتاريخ " + LocalDate.now()
                + " — السبب: " + reason.trim();
        run.setNotes(run.getNotes() == null ? trail : run.getNotes() + "\n" + trail);
        run.setStatus(PayrollRun.DRAFT);
        run.setApprovedAt(null);
        run.setApprovedBy(null);
        return toRunResponse(runRepo.save(run));
    }

    @Transactional
    public void delete(UUID runId) {
        PayrollRun run = requireRun(runId);
        if (run.isLocked()) {
            throw new BusinessException("RUN_LOCKED", "لا يمكن حذف مسير معتمد");
        }
        loanService.reversePayrollDeductions(run);
        runRepo.delete(run);
    }

    // ---- Adjustments ----

    @Transactional
    public PayrollLineResponse addAdjustment(UUID runId, AdjustmentRequest request) {
        PayrollRun run = requireDraft(runId);

        String type = request.getAdjustmentType().trim().toUpperCase();
        if (!CaptainAdjustment.BONUS_TYPES.contains(type)
                && !CaptainAdjustment.DEDUCTION_TYPES.contains(type)) {
            throw new BusinessException("INVALID_TYPE", "نوع البند غير معروف: " + type);
        }

        Captain captain = captainRepo.findById(request.getCaptainId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));

        CaptainAdjustment adjustment = adjustmentRepo.save(CaptainAdjustment.builder()
                .captain(captain)
                .periodFrom(run.getPeriodFrom())
                .periodTo(run.getPeriodTo())
                .kind(CaptainAdjustment.kindFor(type))
                .adjustmentType(type)
                .labelAr(request.getLabelAr() != null && !request.getLabelAr().isBlank()
                        ? request.getLabelAr().trim()
                        : CaptainAdjustment.defaultLabel(type))
                .amount(request.getAmount())
                .reason(request.getReason())
                .payrollRun(run)
                .createdBy(currentUser())
                .build());

        if ("LOAN".equals(type)) {
            loanService.processManualLoanDeduction(captain.getId(), "CAPTAIN", request.getAmount(), run, adjustment.getId(), null, request.getReason());
        }

        return refreshLine(run, captain.getId());
    }

    @Transactional
    public PayrollLineResponse deleteAdjustment(UUID runId, UUID adjustmentId) {
        PayrollRun run = requireDraft(runId);

        CaptainAdjustment adjustment = adjustmentRepo.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "البند غير موجود"));
        UUID captainId = adjustment.getCaptain().getId();

        if ("LOAN".equals(adjustment.getAdjustmentType())) {
            loanService.reverseManualLoanDeduction(adjustment.getId(), null);
        }

        adjustmentRepo.delete(adjustment);
        adjustmentRepo.flush();

        return refreshLine(run, captainId);
    }

    /**
     * Recomputes one line after its adjustments change, without regenerating the whole run —
     * the shift-derived totals on the line are still valid.
     */
    private PayrollLineResponse refreshLine(PayrollRun run, UUID captainId) {
        PayrollLine line = lineRepo.findByPayrollRunIdAndCaptainId(run.getId(), captainId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND",
                        "لا يوجد سطر لهذا الكابتن في المسير — أعد توليد المسير أولًا"));

        List<CaptainAdjustment> adjustments = adjustmentRepo.findForCaptainInPeriod(
                captainId, run.getPeriodFrom(), run.getPeriodTo());

        BigDecimal total = adjustments.stream()
                .map(PayrollService::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        line.setAdjustmentsTotal(total);
        line.setNet(line.getGross().add(total));
        line.setMargin(line.getSupplierRevenueTotal().subtract(line.getNet()));

        BigDecimal activeLoan = loanService.getActiveLoanBalance(captainId, "CAPTAIN");
        return toLineResponse(lineRepo.save(line), adjustments, activeLoan);
    }

    // ---- helpers ----

    private Map<UUID, BigDecimal> adjustmentTotals(LocalDate from, LocalDate to) {
        return adjustmentRepo.findInPeriod(from, to).stream()
                .collect(Collectors.groupingBy(
                        a -> a.getCaptain().getId(),
                        Collectors.reducing(BigDecimal.ZERO, PayrollService::signedAmount, BigDecimal::add)));
    }

    private static BigDecimal signedAmount(CaptainAdjustment a) {
        return CaptainAdjustment.BONUS.equals(a.getKind()) ? a.getAmount() : a.getAmount().negate();
    }

    private static BigDecimal signedAmount(StaffAdjustment a) {
        return StaffAdjustment.BONUS.equals(a.getKind()) ? a.getAmount() : a.getAmount().negate();
    }

    private String supplierIdsFor(UUID captainId) {
        List<String> ids = assignmentRepo.findByCaptainIdOrderByValidFromDesc(captainId).stream()
                .map(a -> a.getSupplierIdSlot().getExternalId())
                .distinct()
                .toList();
        return ids.isEmpty() ? null : String.join(", ", ids);
    }

    private PayrollRun requireRun(UUID runId) {
        return runRepo.findById(runId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المسير غير موجود"));
    }

    private PayrollRun requireDraft(UUID runId) {
        PayrollRun run = requireRun(runId);
        if (run.isLocked()) {
            throw new BusinessException("RUN_LOCKED", "لا يمكن تعديل مسير معتمد");
        }
        return run;
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private String toJson(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            log.warn("Could not serialise settings snapshot: {}", e.getMessage());
            return null;
        }
    }

    private static String defaultTitle(LocalDate periodFrom) {
        return "مسير رواتب " + periodFrom.getYear() + "-"
                + String.format("%02d", periodFrom.getMonthValue());
    }

    private PayrollRunResponse toRunResponse(PayrollRun run) {
        List<PayrollLine> lines = lineRepo.findByPayrollRunIdOrderByCaptainCodeAsc(run.getId());
        List<StaffPayrollLine> staffLines = staffLineRepo.findByPayrollRunIdOrderByStaffCodeAsc(run.getId());

        BigDecimal shiftRevenue = sum(lines, PayrollLine::getSupplierRevenueTotal);
        BigDecimal netSupplierAdjustments = supplierAdjustmentRepo.findByAdjustmentDateBetweenOrderByAdjustmentDateDesc(
                        run.getPeriodFrom(), run.getPeriodTo()).stream()
                .map(a -> "ADDITION".equalsIgnoreCase(a.getType()) ? a.getAmount() : a.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSupplierRev = shiftRevenue.add(netSupplierAdjustments);
        BigDecimal totalCaptainNet = sum(lines, PayrollLine::getNet);

        return PayrollRunResponse.builder()
                .id(run.getId())
                .periodFrom(run.getPeriodFrom().toString())
                .periodTo(run.getPeriodTo().toString())
                .status(run.getStatus())
                .title(run.getTitle())
                .generatedAt(run.getGeneratedAt() != null ? run.getGeneratedAt().toString() : null)
                .generatedBy(run.getGeneratedBy())
                .approvedAt(run.getApprovedAt() != null ? run.getApprovedAt().toString() : null)
                .approvedBy(run.getApprovedBy())
                .paidAt(run.getPaidAt() != null ? run.getPaidAt().toString() : null)
                .paidBy(run.getPaidBy())
                .notes(run.getNotes())
                .locked(run.isLocked())
                .captainCount(lines.size())
                .totalGross(sum(lines, PayrollLine::getGross))
                .totalNet(totalCaptainNet)
                .totalSupplierRevenue(totalSupplierRev)
                .totalMargin(totalSupplierRev.subtract(totalCaptainNet))
                .staffCount(staffLines.size())
                .totalStaffGross(sumStaff(staffLines, StaffPayrollLine::getGross))
                .totalStaffNet(sumStaff(staffLines, StaffPayrollLine::getNet))
                .build();
    }

    private static BigDecimal sum(List<PayrollLine> lines,
                                  java.util.function.Function<PayrollLine, BigDecimal> field) {
        return lines.stream().map(field).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumStaff(List<StaffPayrollLine> lines,
                                       java.util.function.Function<StaffPayrollLine, BigDecimal> field) {
        return lines.stream().map(field).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static PayrollLineResponse toLineResponse(PayrollLine line, List<CaptainAdjustment> adjustments, BigDecimal activeLoanBalance) {
        // Flag inactive captains so the UI can warn the user (#3)
        Captain captain = line.getCaptain();
        boolean inactive = captain != null && !"ACTIVE".equals(captain.getStatus());

        return PayrollLineResponse.builder()
                .id(line.getId())
                .captainId(captain != null ? captain.getId() : null)
                .captainCode(line.getCaptainCode())
                .captainName(line.getCaptainName())
                .captainNameEn(line.getCaptainNameEn())
                .captainType(line.getCaptainType())
                .supplierExternalIds(line.getSupplierExternalIds())
                .attendedShifts(line.getAttendedShifts())
                .absentShifts(line.getAbsentShifts())
                .totalOrders(line.getTotalOrders())
                .totalHours(line.getTotalHours())
                .avgBusyUtilization(line.getAvgBusyUtilization())
                .avgHourlyUtr(line.getAvgHourlyUtr())
                .avgDeliveryOntime(line.getAvgDeliveryOntime())
                .basePay(line.getBasePay())
                .lateDeductions(line.getLateDeductions())
                .supplierDeductions(line.getSupplierDeductions())
                .dailyIncentives(line.getDailyIncentives())
                .fuelAllowances(line.getFuelAllowances())
                .carAllowances(line.getCarAllowances())
                .targetBonus(line.getTargetBonus())
                .extraOrderPay(line.getExtraOrderPay())
                .vacationCommission(line.getVacationCommission())
                .gross(line.getGross())
                .adjustmentsTotal(line.getAdjustmentsTotal())
                .net(line.getNet())
                .supplierRevenueTotal(line.getSupplierRevenueTotal())
                .margin(line.getMargin())
                .activeLoanBalance(activeLoanBalance)
                .inactiveCaptain(inactive)
                .notes(line.getNotes())
                .adjustments(adjustments.stream().map(PayrollService::toAdjustmentResponse).toList())
                .build();
    }

    static AdjustmentResponse toAdjustmentResponse(CaptainAdjustment a) {
        return AdjustmentResponse.builder()
                .id(a.getId())
                .captainId(a.getCaptain().getId())
                .adjustmentType(a.getAdjustmentType())
                .kind(a.getKind())
                .labelAr(a.getLabelAr())
                .amount(a.getAmount())
                .signedAmount(signedAmount(a))
                .reason(a.getReason())
                .periodFrom(a.getPeriodFrom().toString())
                .periodTo(a.getPeriodTo().toString())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
                .build();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ---- Staff payroll lines ----

    @Transactional(readOnly = true)
    public List<StaffPayrollLineResponse> getStaffLines(UUID runId, String staffType) {
        PayrollRun run = requireRun(runId);
        List<StaffPayrollLine> lines;
        if (staffType != null && !staffType.isBlank()) {
            lines = staffLineRepo.findByPayrollRunIdAndStaffTypeOrderByStaffCodeAsc(runId, staffType.toUpperCase());
        } else {
            lines = staffLineRepo.findByPayrollRunIdOrderByStaffCodeAsc(runId);
        }

        Map<UUID, List<StaffAdjustment>> adjustmentsByStaff =
                staffAdjustmentRepo.findInPeriod(run.getPeriodFrom(), run.getPeriodTo()).stream()
                        .collect(Collectors.groupingBy(a -> a.getStaff().getId()));

        return lines.stream().map(line -> {
            BigDecimal activeLoan = loanService.getActiveLoanBalance(line.getStaff().getId(), line.getStaffType());
            return toStaffLineResponse(line, adjustmentsByStaff.getOrDefault(line.getStaff().getId(), List.of()), activeLoan);
        }).toList();
    }

    private StaffPayrollLineResponse toStaffLineResponse(StaffPayrollLine line, List<StaffAdjustment> adjustments, BigDecimal activeLoanBalance) {
        String jobTitle = (line.getJobTitle() != null && !line.getJobTitle().isBlank())
                ? line.getJobTitle()
                : (line.getStaff() != null ? line.getStaff().getJobTitle() : null);

        return StaffPayrollLineResponse.builder()
                .id(line.getId())
                .staffId(line.getStaff().getId())
                .staffCode(line.getStaffCode())
                .staffName(line.getStaffName())
                .staffType(line.getStaffType())
                .jobTitle(jobTitle)
                .basicSalary(line.getBasicSalary())
                .housingAllowance(line.getHousingAllowance())
                .otherAllowances(line.getOtherAllowances())
                .adjustmentsTotal(line.getAdjustmentsTotal())
                .gross(line.getGross())
                .net(line.getNet())
                .activeLoanBalance(activeLoanBalance)
                .notes(line.getNotes())
                .adjustments(adjustments.stream().map(PayrollService::toStaffAdjustmentResponse).toList())
                .build();
    }

    static com.kamal.lc.payroll.dto.StaffAdjustmentResponse toStaffAdjustmentResponse(StaffAdjustment a) {
        return com.kamal.lc.payroll.dto.StaffAdjustmentResponse.builder()
                .id(a.getId())
                .staffId(a.getStaff().getId())
                .adjustmentType(a.getAdjustmentType())
                .kind(a.getKind())
                .labelAr(a.getLabelAr())
                .amount(a.getAmount())
                .signedAmount(signedAmount(a))
                .reason(a.getReason())
                .periodFrom(a.getPeriodFrom().toString())
                .periodTo(a.getPeriodTo().toString())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
                .build();
    }

    @Transactional
    public StaffPayrollLineResponse addStaffAdjustment(UUID runId, com.kamal.lc.payroll.dto.StaffAdjustmentRequest request) {
        PayrollRun run = requireDraft(runId);

        String type = request.getAdjustmentType().trim().toUpperCase();
        if (!StaffAdjustment.BONUS_TYPES.contains(type)
                && !StaffAdjustment.DEDUCTION_TYPES.contains(type)) {
            throw new BusinessException("INVALID_TYPE", "نوع البند غير معروف: " + type);
        }

        Staff staff = staffRepo.findById(request.getStaffId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود"));

        StaffAdjustment adjustment = staffAdjustmentRepo.save(StaffAdjustment.builder()
                .staff(staff)
                .periodFrom(run.getPeriodFrom())
                .periodTo(run.getPeriodTo())
                .kind(StaffAdjustment.kindFor(type))
                .adjustmentType(type)
                .labelAr(request.getLabelAr() != null && !request.getLabelAr().isBlank()
                        ? request.getLabelAr().trim()
                        : StaffAdjustment.defaultLabel(type))
                .amount(request.getAmount())
                .reason(request.getReason())
                .payrollRun(run)
                .createdBy(currentUser())
                .build());

        if ("LOAN".equals(type)) {
            loanService.processManualLoanDeduction(staff.getId(), staff.getStaffType(), request.getAmount(), run, null, adjustment.getId(), request.getReason());
        }

        return refreshStaffLine(run, staff.getId());
    }

    @Transactional
    public StaffPayrollLineResponse deleteStaffAdjustment(UUID runId, UUID adjustmentId) {
        PayrollRun run = requireDraft(runId);

        StaffAdjustment adjustment = staffAdjustmentRepo.findById(adjustmentId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "البند غير موجود"));
        UUID staffId = adjustment.getStaff().getId();

        if ("LOAN".equals(adjustment.getAdjustmentType())) {
            loanService.reverseManualLoanDeduction(null, adjustment.getId());
        }

        staffAdjustmentRepo.delete(adjustment);
        staffAdjustmentRepo.flush();

        return refreshStaffLine(run, staffId);
    }

    private StaffPayrollLineResponse refreshStaffLine(PayrollRun run, UUID staffId) {
        StaffPayrollLine line = staffLineRepo.findByPayrollRunIdAndStaffId(run.getId(), staffId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND",
                        "لا يوجد سطر لهذا الموظف في المسير — أعد توليد المسير أولًا"));

        List<StaffAdjustment> adjustments = staffAdjustmentRepo.findForStaffInPeriod(
                staffId, run.getPeriodFrom(), run.getPeriodTo());

        BigDecimal total = adjustments.stream()
                .map(PayrollService::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        line.setAdjustmentsTotal(total);
        line.setNet(line.getGross().add(total));

        BigDecimal activeLoan = loanService.getActiveLoanBalance(staffId, line.getStaffType());
        return toStaffLineResponse(staffLineRepo.save(line), adjustments, activeLoan);
    }
}
