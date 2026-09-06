package com.kamal.lc.fleet.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.fleet.dto.*;
import com.kamal.lc.fleet.service.FleetExportService;
import com.kamal.lc.fleet.service.FleetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/fleet")
@RequiredArgsConstructor
public class FleetController {

    private final FleetService service;
    private final FleetExportService exportService;

    // ──── Vehicles ────

    @GetMapping("/vehicles")
    public ResponseEntity<ApiResponse<PageResponse<VehicleResponse>>> vehicles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ownership,
            @RequestParam(required = false) String agent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<VehicleResponse> result = service.listVehicles(search, status, ownership, agent,
                PageRequest.of(page, size, Sort.by("plateNumber")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<VehicleResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    @GetMapping("/vehicles/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> vehicle(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getVehicle(id)));
    }

    @PostMapping("/vehicles")
    public ResponseEntity<ApiResponse<VehicleResponse>> createVehicle(@Valid @RequestBody VehicleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم إضافة السيارة بنجاح", service.createVehicle(req)));
    }

    @PutMapping("/vehicles/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateVehicle(@PathVariable UUID id,
                                                                       @Valid @RequestBody VehicleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث بيانات السيارة", service.updateVehicle(id, req)));
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVehicle(@PathVariable UUID id) {
        service.deleteVehicle(id);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف السيارة"));
    }

    // ──── Assignments ────

    @PostMapping("/vehicles/{vehicleId}/assign")
    public ResponseEntity<ApiResponse<VehicleAssignmentResponse>> assign(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleAssignmentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم تسليم السيارة للكابتن بنجاح",
                service.assignVehicle(vehicleId, req)));
    }

    @PostMapping("/assignments/{assignmentId}/return")
    public ResponseEntity<ApiResponse<VehicleAssignmentResponse>> returnVehicle(
            @PathVariable UUID assignmentId,
            @RequestBody(required = false) Map<String, String> body) {
        String notes = body != null ? body.get("notes") : null;
        return ResponseEntity.ok(ApiResponse.ok("تم استلام السيارة",
                service.returnVehicle(assignmentId, notes)));
    }

    @GetMapping("/assignments")
    public ResponseEntity<ApiResponse<PageResponse<VehicleAssignmentResponse>>> assignments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID captainId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<VehicleAssignmentResponse> result = service.listAssignments(status, captainId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assignedAt")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<VehicleAssignmentResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    @GetMapping("/vehicles/{vehicleId}/history")
    public ResponseEntity<ApiResponse<List<VehicleAssignmentResponse>>> vehicleHistory(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getVehicleHistory(vehicleId)));
    }

    // ──── Maintenance ────

    @PostMapping("/maintenance")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> openMaintenance(
            @Valid @RequestBody MaintenanceOpenRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم إدخال السيارة للصيانة بنجاح",
                service.openMaintenance(req)));
    }

    @PutMapping("/maintenance/{id}")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> updateMaintenance(
            @PathVariable UUID id,
            @Valid @RequestBody MaintenanceOpenRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم التعديل بنجاح",
                service.updateMaintenance(id, req)));
    }

    @PostMapping("/maintenance/{id}/close")
    public ResponseEntity<ApiResponse<MaintenanceResponse>> closeMaintenance(
            @PathVariable UUID id,
            @Valid @RequestBody MaintenanceCloseRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم تقفيل أمر الصيانة بنجاح",
                service.closeMaintenance(id, req)));
    }

    @PostMapping("/maintenance/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelMaintenance(@PathVariable UUID id) {
        service.cancelMaintenance(id);
        return ResponseEntity.ok(ApiResponse.ok("تم إلغاء أمر الصيانة"));
    }

    @GetMapping("/maintenance")
    public ResponseEntity<ApiResponse<PageResponse<MaintenanceResponse>>> listMaintenance(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<MaintenanceResponse> result = service.listMaintenance(status, vehicleId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<MaintenanceResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    @GetMapping("/vehicles/{vehicleId}/maintenance")
    public ResponseEntity<ApiResponse<List<MaintenanceResponse>>> vehicleMaintenanceHistory(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getMaintenanceHistory(vehicleId)));
    }

    @GetMapping("/rental-agents")
    public ResponseEntity<ApiResponse<List<String>>> getRentalAgents() {
        return ResponseEntity.ok(ApiResponse.ok(service.getRentalAgents()));
    }

    // ──── Stats ────

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<FleetService.FleetStats>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getFleetStats()));
    }

    // ──── Export ────

    @GetMapping("/vehicles/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String ownership) {
        byte[] bytes = exportService.exportVehiclesExcel(ownership);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fleet-export.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
