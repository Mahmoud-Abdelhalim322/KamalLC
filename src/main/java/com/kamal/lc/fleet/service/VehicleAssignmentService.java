package com.kamal.lc.fleet.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.custody.service.CustodyService;
import com.kamal.lc.fleet.dto.VehicleAssignmentRequest;
import com.kamal.lc.fleet.dto.VehicleAssignmentResponse;
import com.kamal.lc.fleet.entity.Vehicle;
import com.kamal.lc.fleet.entity.VehicleAssignment;
import com.kamal.lc.fleet.repository.VehicleAssignmentRepository;
import com.kamal.lc.fleet.repository.VehicleRepository;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import com.kamal.lc.payroll.repository.CaptainAdjustmentRepository;
import com.kamal.lc.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages the vehicle assignment lifecycle: assigning vehicles to captains,
 * returning them, detecting late returns, and creating automatic deductions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VehicleAssignmentService {

    private final VehicleRepository vehicleRepo;
    private final VehicleAssignmentRepository assignmentRepo;
    private final CaptainRepository captainRepo;
    private final CaptainAdjustmentRepository adjustmentRepo;
    private final CustodyService custodyService;
    private final AppSettingRepository settingRepo;

    public VehicleAssignmentResponse assignVehicle(UUID vehicleId, VehicleAssignmentRequest req) {
        Vehicle vehicle = vehicleRepo.findById(vehicleId)
                .orElseThrow(() -> new BusinessException("VEHICLE_NOT_FOUND", "السيارة غير موجودة"));

        if (!"AVAILABLE".equals(vehicle.getStatus())) {
            throw new BusinessException("NOT_AVAILABLE", "السيارة غير متاحة للتسليم حالياً");
        }
        Captain captain = captainRepo.findById(req.getCaptainId())
                .orElseThrow(() -> new BusinessException("CAPTAIN_NOT_FOUND", "الكابتن غير موجود"));

        Captain nextCaptain = null;
        if (req.getNextCaptainId() != null) {
            nextCaptain = captainRepo.findById(req.getNextCaptainId())
                    .orElseThrow(() -> new BusinessException("NEXT_CAPTAIN_NOT_FOUND", "الكابتن التالي غير موجود"));
        }

        VehicleAssignment assignment = VehicleAssignment.builder()
                .vehicle(vehicle)
                .captain(captain)
                .nextCaptain(nextCaptain)
                .assignedAt(OffsetDateTime.now())
                .expectedReturnAt(req.getExpectedReturnAt())
                .status("ACTIVE")
                .notes(req.getNotes())
                .build();

        vehicle.setStatus("ASSIGNED");
        vehicleRepo.save(vehicle);
        VehicleAssignment saved = assignmentRepo.save(assignment);

        // Create custody record
        String label = vehicleLabel(vehicle);
        custodyService.recordAssignment(captain, "VEHICLE", saved.getId(), label, currentUser());

        return toAssignmentResponse(saved);
    }

    public VehicleAssignmentResponse returnVehicle(UUID assignmentId, String notes) {
        VehicleAssignment a = assignmentRepo.findById(assignmentId)
                .orElseThrow(() -> new BusinessException("ASSIGNMENT_NOT_FOUND", "لم يتم العثور على سجل التسليم"));

        if (!"ACTIVE".equals(a.getStatus()) && !"LATE".equals(a.getStatus())) {
            throw new BusinessException("ALREADY_RETURNED", "السيارة مُرجعة بالفعل");
        }

        OffsetDateTime now = OffsetDateTime.now();
        a.setActualReturnAt(now);
        a.setNotes(notes);

        boolean isLate = now.isAfter(a.getExpectedReturnAt());
        if (isLate) {
            a.setStatus("LATE");
            CaptainAdjustment deduction = createLateDeduction(a.getCaptain());
            a.setLateDeduction(deduction);
        } else {
            a.setStatus("RETURNED");
        }

        Vehicle vehicle = a.getVehicle();
        vehicle.setStatus("AVAILABLE");
        vehicleRepo.save(vehicle);

        custodyService.recordReturn(a.getId(), currentUser());

        assignmentRepo.save(a);
        return toAssignmentResponse(a);
    }

    @Transactional(readOnly = true)
    public Page<VehicleAssignmentResponse> listAssignments(String status, UUID captainId, Pageable pageable) {
        Page<VehicleAssignment> page;
        if (captainId != null) {
            page = assignmentRepo.findByCaptainId(captainId, pageable);
        } else if (status != null && !status.isBlank()) {
            page = assignmentRepo.findByStatus(status, pageable);
        } else {
            page = assignmentRepo.findAll(pageable);
        }
        return page.map(this::toAssignmentResponse);
    }

    @Transactional(readOnly = true)
    public List<VehicleAssignmentResponse> getVehicleHistory(UUID vehicleId) {
        return assignmentRepo.findByVehicleIdOrderByAssignedAtDesc(vehicleId)
                .stream().map(this::toAssignmentResponse).toList();
    }

    // ──────────────────────────── Scheduled: late detection ────────────────────────────

    /**
     * Runs every 30 minutes. Marks overdue assignments as LATE and creates automatic deductions.
     */
    @Scheduled(fixedRate = 1800000)
    public void checkLateAssignments() {
        List<VehicleAssignment> overdue = assignmentRepo.findOverdueAssignments();
        int markedLate = 0;
        for (VehicleAssignment a : overdue) {
            if (!"LATE".equals(a.getStatus())) {
                a.setStatus("LATE");
                CaptainAdjustment deduction = createLateDeduction(a.getCaptain());
                a.setLateDeduction(deduction);
                assignmentRepo.save(a);
                markedLate++;
                log.warn("⚠️ Late return detected: vehicle {} (captain {})",
                        a.getVehicle().getPlateNumber(), a.getCaptain().getKamalCode());
            }
        }
        if (markedLate > 0) {
            log.info("🕐 Late assignment check: {} overdue found, {} newly marked LATE",
                    overdue.size(), markedLate);
        }
    }

    // ──────────────────────────── Internal helpers ────────────────────────────

    CaptainAdjustment createLateDeduction(Captain captain) {
        BigDecimal amount = settingRepo.findCurrent("VEHICLE_LATE_RETURN_DEDUCTION")
                .map(s -> new BigDecimal(s.getValue()))
                .orElse(new BigDecimal("100"));

        LocalDate today = LocalDate.now();
        CaptainAdjustment adj = CaptainAdjustment.builder()
                .captain(captain)
                .periodFrom(today.withDayOfMonth(1))
                .periodTo(today.withDayOfMonth(today.lengthOfMonth()))
                .kind(CaptainAdjustment.DEDUCTION)
                .adjustmentType("OTHER_DEDUCTION")
                .labelAr("خصم تأخير إرجاع سيارة")
                .amount(amount)
                .reason("خصم تلقائي — تأخير عن موعد إرجاع السيارة")
                .createdBy("SYSTEM")
                .build();
        return adjustmentRepo.save(adj);
    }

    String vehicleLabel(Vehicle v) {
        StringBuilder sb = new StringBuilder();
        if (v.getBrand() != null) sb.append(v.getBrand()).append(" ");
        if (v.getModel() != null) sb.append(v.getModel()).append(" ");
        sb.append(v.getPlateNumber());
        return sb.toString().trim();
    }

    VehicleAssignmentResponse toAssignmentResponse(VehicleAssignment a) {
        var builder = VehicleAssignmentResponse.builder()
                .id(a.getId())
                .vehicleId(a.getVehicle().getId())
                .vehiclePlate(a.getVehicle().getPlateNumber())
                .vehicleBrand(a.getVehicle().getBrand())
                .vehicleModel(a.getVehicle().getModel())
                .captainId(a.getCaptain().getId())
                .captainName(a.getCaptain().getFullNameAr())
                .captainCode(a.getCaptain().getKamalCode())
                .assignedAt(a.getAssignedAt().toString())
                .expectedReturnAt(a.getExpectedReturnAt().toString())
                .actualReturnAt(a.getActualReturnAt() != null ? a.getActualReturnAt().toString() : null)
                .status(a.getStatus())
                .late("LATE".equals(a.getStatus()))
                .lateDeductionId(a.getLateDeduction() != null ? a.getLateDeduction().getId() : null)
                .notes(a.getNotes());

        if (a.getNextCaptain() != null) {
            builder.nextCaptainId(a.getNextCaptain().getId());
            builder.nextCaptainName(a.getNextCaptain().getFullNameAr());
            builder.nextCaptainCode(a.getNextCaptain().getKamalCode());
        }

        return builder.build();
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
}
