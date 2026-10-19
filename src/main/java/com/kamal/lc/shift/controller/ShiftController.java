package com.kamal.lc.shift.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.imports.service.ShiftImportService;
import com.kamal.lc.imports.service.SupplierDeductionImportService;
import com.kamal.lc.shift.dto.ShiftFilter;
import com.kamal.lc.shift.dto.ShiftResponse;
import com.kamal.lc.shift.dto.ShiftUpdateRequest;
import com.kamal.lc.shift.service.ShiftExportService;
import com.kamal.lc.shift.service.ShiftService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shifts")
@RequiredArgsConstructor
public class ShiftController {

    private final ShiftService shiftService;
    private final ShiftImportService importService;
    private final ShiftExportService exportService;
    private final SupplierDeductionImportService supplierDeductionImportService;
    private final com.kamal.lc.common.export.PdfExportService pdfExportService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ShiftResponse>>> search(
            @ModelAttribute ShiftFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<ShiftResponse> result = shiftService.search(filter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "shiftDate")));

        return ResponseEntity.ok(ApiResponse.ok(toPageResponse(result)));
    }

    /** The filtered list as an Excel sheet — same criteria as the screen, without paging. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@ModelAttribute ShiftFilter filter) {
        List<ShiftResponse> shifts = shiftService.searchAll(filter,
                Sort.by(Sort.Direction.ASC, "shiftDate"));

        String fileName = URLEncoder.encode(
                "الشيفتات-" + (filter.getFrom() != null ? filter.getFrom() : "الكل") + ".xlsx",
                StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fileName)
                .body(exportService.build(shifts, filter));
    }

    /** The filtered list as a PDF document. */
    @GetMapping("/export-pdf")
    public ResponseEntity<byte[]> exportPdf(@ModelAttribute ShiftFilter filter) {
        List<ShiftResponse> shifts = shiftService.searchAll(filter,
                Sort.by(Sort.Direction.ASC, "shiftDate"));

        String fileName = URLEncoder.encode(
                "تقرير-الشيفتات-" + (filter.getFrom() != null ? filter.getFrom() : "الكل") + ".pdf",
                StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(pdfExportService.buildShiftsPdf(shifts, filter));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShiftResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getById(id)));
    }

    /** Update shift editable fields — re-calculates computed values and logs changes. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ShiftResponse>> update(
            @PathVariable UUID id,
            @RequestBody ShiftUpdateRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "مدير النظام";
        ShiftResponse updated = shiftService.updateShift(id, request, username);
        return ResponseEntity.ok(ApiResponse.ok("تم تعديل بيانات الشيفت بنجاح", updated));
    }

    /** Shifts whose supplier ID has no captain linked — the inbox to work through. */
    @GetMapping("/unmatched")
    public ResponseEntity<ApiResponse<PageResponse<ShiftResponse>>> unmatched(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                toPageResponse(shiftService.getUnmatched(PageRequest.of(page, size)))));
    }

    @GetMapping("/zones")
    public ResponseEntity<ApiResponse<List<String>>> zones() {
        return ResponseEntity.ok(ApiResponse.ok(shiftService.getZones()));
    }

    @PatchMapping("/{id}/exclude")
    public ResponseEntity<ApiResponse<ShiftResponse>> exclude(
            @PathVariable UUID id,
            @RequestBody ExcludeRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "مدير النظام";
        ShiftResponse updated = shiftService.setExcluded(id, request.isExcluded(), request.getReason(), username);
        return ResponseEntity.ok(ApiResponse.ok(
                request.isExcluded() ? "تم استبعاد الشفت" : "تم إرجاع الشفت للحساب", updated));
    }

    /** Exclude a supplier deduction — zeroes the value and requires a reason. */
    @PatchMapping("/{id}/exclude-deduction")
    public ResponseEntity<ApiResponse<ShiftResponse>> excludeDeduction(
            @PathVariable UUID id,
            @RequestBody DeductionExcludeRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "مدير النظام";
        ShiftResponse updated = shiftService.excludeSupplierDeduction(id, request.getReason(), username);
        return ResponseEntity.ok(ApiResponse.ok("تم استبعاد خصم المورد", updated));
    }

    /** Restore a previously excluded supplier deduction to its original value. */
    @PatchMapping("/{id}/restore-deduction")
    public ResponseEntity<ApiResponse<ShiftResponse>> restoreDeduction(
            @PathVariable UUID id,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "مدير النظام";
        ShiftResponse updated = shiftService.restoreSupplierDeduction(id, username);
        return ResponseEntity.ok(ApiResponse.ok("تم إرجاع خصم المورد", updated));
    }

    /** Re-matches and re-prices a period — run after linking a supplier ID or fixing a rate. */
    @PostMapping("/recalculate")
    public ResponseEntity<ApiResponse<ShiftImportService.RecalculationResult>> recalculate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ShiftImportService.RecalculationResult result = importService.recalculate(from, to);
        return ResponseEntity.ok(ApiResponse.ok(
                "تمت إعادة حساب " + result.recalculated() + " شفت (" + result.relinked() + " تم ربطها بكابتن)",
                result));
    }

    /** Import supplier (Ninja) deductions from an Excel file. */
    @PostMapping("/import-supplier-deductions")
    public ResponseEntity<ApiResponse<SupplierDeductionImportService.ImportResult>> importSupplierDeductions(
            @RequestParam("file") MultipartFile file) throws java.io.IOException {
        SupplierDeductionImportService.ImportResult result =
                supplierDeductionImportService.importDeductions(file.getInputStream());
        return ResponseEntity.ok(ApiResponse.ok(
                "تم استيراد " + result.getMatched() + " خصم (" + result.getUnmatched() + " غير مطابق)", result));
    }

    private static PageResponse<ShiftResponse> toPageResponse(Page<ShiftResponse> page) {
        return PageResponse.<ShiftResponse>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Data
    public static class ExcludeRequest {
        private boolean excluded;
        private String reason;
    }

    @Data
    public static class DeductionExcludeRequest {
        private String reason;
    }
}
