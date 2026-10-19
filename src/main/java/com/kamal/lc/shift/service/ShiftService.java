package com.kamal.lc.shift.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.changelog.service.ChangeLogWriter;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.payroll.entity.PayrollRun;
import com.kamal.lc.payroll.repository.PayrollRunRepository;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.dto.ShiftFilter;
import com.kamal.lc.shift.dto.ShiftResponse;
import com.kamal.lc.shift.dto.ShiftUpdateRequest;
import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {

    private final ShiftRepository shiftRepo;
    private final ShiftCalculator calculator;
    private final SettingsResolver settingsResolver;
    private final PayrollRunRepository payrollRunRepo;
    private final com.kamal.lc.auth.service.CaptainAllocationService allocationService;
    private final ChangeLogWriter changeLogWriter;

    /** Arabic labels for shift fields — used by the changelog writer. */
    private static final Map<String, String> SHIFT_FIELD_LABELS = Map.ofEntries(
            Map.entry("shiftDate", "تاريخ الشيفت"),
            Map.entry("ninjaCaptainId", "معرف الكابتن (المورد)"),
            Map.entry("ninjaCaptainName", "اسم الكابتن (المورد)"),
            Map.entry("zoneName", "المنطقة"),
            Map.entry("shiftName", "اسم الشيفت"),
            Map.entry("attendanceFlag", "حالة الحضور"),
            Map.entry("workingHours", "ساعات العمل"),
            Map.entry("shiftTypeHours", "ساعات نوع الشيفت"),
            Map.entry("totalDelivered", "عدد التوصيلات"),
            Map.entry("offlineTime", "وقت الأوفلاين"),
            Map.entry("freeTime", "الوقت الحر"),
            Map.entry("busyTime", "وقت الانشغال"),
            Map.entry("awayFromZone", "خارج المنطقة"),
            Map.entry("busyUtilization", "نسبة الانشغال"),
            Map.entry("deliveryOntime", "التوصيل في الوقت"),
            Map.entry("hourlyUtr", "معدل UTR"),
            Map.entry("delayMinutes", "دقائق التأخير"),
            Map.entry("supplierDeduction", "خصم المورد"),
            Map.entry("supplierDeductionReason", "سبب خصم المورد")
    );

    public Page<ShiftResponse> search(ShiftFilter filter, Pageable pageable) {
        return shiftRepo.findAll(toSpecification(filter), pageable).map(ShiftService::toResponse);
    }

    /** Every row matching the filter, unpaged — for the Excel export of what is on screen. */
    public List<ShiftResponse> searchAll(ShiftFilter filter, Sort sort) {
        return shiftRepo.findAll(toSpecification(filter), sort)
                .stream().map(ShiftService::toResponse).toList();
    }

    public ShiftResponse getById(UUID id) {
        return toResponse(shiftRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الشفت غير موجود")));
    }

    public Page<ShiftResponse> getUnmatched(Pageable pageable) {
        return shiftRepo.findUnmatched(pageable).map(ShiftService::toResponse);
    }

    public List<String> getZones() {
        return shiftRepo.findDistinctZones();
    }

    public List<ShiftResponse> getForCaptain(UUID captainId, LocalDate from, LocalDate to) {
        return shiftRepo.findByCaptainIdAndShiftDateBetweenOrderByShiftDateAsc(captainId, from, to)
                .stream().map(ShiftService::toResponse).toList();
    }

    // ──────── Shift Editing ────────

    /**
     * Updates a shift's editable fields, re-runs the calculator, and logs every
     * changed field into the changelog.
     */
    @Transactional
    public ShiftResponse updateShift(UUID id, ShiftUpdateRequest request, String username) {
        Shift shift = shiftRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الشفت غير موجود"));

        assertPeriodOpen(shift.getShiftDate());
        if (request.getShiftDate() != null) {
            assertPeriodOpen(request.getShiftDate());
        }

        // Snapshot before update
        Map<String, Object> before = ChangeLogWriter.snapshot(shift, SHIFT_FIELD_LABELS);

        // Apply changes
        if (request.getShiftDate() != null) shift.setShiftDate(request.getShiftDate());
        if (request.getNinjaCaptainId() != null) shift.setNinjaCaptainId(request.getNinjaCaptainId());
        if (request.getNinjaCaptainName() != null) shift.setNinjaCaptainName(request.getNinjaCaptainName());
        if (request.getZoneName() != null) shift.setZoneName(request.getZoneName());
        if (request.getShiftName() != null) shift.setShiftName(request.getShiftName());
        if (request.getAttendanceFlag() != null) shift.setAttendanceFlag(request.getAttendanceFlag());
        if (request.getWorkingHours() != null) shift.setWorkingHours(request.getWorkingHours());
        if (request.getShiftTypeHours() != null) shift.setShiftTypeHours(request.getShiftTypeHours());
        if (request.getTotalDelivered() != null) shift.setTotalDelivered(request.getTotalDelivered());
        if (request.getOfflineTime() != null) shift.setOfflineTime(request.getOfflineTime());
        if (request.getFreeTime() != null) shift.setFreeTime(request.getFreeTime());
        if (request.getBusyTime() != null) shift.setBusyTime(request.getBusyTime());
        if (request.getAwayFromZone() != null) shift.setAwayFromZone(request.getAwayFromZone());
        if (request.getBusyUtilization() != null) shift.setBusyUtilization(request.getBusyUtilization());
        if (request.getDeliveryOntime() != null) shift.setDeliveryOntime(request.getDeliveryOntime());
        if (request.getHourlyUtr() != null) shift.setHourlyUtr(request.getHourlyUtr());
        if (request.getDelayMinutes() != null) shift.setDelayMinutes(request.getDelayMinutes());
        if (request.getSupplierDeduction() != null) shift.setSupplierDeduction(request.getSupplierDeduction());
        if (request.getSupplierDeductionReason() != null) shift.setSupplierDeductionReason(request.getSupplierDeductionReason());

        // Recalculate computed values
        Captain captain = shift.getCaptain();
        calculator.apply(shift, captain, settingsResolver.resolve(shift.getShiftDate()));

        Shift saved = shiftRepo.save(shift);

        // Log changes (snapshot after update)
        Map<String, Object> after = ChangeLogWriter.snapshot(saved, SHIFT_FIELD_LABELS);
        String label = buildShiftLabel(saved);
        changeLogWriter.logFieldChanges("SHIFT", saved.getId(), label,
                before, after, SHIFT_FIELD_LABELS, username);

        return toResponse(saved);
    }

    // ──────── Shift Exclusion ────────

    /**
     * Marks a shift in or out of the calculation — for supplier-side mistakes that would
     * otherwise deduct a captain's whole day. The raw row is kept either way; only the
     * computed money changes.
     */
    @Transactional
    public ShiftResponse setExcluded(UUID id, boolean excluded, String reason, String username) {
        Shift shift = shiftRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الشفت غير موجود"));

        assertPeriodOpen(shift.getShiftDate());

        if (excluded && (reason == null || reason.isBlank())) {
            throw new BusinessException("REASON_REQUIRED", "سبب الاستبعاد مطلوب");
        }

        shift.setExcluded(excluded);
        shift.setExclusionReason(excluded ? reason.trim() : null);

        Captain captain = shift.getCaptain();
        calculator.apply(shift, captain, settingsResolver.resolve(shift.getShiftDate()));

        Shift saved = shiftRepo.save(shift);

        // Log to changelog
        String label = buildShiftLabel(saved);
        changeLogWriter.logAction("SHIFT", saved.getId(), label,
                excluded ? "EXCLUDE" : "RESTORE",
                excluded ? "مفعّل" : "مستبعد",
                excluded ? "مستبعد" : "مفعّل",
                reason, username != null ? username : "نظام");

        return toResponse(saved);
    }

    /** Backward-compatible overload for callers that don't pass a username. */
    @Transactional
    public ShiftResponse setExcluded(UUID id, boolean excluded, String reason) {
        return setExcluded(id, excluded, reason, "نظام");
    }

    // ──────── Supplier Deduction Exclusion ────────

    /**
     * Excludes a supplier deduction: saves the original value, zeroes the deduction,
     * and requires a reason. Logged in the changelog.
     */
    @Transactional
    public ShiftResponse excludeSupplierDeduction(UUID shiftId, String reason, String username) {
        Shift shift = shiftRepo.findById(shiftId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الشفت غير موجود"));

        assertPeriodOpen(shift.getShiftDate());

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "سبب استبعاد الخصم مطلوب");
        }
        if (shift.isSupplierDeductionExcluded()) {
            throw new BusinessException("ALREADY_EXCLUDED", "خصم المورد مستبعد بالفعل");
        }

        BigDecimal original = shift.getSupplierDeduction() != null ? shift.getSupplierDeduction() : BigDecimal.ZERO;
        shift.setSupplierDeductionOriginal(original);
        shift.setSupplierDeduction(BigDecimal.ZERO);
        shift.setSupplierDeductionExcluded(true);
        shift.setSupplierDeductionExclusionReason(reason.trim());

        Shift saved = shiftRepo.save(shift);

        String label = buildShiftLabel(saved);
        changeLogWriter.logAction("SHIFT", saved.getId(), label,
                "EXCLUDE_DEDUCTION",
                original.toPlainString(),
                "0",
                reason.trim(), username);

        return toResponse(saved);
    }

    /**
     * Restores a previously excluded supplier deduction back to its original value.
     */
    @Transactional
    public ShiftResponse restoreSupplierDeduction(UUID shiftId, String username) {
        Shift shift = shiftRepo.findById(shiftId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الشفت غير موجود"));

        assertPeriodOpen(shift.getShiftDate());

        if (!shift.isSupplierDeductionExcluded()) {
            throw new BusinessException("NOT_EXCLUDED", "خصم المورد غير مستبعد");
        }

        BigDecimal original = shift.getSupplierDeductionOriginal() != null
                ? shift.getSupplierDeductionOriginal() : BigDecimal.ZERO;
        shift.setSupplierDeduction(original);
        shift.setSupplierDeductionExcluded(false);
        shift.setSupplierDeductionOriginal(null);
        shift.setSupplierDeductionExclusionReason(null);

        Shift saved = shiftRepo.save(shift);

        String label = buildShiftLabel(saved);
        changeLogWriter.logAction("SHIFT", saved.getId(), label,
                "RESTORE_DEDUCTION",
                "0",
                original.toPlainString(),
                null, username);

        return toResponse(saved);
    }

    // ──────── Internal Helpers ────────

    private void assertPeriodOpen(LocalDate date) {
        List<PayrollRun> locked = payrollRunRepo.findLockedOverlapping(date, date);
        if (!locked.isEmpty()) {
            PayrollRun run = locked.get(0);
            throw new BusinessException("PERIOD_LOCKED",
                    "لا يمكن التعديل: الفترة " + run.getPeriodFrom() + " إلى " + run.getPeriodTo()
                            + " مقفلة بمسير رواتب معتمد");
        }
    }

    private static String buildShiftLabel(Shift s) {
        String captainName = s.getCaptain() != null ? s.getCaptain().getFullNameAr() : s.getNinjaCaptainName();
        return (captainName != null ? captainName : "غير معروف") + " — " + s.getShiftDate();
    }

    private Specification<Shift> toSpecification(ShiftFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            List<UUID> accessibleIds = allocationService.getCurrentUserAccessibleCaptainIds();
            if (accessibleIds != null) {
                if (accessibleIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("captain").get("id").in(accessibleIds));
                }
            }

            if (filter.getFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("shiftDate"), filter.getFrom()));
            }
            if (filter.getTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("shiftDate"), filter.getTo()));
            }
            if (filter.getCaptainId() != null) {
                predicates.add(cb.equal(root.get("captain").get("id"), filter.getCaptainId()));
            }
            if (filter.getZoneName() != null && !filter.getZoneName().isBlank()) {
                predicates.add(cb.equal(root.get("zoneName"), filter.getZoneName()));
            }
            if (filter.getAttended() != null) {
                predicates.add(cb.equal(root.get("attended"), filter.getAttended()));
            }
            if (filter.getExcluded() != null) {
                predicates.add(cb.equal(root.get("excluded"), filter.getExcluded()));
            }
            if (Boolean.TRUE.equals(filter.getUnmatched())) {
                predicates.add(cb.isNull(root.get("captain")));
            }
            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String term = "%" + filter.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("ninjaCaptainId")), term),
                        cb.like(cb.lower(root.get("ninjaCaptainName")), term),
                        cb.like(cb.lower(root.get("externalShiftId")), term)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static ShiftResponse toResponse(Shift s) {
        Captain captain = s.getCaptain();
        return ShiftResponse.builder()
                .id(s.getId())
                .externalShiftId(s.getExternalShiftId())
                .shiftDate(s.getShiftDate() != null ? s.getShiftDate().toString() : null)
                .captainId(captain != null ? captain.getId() : null)
                .captainCode(captain != null ? captain.getKamalCode() : null)
                .captainName(captain != null ? captain.getFullNameAr() : null)
                .ninjaCaptainId(s.getNinjaCaptainId())
                .ninjaCaptainName(s.getNinjaCaptainName())
                .supplierName(s.getSupplierName())
                .city(s.getCity())
                .zoneName(s.getZoneName())
                .shiftName(s.getShiftName())
                .scheduledStart(str(s.getScheduledStart()))
                .scheduledEnd(str(s.getScheduledEnd()))
                .actualCheckin(str(s.getActualCheckin()))
                .attendanceFlag(s.getAttendanceFlag())
                .delayMinutes(s.getDelayMinutes())
                .workingHours(s.getWorkingHours())
                .shiftTypeHours(s.getShiftTypeHours())
                .totalDelivered(s.getTotalDelivered())
                .offlineTime(s.getOfflineTime())
                .freeTime(s.getFreeTime())
                .busyTime(s.getBusyTime())
                .awayFromZone(s.getAwayFromZone())
                .busyUtilization(s.getBusyUtilization())
                .deliveryOntime(s.getDeliveryOntime())
                .hourlyUtr(s.getHourlyUtr())
                .attended(s.getAttended())
                .dailyWage(nz(s.getDailyWage()))
                .lateDeduction(nz(s.getLateDeduction()))
                .extraOrders(s.getExtraOrders())
                .dailyIncentive(nz(s.getDailyIncentive()))
                .fuelAllowance(nz(s.getFuelAllowance()))
                .carAllowance(nz(s.getCarAllowance()))
                .netShiftPay(nz(s.getNetShiftPay()))
                .qualifiesFullRate(s.getQualifiesFullRate())
                .supplierRevenue(nz(s.getSupplierRevenue()))
                .supplierExtraRevenue(nz(s.getSupplierExtraRevenue()))
                .shiftMargin(nz(s.getShiftMargin()))
                .supplierDeduction(nz(s.getSupplierDeduction()))
                .supplierDeductionReason(s.getSupplierDeductionReason())
                .supplierDeductionExcluded(s.isSupplierDeductionExcluded())
                .supplierDeductionOriginal(s.getSupplierDeductionOriginal())
                .supplierDeductionExclusionReason(s.getSupplierDeductionExclusionReason())
                .calcNote(s.getCalcNote())
                .excluded(s.isExcluded())
                .exclusionReason(s.getExclusionReason())
                .build();
    }

    private static String str(java.time.OffsetDateTime value) {
        return value != null ? value.toString() : null;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
