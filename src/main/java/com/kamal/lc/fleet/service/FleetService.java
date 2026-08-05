package com.kamal.lc.fleet.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.custody.service.CustodyService;
import com.kamal.lc.fleet.dto.*;
import com.kamal.lc.fleet.entity.MaintenancePart;
import com.kamal.lc.fleet.entity.Vehicle;
import com.kamal.lc.fleet.entity.VehicleAssignment;
import com.kamal.lc.fleet.entity.VehicleMaintenance;
import com.kamal.lc.fleet.repository.MaintenanceRepository;
import com.kamal.lc.fleet.repository.VehicleAssignmentRepository;
import com.kamal.lc.fleet.repository.VehicleRepository;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import com.kamal.lc.payroll.repository.CaptainAdjustmentRepository;
import com.kamal.lc.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fleet management service — vehicle CRUD, captain assignment lifecycle,
 * maintenance orders, automatic deductions, and rental-expiry awareness.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FleetService {

    private final VehicleRepository vehicleRepo;
    private final VehicleAssignmentRepository assignmentRepo;
    private final MaintenanceRepository maintenanceRepo;
    private final CaptainRepository captainRepo;
    private final CaptainAdjustmentRepository adjustmentRepo;
    private final CustodyService custodyService;
    private final AppSettingRepository settingRepo;

    // ──────────────────────────── Vehicle CRUD ────────────────────────────

    public VehicleResponse createVehicle(VehicleRequest req) {
        if (vehicleRepo.existsByPlateNumber(req.getPlateNumber())) {
            throw new BusinessException("DUPLICATE_PLATE", "رقم اللوحة مسجل بالفعل");
        }
        Vehicle v = Vehicle.builder()
                .plateNumber(req.getPlateNumber())
                .ownershipType(req.getOwnershipType() != null ? req.getOwnershipType() : "OWNED")
                .brand(req.getBrand())
                .model(req.getModel())
                .year(req.getYear())
                .color(req.getColor())
                .odometer(req.getOdometer())
                .inspectionDate(req.getInspectionDate())
                .status(req.getStatus() != null ? req.getStatus() : "AVAILABLE")
                .rentalAgentName(req.getRentalAgentName())
                .rentalStartDate(req.getRentalStartDate())
                .rentalEndDate(req.getRentalEndDate())
                .registrationNumber(req.getRegistrationNumber())
                .vin(req.getVin())
                .registrationExpiryDate(req.getRegistrationExpiryDate())
                .operatingCardNumber(req.getOperatingCardNumber())
                .insuranceCompany(req.getInsuranceCompany())
                .inspectionExpiryDate(req.getInspectionExpiryDate())
                .notes(req.getNotes())
                .build();
        return toResponse(vehicleRepo.save(v));
    }

    public VehicleResponse updateVehicle(UUID id, VehicleRequest req) {
        Vehicle v = findVehicleOrThrow(id);
        if (!v.getPlateNumber().equals(req.getPlateNumber()) && vehicleRepo.existsByPlateNumber(req.getPlateNumber())) {
            throw new BusinessException("DUPLICATE_PLATE", "رقم اللوحة مسجل بالفعل");
        }
        v.setPlateNumber(req.getPlateNumber());
        v.setOwnershipType(req.getOwnershipType() != null ? req.getOwnershipType() : v.getOwnershipType());
        v.setBrand(req.getBrand());
        v.setModel(req.getModel());
        v.setYear(req.getYear());
        v.setColor(req.getColor());
        v.setOdometer(req.getOdometer());
        v.setInspectionDate(req.getInspectionDate());
        if (req.getStatus() != null) v.setStatus(req.getStatus());
        v.setRentalAgentName(req.getRentalAgentName());
        v.setRentalStartDate(req.getRentalStartDate());
        v.setRentalEndDate(req.getRentalEndDate());
        v.setRegistrationNumber(req.getRegistrationNumber());
        v.setVin(req.getVin());
        v.setRegistrationExpiryDate(req.getRegistrationExpiryDate());
        v.setOperatingCardNumber(req.getOperatingCardNumber());
        v.setInsuranceCompany(req.getInsuranceCompany());
        v.setInspectionExpiryDate(req.getInspectionExpiryDate());
        v.setNotes(req.getNotes());
        return toResponse(vehicleRepo.save(v));
    }

    public void deleteVehicle(UUID id) {
        Vehicle v = findVehicleOrThrow(id);
        if (assignmentRepo.existsByVehicleId(id)) {
            throw new BusinessException("HAS_ASSIGNMENTS", "لا يمكن حذف سيارة لها سجل تسليمات");
        }
        vehicleRepo.delete(v);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(UUID id) {
        return toResponse(findVehicleOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<VehicleResponse> listVehicles(String search, String status, String ownership,
                                               String agent, Pageable pageable) {
        Page<Vehicle> page;
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasOwnership = ownership != null && !ownership.isBlank();
        boolean hasAgent = agent != null && !agent.isBlank();

        if (hasSearch && hasStatus) {
            page = vehicleRepo.searchByStatus(search, status, pageable);
        } else if (hasSearch) {
            page = vehicleRepo.search(search, pageable);
        } else if (hasAgent && hasStatus) {
            page = vehicleRepo.findByRentalAgentAndStatus(agent, status, pageable);
        } else if (hasAgent) {
            page = vehicleRepo.findByRentalAgent(agent, pageable);
        } else if (hasOwnership && hasStatus) {
            page = vehicleRepo.findByOwnershipTypeAndStatus(ownership, status, pageable);
        } else if (hasStatus) {
            page = vehicleRepo.findByStatus(status, pageable);
        } else if (hasOwnership) {
            page = vehicleRepo.findByOwnershipType(ownership, pageable);
        } else {
            page = vehicleRepo.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<String> getRentalAgents() {
        return vehicleRepo.findDistinctRentalAgents();
    }

    // ──────────────────────────── Assignment lifecycle ────────────────────────────

    public VehicleAssignmentResponse assignVehicle(UUID vehicleId, VehicleAssignmentRequest req) {
        Vehicle vehicle = findVehicleOrThrow(vehicleId);
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

        // Determine if late and create automatic deduction
        boolean isLate = now.isAfter(a.getExpectedReturnAt());
        if (isLate) {
            a.setStatus("LATE");
            CaptainAdjustment deduction = createLateDeduction(a.getCaptain());
            a.setLateDeduction(deduction);
        } else {
            a.setStatus("RETURNED");
        }

        // Release the vehicle
        Vehicle vehicle = a.getVehicle();
        vehicle.setStatus("AVAILABLE");
        vehicleRepo.save(vehicle);

        // Close custody record
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
        for (VehicleAssignment a : overdue) {
            if (!"LATE".equals(a.getStatus())) {
                a.setStatus("LATE");
                CaptainAdjustment deduction = createLateDeduction(a.getCaptain());
                a.setLateDeduction(deduction);
                assignmentRepo.save(a);
            }
        }
    }

    // ──────────────────────────── Dashboard stats ────────────────────────────

    @Transactional(readOnly = true)
    public FleetStats getFleetStats() {
        return new FleetStats(
                vehicleRepo.count(),
                vehicleRepo.countByStatus("AVAILABLE"),
                vehicleRepo.countByStatus("ASSIGNED"),
                vehicleRepo.countByStatus("MAINTENANCE"),
                vehicleRepo.countByOwnershipType("RENTED"),
                assignmentRepo.countByStatus("ACTIVE"),
                assignmentRepo.countLateReturns(),
                maintenanceRepo.countByStatus("IN_PROGRESS")
        );
    }

    public record FleetStats(
            long totalVehicles, long available, long assigned, long maintenance,
            long rented, long activeAssignments, long lateReturns,
            long maintenanceOrders) {}

    // ──────────────────────────── Maintenance ────────────────────────────

    /**
     * Opens a maintenance order. If the vehicle is ASSIGNED, automatically returns
     * the active assignment first (so custody is updated correctly).
     */
    public MaintenanceResponse openMaintenance(MaintenanceOpenRequest req) {
        Vehicle vehicle = findVehicleOrThrow(req.getVehicleId());

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
     * Closes a maintenance order: records cost, optionally updates odometer,
     * creates a deduction if the captain bears the cost, and releases the vehicle.
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

        // Update parts if provided on close
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

        // (Auto-deduction removed based on user request - deductions will be added manually via payroll)

        // Update vehicle odometer and release
        Vehicle vehicle = m.getVehicle();
        if (req.getOdometerOnClose() != null) {
            vehicle.setOdometer(req.getOdometerOnClose());
        }
        vehicle.setStatus("AVAILABLE");
        vehicleRepo.save(vehicle);

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

        // Release vehicle
        Vehicle vehicle = m.getVehicle();
        vehicle.setStatus("AVAILABLE");
        vehicleRepo.save(vehicle);
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

    // ──────────────────────────── Internal helpers ────────────────────────────

    private CaptainAdjustment createLateDeduction(Captain captain) {
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

    private Vehicle findVehicleOrThrow(UUID id) {
        return vehicleRepo.findById(id)
                .orElseThrow(() -> new BusinessException("VEHICLE_NOT_FOUND", "السيارة غير موجودة"));
    }

    private String vehicleLabel(Vehicle v) {
        StringBuilder sb = new StringBuilder();
        if (v.getBrand() != null) sb.append(v.getBrand()).append(" ");
        if (v.getModel() != null) sb.append(v.getModel()).append(" ");
        sb.append(v.getPlateNumber());
        return sb.toString().trim();
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    // ──────────────────────────── Mappers ────────────────────────────

    private VehicleResponse toResponse(Vehicle v) {
        var activeAssignment = assignmentRepo.findByVehicleIdAndStatus(v.getId(), "ACTIVE");
        var builder = VehicleResponse.builder()
                .id(v.getId())
                .plateNumber(v.getPlateNumber())
                .ownershipType(v.getOwnershipType())
                .brand(v.getBrand())
                .model(v.getModel())
                .year(v.getYear())
                .color(v.getColor())
                .odometer(v.getOdometer())
                .inspectionDate(v.getInspectionDate() != null ? v.getInspectionDate().toString() : null)
                .status(v.getStatus())
                .rentalAgentName(v.getRentalAgentName())
                .rentalStartDate(v.getRentalStartDate() != null ? v.getRentalStartDate().toString() : null)
                .rentalEndDate(v.getRentalEndDate() != null ? v.getRentalEndDate().toString() : null)
                .registrationNumber(v.getRegistrationNumber())
                .vin(v.getVin())
                .registrationExpiryDate(v.getRegistrationExpiryDate() != null ? v.getRegistrationExpiryDate().toString() : null)
                .operatingCardNumber(v.getOperatingCardNumber())
                .insuranceCompany(v.getInsuranceCompany())
                .inspectionExpiryDate(v.getInspectionExpiryDate() != null ? v.getInspectionExpiryDate().toString() : null)
                .notes(v.getNotes());

        evaluateDocumentAlerts(v, builder);

        activeAssignment.ifPresent(a -> {
            builder.currentCaptainName(a.getCaptain().getFullNameAr());
            builder.currentCaptainCode(a.getCaptain().getKamalCode());
            builder.currentCaptainId(a.getCaptain().getId());
            builder.assignedSince(a.getAssignedAt().toString());
        });

        return builder.build();
    }

    private void evaluateDocumentAlerts(Vehicle v, VehicleResponse.VehicleResponseBuilder builder) {
        LocalDate today = LocalDate.now();
        int regAlertDays = getSettingInt("VEHICLE_REGISTRATION_EXPIRY_ALERT_DAYS", 30);
        int inspAlertDays = getSettingInt("VEHICLE_INSPECTION_EXPIRY_ALERT_DAYS", 30);
        int rentAlertDays = getSettingInt("VEHICLE_RENTAL_EXPIRY_ALERT_DAYS", 7);

        List<String> alerts = new ArrayList<>();
        boolean hasExpired = false;
        boolean hasExpiring = false;

        // 1. Registration expiry
        if (v.getRegistrationExpiryDate() != null) {
            if (v.getRegistrationExpiryDate().isBefore(today)) {
                alerts.add("استمارة السيارة منتهية (" + v.getRegistrationExpiryDate() + ")");
                hasExpired = true;
            } else if (!v.getRegistrationExpiryDate().isAfter(today.plusDays(regAlertDays))) {
                alerts.add("استمارة السيارة تنتهي قريباً (" + v.getRegistrationExpiryDate() + ")");
                hasExpiring = true;
            }
        }

        // 2. Inspection expiry
        LocalDate inspDate = v.getInspectionExpiryDate() != null ? v.getInspectionExpiryDate() : v.getInspectionDate();
        if (inspDate != null) {
            if (inspDate.isBefore(today)) {
                alerts.add("فحص السيارة منتهي (" + inspDate + ")");
                hasExpired = true;
            } else if (!inspDate.isAfter(today.plusDays(inspAlertDays))) {
                alerts.add("فحص السيارة ينتهي قريباً (" + inspDate + ")");
                hasExpiring = true;
            }
        }

        // 3. Rental expiry
        if ("RENTED".equalsIgnoreCase(v.getOwnershipType()) && v.getRentalEndDate() != null) {
            if (v.getRentalEndDate().isBefore(today)) {
                alerts.add("عقد التأجير منتهي (" + v.getRentalEndDate() + ")");
                hasExpired = true;
            } else if (!v.getRentalEndDate().isAfter(today.plusDays(rentAlertDays))) {
                alerts.add("عقد التأجير ينتهي قريباً (" + v.getRentalEndDate() + ")");
                hasExpiring = true;
            }
        }

        String status = hasExpired ? "EXPIRED" : (hasExpiring ? "EXPIRING" : "VALID");
        builder.documentAlertStatus(status);
        builder.documentAlerts(alerts);
    }

    private int getSettingInt(String key, int defaultValue) {
        return settingRepo.findCurrent(key)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getValue().trim());
                    } catch (Exception e) {
                        return defaultValue;
                    }
                }).orElse(defaultValue);
    }

    private VehicleAssignmentResponse toAssignmentResponse(VehicleAssignment a) {
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

    private MaintenanceResponse toMaintenanceResponse(VehicleMaintenance m) {
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


}
