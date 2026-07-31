package com.kamal.lc.staff.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.staff.dto.StaffDetailResponse;
import com.kamal.lc.staff.dto.StaffRequest;
import com.kamal.lc.staff.dto.StaffResponse;
import com.kamal.lc.staff.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StaffResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String alert,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<StaffResponse> result = staffService.list(search, type, alert,
                PageRequest.of(page, size, Sort.by("fullNameAr")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<StaffResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    @GetMapping("/next-code")
    public ResponseEntity<ApiResponse<Map<String, String>>> nextCode(@RequestParam String type) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("staffCode", staffService.previewNextCode(type))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffDetailResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(staffService.getDetail(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StaffDetailResponse>> create(@Valid @RequestBody StaffRequest request) {
        String label = "MANAGER".equalsIgnoreCase(request.getStaffType()) ? "الإداري" : "العامل";
        return ResponseEntity.ok(ApiResponse.ok("تم إضافة " + label + " بنجاح", staffService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffDetailResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody StaffRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم حفظ البيانات بنجاح", staffService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        staffService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف الموظف"));
    }
}
