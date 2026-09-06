package com.kamal.lc.fleet.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.custody.service.CustodyService;
import com.kamal.lc.fleet.dto.MaintenanceCloseRequest;
import com.kamal.lc.fleet.dto.MaintenanceOpenRequest;
import com.kamal.lc.fleet.dto.MaintenanceResponse;
import com.kamal.lc.fleet.entity.MaintenancePart;
import com.kamal.lc.fleet.entity.Vehicle;
import com.kamal.lc.fleet.entity.VehicleMaintenance;
import com.kamal.lc.fleet.repository.MaintenanceRepository;
import com.kamal.lc.fleet.repository.VehicleAssignmentRepository;
import com.kamal.lc.fleet.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages vehicle maintenance orders: opening, updating, closing, and cancelling.
 * Handles auto-return of assigned vehicles entering maintenance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VehicleMaintenanceService {

    private final VehicleRepository vehicleRepo;
    private final VehicleAssignmentRepository assignmentRepo;
    private final MaintenanceRepository maintenanceRepo;
    private final CaptainRepository captainRepo;
    private final CustodyService custodyService;

    /**
     * Opens a maintenance order. If the vehicle is ASSIGNED, automatically returns
     * the active assignment first (so custody is updated correctly).
     */
    public MaintenanceResponse openMaintenance(MaintenanceOpenRequest req) {
        Vehicle vehicle = vehicleRepo.findById(req.getVehicleId())
                .orElseThrow(() -> new BusinessException("VEHICLE_NOT_FOUND", "السيارة غير موجودة"));

        if ("MAINTENANCE".equals(vehicle.getStatus())) {
            throw new BusinessException("ALREADY_IN_MAINTENANCE", "السيارة في الصيانة بالفعل");
        }
        if ("DECOMMISSIONED".equals(vehicle.getStatus())) {
            throw new BusinessException("DECOMMISSIONED", "السيارة معطّلة");
        }

        // If assigned, auto-return the active assignment
        if ("ASSIGNED".equals(vehicle.getStatus())) {
            assignmentRepo.findByVehicleIdAndStatus(vehicle.getId(), "ACTIVE")
                    .ifPresent(a -> {
                        a.setActualReturnAt(OffsetDateTime.now());
                        a.setStatus("RETURNED");
                        a.setNotes((a.getNotes() != null ? a.getNotes() + " | " : "") + "إرجاع تلقائي — دخول صيانة");
                        assignmentRepo.save(a);
                        custodyService.recordReturn(a.getId(), currentUser());
                    });
        }

        Captain captain = null;
        if (req.getCaptainId() != null) {
            captain = captainRepo.findById(req.getCaptainId())
                    .orElseThrow(() -> new BusinessException("CAPTAIN_NOT_FOUND", "الكابتن غير موجود"));
        }

        VehicleMaintenance m = VehicleMaintenance.builder()
                .vehicle(vehicle)
                .workshopName(req.getWorkshopName())
                .faultReason(req.getFaultReason())
                .costBearer(req.getCostBearer() != null ? req.getCostBearer() : "COMPANY")
                .captain(captain)
                .notes(req.getNotes())
                .createdBy(currentUser())
                .build();

        if (req.getParts() != null) {
            for (int i = 0; i < req.getParts().size(); i++) {
                var p = req.getParts().get(i);
                m.addPart(MaintenancePart.builder()
                        .partName(p.getPartName())
                        .partCost(p.getPartCost())
                        .sortOrder(i)
                        .build());
            }
        }

        vehicle.setStatus("MAINTENANCE");
        vehicleRepo.save(vehicle);
        log.info("🔧 Maintenance opened for vehicle {} at {}", vehicle.getPlateNumber(), req.getWorkshopName());
        return toMaintenanceResponse(maintenanceRepo.save(m));
    }

    public MaintenanceResponse updateMaintenance(UUID maintenanceId, MaintenanceOpenRequest req) {
        VehicleMaintenance m = maintenanceRepo.findById(maintenanceId)
                .orElseThrow(() -> new BusinessException("MAINTENANCE_NOT_FOUND", "أمر الصيانة غير موجود"));

        if (!"IN_PROGRESS".equals(m.getStatus())) {
            throw new BusinessException("NOT_IN_PROGRESS", "لا يمكن تعديل أمر صيانة مقفل");
        }

        m.setFaultReason(req.getFaultReason());
        m.setWorkshopName(req.getWorkshopName());
        m.setCostBearer(req.getCostBearer() != null ? req.getCostBearer() : "COMPANY");
        m.setNotes(req.getNotes());

        if ("CAPTAIN".equals(req.getCostBearer()) && req.getCaptainId() != null) {
            Captain captain = captainRepo.findById(req.getCaptainId())
                    .orElseThrow(() -> new BusinessException("CAPTAIN_NOT_FOUND", "الكابتن غير موجود"));
            m.setCaptain(captain);
        } else if ("COMPANY".equals(req.getCostBearer())) {
            m.setCaptain(null);
        }

        if (req.getParts() != null) {
            List<MaintenancePart> newParts = new ArrayList<>();
            for (int i = 0; i < req.getParts().size(); i++) {
                var p = req.getParts().get(i);
                newParts.add(MaintenancePart.builder()
                        .partName(p.getPartName())
                        .partCost(p.getPartCost())
                        .sortOrder(i)
                        .build());
            }
            m.replaceParts(newParts);
        }

        return toMaintenanceResponse(maintenanceRepo.save(m));
    }

    /**
     * Closes a maintenance order: records cost, optionally updates odometer, and releases the vehicle.
     */
    public MaintenanceResponse closeMaintenance(UUID maintenanceId, MaintenanceCloseRequest req) {
        VehicleMaintenance m = maintenanceRepo.findById(maintenanceId)
                .orElseThrow(() -> new BusinessException("MAINTENANCE_NOT_FOUND", "أمر الصيانة غير موجود"));

        if (!"IN_PROGRESS".equals(m.getStatus())) {
            throw new BusinessException("NOT_IN_PROGRESS", "أمر الصيانة ليس قيد التنفيذ");
        }

        m.setTotalCost(req.getTotalCost());
        m.setOdometerOnClose(req.getOdometerOnClose());
        m.setClosedAt(OffsetDateTime.now());
        m.setStatus("COMPLETED");
        if (req.getNotes() != null) m.setNotes(req.getNotes());

        if (req.getParts() != null && !req.getParts().isEmpty()) {
            List<MaintenancePart> newParts = new ArrayList<>();
            for (int i = 0; i < req.getParts().size(); i++) {
                var p = req.getParts().get(i);
                newParts.add(MaintenancePart.builder()
                        .partName(p.getPartName())
                        .partCost(p.getPartCost())
                        .sortOrder(i)
                        .build());
            }
            m.replaceParts(newParts);
        }

        Vehicle vehicle = m.getVehicle();
        if (req.getOdometerOnClose() != null) {
            vehicle.setOdometer(req.getOdometerOnClose());
        }
        vehicle.setStatus("AVAILABLE");
        vehicleRepo.save(vehicle);

        log.info("✅ Maintenance closed for vehicle {} (cost: {})", vehicle.getPlateNumber(), req.getTotalCost());
        return toMaintenanceResponse(maintenanceRepo.save(m));
    }

    public void cancelMaintenance(UUID maintenanceId) {
        VehicleMaintenance m = maintenanceRepo.findById(maintenanceId)
                .orElseThrow(() -> new BusinessException("MAINTENANCE_NOT_FOUND", "أمر الصيانة غير موجود"));

        if (!"IN_PROGRESS".equals(m.getStatus())) {
            throw new BusinessException("NOT_IN_PROGRESS", "أمر الصيانة ليس قيد التنفيذ");
        }

        m.setStatus("CANCELLED");
        m.setClosedAt(OffsetDateTime.now());
        maintenanceRepo.save(m);

        Vehicle vehicle = m.getVehicle();
        vehicle.setStatus("AVAILABLE");
        vehicleRepo.save(vehicle);
        log.info("❌ Maintenance cancelled for vehicle {}", vehicle.getPlateNumber());
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> listMaintenance(String status, UUID vehicleId, Pageable pageable) {
        Page<VehicleMaintenance> page;
        if (vehicleId != null && status != null && !status.isBlank()) {
            page = maintenanceRepo.findByStatusAndVehicleId(status, vehicleId, pageable);
        } else if (vehicleId != null) {
            page = maintenanceRepo.findByVehicleId(vehicleId, pageable);
        } else if (status != null && !status.isBlank()) {
            page = maintenanceRepo.findByStatus(status, pageable);
        } else {
            page = maintenanceRepo.findAll(pageable);
        }
        return page.map(this::toMaintenanceResponse);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceResponse> getMaintenanceHistory(UUID vehicleId) {
        return maintenanceRepo.findByVehicleIdOrderByOpenedAtDesc(vehicleId)
                .stream().map(this::toMaintenanceResponse).toList();
    }

    // ──────────────────────────── Mapper ────────────────────────────

    MaintenanceResponse toMaintenanceResponse(VehicleMaintenance m) {
        var builder = MaintenanceResponse.builder()
                .id(m.getId())
                .vehicleId(m.getVehicle().getId())
                .vehiclePlate(m.getVehicle().getPlateNumber())
                .vehicleBrand(m.getVehicle().getBrand())
                .vehicleModel(m.getVehicle().getModel())
                .status(m.getStatus())
                .workshopName(m.getWorkshopName())
                .faultReason(m.getFaultReason())
                .costBearer(m.getCostBearer())
                .totalCost(m.getTotalCost())
                .odometerOnClose(m.getOdometerOnClose())
                .notes(m.getNotes())
                .openedAt(m.getOpenedAt().toString())
                .closedAt(m.getClosedAt() != null ? m.getClosedAt().toString() : null)
                .createdBy(m.getCreatedBy());

        if (m.getCaptain() != null) {
            builder.captainId(m.getCaptain().getId());
            builder.captainName(m.getCaptain().getFullNameAr());
            builder.captainCode(m.getCaptain().getKamalCode());
        }

        if (m.getDeduction() != null) {
            builder.deductionId(m.getDeduction().getId());
        }

        if (m.getParts() != null) {
            builder.parts(m.getParts().stream()
                    .map(p -> MaintenanceResponse.PartResponse.builder()
                            .id(p.getId())
                            .partName(p.getPartName())
                            .partCost(p.getPartCost())
                            .sortOrder(p.getSortOrder())
                            .build())
                    .toList());
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
