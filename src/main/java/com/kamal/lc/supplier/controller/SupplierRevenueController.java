package com.kamal.lc.supplier.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.supplier.dto.SupplierRevenueDtos.*;
import com.kamal.lc.supplier.service.SupplierRevenueExportService;
import com.kamal.lc.supplier.service.SupplierRevenueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplier-revenue")
@RequiredArgsConstructor
public class SupplierRevenueController {

    private final SupplierRevenueService service;
    private final SupplierRevenueExportService exportService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<SupplierRevenueSummaryResponse>> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.ok(service.getRevenueSummary(from, to)));
    }

    @GetMapping("/statement/{supplierId}")
    public ResponseEntity<ApiResponse<SupplierStatementResponse>> getStatement(
            @PathVariable UUID supplierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return ResponseEntity.ok(ApiResponse.ok(service.getSupplierStatement(supplierId, from, to)));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<ApiResponse<SupplierAdjustmentResponse>> addAdjustment(
            @Valid @RequestBody SupplierAdjustmentRequest request,
            Authentication authentication) {

        String username = authentication != null ? authentication.getName() : "مدير النظام";
        SupplierAdjustmentResponse response = service.addAdjustment(request, username);
        return ResponseEntity.ok(ApiResponse.ok("تم إضافة التسوية المالية بنجاح", response));
    }

    @DeleteMapping("/adjustments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAdjustment(@PathVariable UUID id) {
        service.deleteAdjustment(id);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف حركة التسوية بنجاح", null));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        SupplierRevenueSummaryResponse summary = service.getRevenueSummary(from, to);
        byte[] bytes = exportService.exportSummary(summary);

        String fileName = URLEncoder.encode("إيرادات-الموردين-" + from + "-إلى-" + to + ".xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(bytes);
    }
}
