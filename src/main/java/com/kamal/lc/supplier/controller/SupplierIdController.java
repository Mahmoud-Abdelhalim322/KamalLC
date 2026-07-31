package com.kamal.lc.supplier.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.supplier.dto.AssignmentRequest;
import com.kamal.lc.supplier.dto.SupplierIdSlotRequest;
import com.kamal.lc.supplier.dto.UnassignRequest;
import com.kamal.lc.supplier.dto.SupplierIdSlotResponse;
import com.kamal.lc.supplier.dto.SupplierResponse;
import com.kamal.lc.supplier.service.SupplierIdService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplier-ids")
@RequiredArgsConstructor
public class SupplierIdController {

    private final SupplierIdService service;

    @GetMapping("/suppliers")
    public ResponseEntity<ApiResponse<List<SupplierResponse>>> suppliers() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAllSuppliers()));
    }

    @PostMapping("/suppliers")
    public ResponseEntity<ApiResponse<SupplierResponse>> createSupplier(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String code = body.get("code");
        String color = body.get("color");
        return ResponseEntity.ok(ApiResponse.ok("تم إضافة المورد بنجاح", service.createSupplier(name, code, color)));
    }

    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<PageResponse<SupplierIdSlotResponse>>> slots(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<SupplierIdSlotResponse> result = service.getSlots(supplierId,
                PageRequest.of(page, size, Sort.by("externalId")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<SupplierIdSlotResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    @PostMapping("/slots")
    public ResponseEntity<ApiResponse<SupplierIdSlotResponse>> createSlot(
            @Valid @RequestBody SupplierIdSlotRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم إضافة المعرف بنجاح", service.createSlot(request)));
    }

    /** Picker for linking an existing ID to a captain; {@code onlyAvailable} hides taken IDs. */
    @GetMapping("/slots/lookup")
    public ResponseEntity<ApiResponse<List<SupplierIdSlotResponse>>> lookupSlots(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "true") boolean onlyAvailable) {
        return ResponseEntity.ok(ApiResponse.ok(service.lookupSlots(q, supplierId, onlyAvailable)));
    }

    @PostMapping("/slots/{slotId}/assign")
    public ResponseEntity<ApiResponse<SupplierIdSlotResponse>> assign(
            @PathVariable UUID slotId,
            @Valid @RequestBody AssignmentRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("تم ربط المعرف بالكابتن بنجاح",
                service.assign(slotId, request.getCaptainId(), request.getValidFrom(),
                        request.getSupplierSideName(), request.getReason())));
    }

    @PostMapping("/slots/{slotId}/unassign")
    public ResponseEntity<ApiResponse<SupplierIdSlotResponse>> unassign(
            @PathVariable UUID slotId,
            @RequestBody(required = false) UnassignRequest request) {

        LocalDate validTo = request != null ? request.getValidTo() : null;
        String reason = request != null ? request.getReason() : null;

        return ResponseEntity.ok(ApiResponse.ok("تم فك ربط المعرف",
                service.unassign(slotId, validTo, reason)));
    }

    @GetMapping("/slots/{slotId}/history")
    public ResponseEntity<ApiResponse<List<SupplierIdService.AssignmentHistoryItem>>> slotHistory(
            @PathVariable UUID slotId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getSlotHistory(slotId)));
    }
}
