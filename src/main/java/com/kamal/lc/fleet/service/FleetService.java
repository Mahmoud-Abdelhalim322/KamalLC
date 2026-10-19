package com.kamal.lc.fleet.service;

import com.kamal.lc.changelog.service.ChangeLogWriter;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.fleet.dto.*;
import com.kamal.lc.fleet.entity.Vehicle;
import com.kamal.lc.fleet.repository.MaintenanceRepository;
import com.kamal.lc.fleet.repository.VehicleAssignmentRepository;
import com.kamal.lc.fleet.repository.VehicleRepository;
import com.kamal.lc.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fleet management facade — delegates to focused sub-services while keeping
 * the public API surface unchanged for the controller.
 *
 * <p>Responsibilities kept here:
 * <ul>
 *   <li>Vehicle CRUD (create, update, delete, list, get)</li>
 *   <li>Vehicle code generation</li>
 *   <li>Fleet-wide statistics</li>
 * </ul>
 *
 * <p>Delegated responsibilities:
 * <ul>
 *   <li>Assignment lifecycle → {@link VehicleAssignmentService}</li>
 *   <li>Maintenance orders → {@link VehicleMaintenanceService}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FleetService {

    private final VehicleRepository vehicleRepo;
    private final VehicleAssignmentRepository assignmentRepo;
    private final MaintenanceRepository maintenanceRepo;
    private final AppSettingRepository settingRepo;

    // Sub-services
    private final VehicleAssignmentService assignmentService;
    private final VehicleMaintenanceService maintenanceService;
    private final ChangeLogWriter changeLogWriter;

    /** Arabic labels for vehicle fields — used by the changelog writer. */
    private static final Map<String, String> VEHICLE_FIELD_LABELS = Map.ofEntries(
            Map.entry("plateNumber", "رقم اللوحة"),
            Map.entry("ownershipType", "نوع الملكية"),
            Map.entry("rentalAgentCode", "كود وكيل التأجير"),
            Map.entry("brand", "الماركة"),
            Map.entry("model", "الموديل"),
            Map.entry("year", "سنة الصنع"),
            Map.entry("color", "اللون"),
            Map.entry("odometer", "عداد المسافة"),
            Map.entry("status", "الحالة"),
            Map.entry("rentalAgentName", "اسم وكيل التأجير"),
            Map.entry("rentalStartDate", "بداية عقد التأجير"),
            Map.entry("rentalEndDate", "نهاية عقد التأجير"),
            Map.entry("registrationNumber", "رقم الاستمارة"),
            Map.entry("vin", "رقم الهيكل"),
            Map.entry("registrationExpiryDate", "تاريخ انتهاء الاستمارة"),
            Map.entry("operatingCardNumber", "رقم بطاقة التشغيل"),
            Map.entry("insuranceCompany", "شركة التأمين"),
            Map.entry("inspectionExpiryDate", "تاريخ انتهاء الفحص"),
            Map.entry("notes", "ملاحظات")
    );

    // ──────────────────────────── Vehicle CRUD ────────────────────────────

    public VehicleResponse createVehicle(VehicleRequest req) {
        if (vehicleRepo.existsByPlateNumber(req.getPlateNumber())) {
            throw new BusinessException("DUPLICATE_PLATE", "رقم اللوحة مسجل بالفعل");
        }
        String ownership = req.getOwnershipType() != null ? req.getOwnershipType() : "OWNED";
        String agentCode = req.getRentalAgentCode();
        String vehicleCode = generateVehicleCode(ownership, agentCode);

        Vehicle v = Vehicle.builder()
                .vehicleCode(vehicleCode)
                .plateNumber(req.getPlateNumber())
                .ownershipType(ownership)
                .rentalAgentCode(agentCode)
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

        // Snapshot before update
        Map<String, Object> before = ChangeLogWriter.snapshot(v, VEHICLE_FIELD_LABELS);

        // Regenerate vehicle code if ownership type or agent code changed
        String newOwnership = req.getOwnershipType() != null ? req.getOwnershipType() : v.getOwnershipType();
        String newAgentCode = req.getRentalAgentCode();
        boolean ownershipChanged = !newOwnership.equals(v.getOwnershipType());
        boolean agentCodeChanged = "RENTED".equals(newOwnership)
                && !java.util.Objects.equals(newAgentCode, v.getRentalAgentCode());

        if (ownershipChanged || agentCodeChanged) {
            v.setVehicleCode(generateVehicleCode(newOwnership, newAgentCode));
        }

        v.setPlateNumber(req.getPlateNumber());
        v.setOwnershipType(newOwnership);
        v.setRentalAgentCode(newAgentCode);
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

        Vehicle saved = vehicleRepo.save(v);

        // Log changes
        Map<String, Object> after = ChangeLogWriter.snapshot(saved, VEHICLE_FIELD_LABELS);
        String label = saved.getPlateNumber() + (saved.getVehicleCode() != null ? " (" + saved.getVehicleCode() + ")" : "");
        changeLogWriter.logFieldChanges("VEHICLE", saved.getId(), label,
                before, after, VEHICLE_FIELD_LABELS, "نظام");

        return toResponse(saved);
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

    // ──────────────────────────── Delegation: Assignments ────────────────────────────

    public VehicleAssignmentResponse assignVehicle(UUID vehicleId, VehicleAssignmentRequest req) {
        return assignmentService.assignVehicle(vehicleId, req);
    }

    public VehicleAssignmentResponse returnVehicle(UUID assignmentId, String notes) {
        return assignmentService.returnVehicle(assignmentId, notes);
    }

    @Transactional(readOnly = true)
    public Page<VehicleAssignmentResponse> listAssignments(String status, UUID captainId, Pageable pageable) {
        return assignmentService.listAssignments(status, captainId, pageable);
    }

    @Transactional(readOnly = true)
    public List<VehicleAssignmentResponse> getVehicleHistory(UUID vehicleId) {
        return assignmentService.getVehicleHistory(vehicleId);
    }

    // ──────────────────────────── Delegation: Maintenance ────────────────────────────

    public MaintenanceResponse openMaintenance(MaintenanceOpenRequest req) {
        return maintenanceService.openMaintenance(req);
    }

    public MaintenanceResponse updateMaintenance(UUID maintenanceId, MaintenanceOpenRequest req) {
        return maintenanceService.updateMaintenance(maintenanceId, req);
    }

    public MaintenanceResponse closeMaintenance(UUID maintenanceId, MaintenanceCloseRequest req) {
        return maintenanceService.closeMaintenance(maintenanceId, req);
    }

    public void cancelMaintenance(UUID maintenanceId) {
        maintenanceService.cancelMaintenance(maintenanceId);
    }

    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> listMaintenance(String status, UUID vehicleId, Pageable pageable) {
        return maintenanceService.listMaintenance(status, vehicleId, pageable);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceResponse> getMaintenanceHistory(UUID vehicleId) {
        return maintenanceService.getMaintenanceHistory(vehicleId);
    }

    // ──────────────────────────── Dashboard stats ────────────────────────────

    @Transactional(readOnly = true)
    public FleetStats getFleetStats() {
        return new FleetStats(
                vehicleRepo.count(),
                vehicleRepo.countByStatus("AVAILABLE"),
                vehicleRepo.countByStatus("ASSIGNED"),
                vehicleRepo.countByStatus("MAINTENANCE"),
                vehicleRepo.countByOwnershipType("OWNED"),
                vehicleRepo.countByOwnershipType("RENTED"),
                vehicleRepo.countByOwnershipType("LEASE_TO_OWN"),
                assignmentRepo.countByStatus("ACTIVE"),
                assignmentRepo.countLateReturns(),
                maintenanceRepo.countByStatus("IN_PROGRESS")
        );
    }

    public record FleetStats(
            long totalVehicles, long available, long assigned, long maintenance,
            long owned, long rented, long leaseToOwn,
            long activeAssignments, long lateReturns,
            long maintenanceOrders) {}

    // ──────────────────────────── Vehicle Code Generation ────────────────────────────

    /**
     * Generates a sequential vehicle code based on ownership type.
     * <ul>
     *   <li>OWNED → KSV0001, KSV0002, ...</li>
     *   <li>RENTED → {agentCode}0001, e.g. TOJI0001</li>
     *   <li>LEASE_TO_OWN → KSEP0001, KSEP0002, ...</li>
     * </ul>
     */
    private String generateVehicleCode(String ownershipType, String agentCode) {
        String prefix = switch (ownershipType) {
            case "RENTED" -> (agentCode != null && !agentCode.isBlank())
                    ? agentCode.trim().toUpperCase() : "RENT";
            case "LEASE_TO_OWN" -> "KSEP";
            default -> "KSV";
        };

        int nextNum = vehicleRepo.findMaxCodeByPrefix(prefix)
                .map(maxCode -> {
                    String numPart = maxCode.substring(prefix.length());
                    try {
                        return Integer.parseInt(numPart) + 1;
                    } catch (NumberFormatException e) {
                        return 1;
                    }
                })
                .orElse(1);

        return prefix + String.format("%04d", nextNum);
    }

    /**
     * Backfills vehicle codes for existing vehicles that don't have one.
     */
    public void migrateExistingVehicleCodes() {
        vehicleRepo.findAll().stream()
                .filter(v -> v.getVehicleCode() == null || v.getVehicleCode().isBlank())
                .forEach(v -> {
                    v.setVehicleCode(generateVehicleCode(v.getOwnershipType(), v.getRentalAgentCode()));
                    vehicleRepo.save(v);
                });
    }

    // ──────────────────────────── Internal helpers ────────────────────────────

    private Vehicle findVehicleOrThrow(UUID id) {
        return vehicleRepo.findById(id)
                .orElseThrow(() -> new BusinessException("VEHICLE_NOT_FOUND", "السيارة غير موجودة"));
    }

    // ──────────────────────────── Vehicle Response Mapper ────────────────────────────

    private VehicleResponse toResponse(Vehicle v) {
        var activeAssignment = assignmentRepo.findByVehicleIdAndStatus(v.getId(), "ACTIVE");
        var builder = VehicleResponse.builder()
                .id(v.getId())
                .vehicleCode(v.getVehicleCode())
                .plateNumber(v.getPlateNumber())
                .ownershipType(v.getOwnershipType())
                .rentalAgentCode(v.getRentalAgentCode())
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
}
