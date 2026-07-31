package com.kamal.lc.shift.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.payroll.entity.PayrollRun;
import com.kamal.lc.payroll.repository.PayrollRunRepository;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.dto.ShiftFilter;
import com.kamal.lc.shift.dto.ShiftResponse;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShiftService {

    private final ShiftRepository shiftRepo;
    private final ShiftCalculator calculator;
    private final SettingsResolver settingsResolver;
    private final PayrollRunRepository payrollRunRepo;

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

    /**
     * Marks a shift in or out of the calculation — for supplier-side mistakes that would
     * otherwise deduct a captain's whole day. The raw row is kept either way; only the
     * computed money changes.
     */
    @Transactional
    public ShiftResponse setExcluded(UUID id, boolean excluded, String reason) {
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

        return toResponse(shiftRepo.save(shift));
    }

    private void assertPeriodOpen(LocalDate date) {
        List<PayrollRun> locked = payrollRunRepo.findLockedOverlapping(date, date);
        if (!locked.isEmpty()) {
            PayrollRun run = locked.get(0);
            throw new BusinessException("PERIOD_LOCKED",
                    "لا يمكن التعديل: الفترة " + run.getPeriodFrom() + " إلى " + run.getPeriodTo()
                            + " مقفلة بمسير رواتب معتمد");
        }
    }

    private Specification<Shift> toSpecification(ShiftFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

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
