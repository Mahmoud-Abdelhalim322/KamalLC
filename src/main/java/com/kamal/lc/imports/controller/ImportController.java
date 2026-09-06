package com.kamal.lc.imports.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.imports.dto.ImportBatchResponse;
import com.kamal.lc.imports.dto.ImportPreviewResponse;
import com.kamal.lc.imports.dto.ShiftImportPreviewResponse;
import com.kamal.lc.imports.entity.ImportBatchType;
import com.kamal.lc.imports.service.ExcelTemplateService;
import com.kamal.lc.imports.service.ImportService;
import com.kamal.lc.imports.service.ShiftImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

import com.kamal.lc.imports.dto.DeductionImportPreviewResponse;
import com.kamal.lc.imports.service.SupplierDeductionImportService;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final ShiftImportService shiftImportService;
    private final SupplierDeductionImportService supplierDeductionImportService;
    private final com.kamal.lc.imports.service.VacationCommissionImportService vacationCommissionImportService;
    private final ExcelTemplateService templateService;

    /** Blank sheet with the expected columns, so files are shaped right before upload. */
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return xlsx(templateService.buildCaptains(), "captains-import-template.xlsx");
    }

    /** The supplier shift report's own layout, for when the export has to be rebuilt by hand. */
    @GetMapping("/shifts/template")
    public ResponseEntity<byte[]> shiftTemplate() {
        return xlsx(templateService.buildShifts(), "supplier-shifts-import-template.xlsx");
    }

    /** Blank template for supplier deductions import. */
    @GetMapping("/deductions/template")
    public ResponseEntity<byte[]> deductionTemplate() {
        return xlsx(templateService.buildDeductions(), "supplier-deductions-template.xlsx");
    }

    /** Blank template for supplier vacation commission import. */
    @GetMapping("/vacation-commission/template")
    public ResponseEntity<byte[]> vacationCommissionTemplate() {
        return xlsx(templateService.buildVacationCommission(), "vacation-commission-template.xlsx");
    }

    private ResponseEntity<byte[]> xlsx(byte[] workbook, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(workbook);
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<ImportPreviewResponse>> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        ImportPreviewResponse result = importService.preview(file, supplierId);
        return ResponseEntity.ok(ApiResponse.ok("تم تحليل الملف بنجاح", result));
    }

    @PostMapping("/{batchId}/commit")
    public ResponseEntity<ApiResponse<ImportPreviewResponse>> commit(
            @PathVariable UUID batchId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        ImportPreviewResponse result = importService.commit(file, batchId, supplierId);
        return ResponseEntity.ok(ApiResponse.ok("تم الاستيراد بنجاح", result));
    }

    /** Supplier shift file — the daily upload that feeds reports and payroll. */
    @PostMapping("/shifts/preview")
    public ResponseEntity<ApiResponse<ShiftImportPreviewResponse>> previewShifts(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        ShiftImportPreviewResponse result = shiftImportService.preview(file, supplierId);
        return ResponseEntity.ok(ApiResponse.ok("تم تحليل ملف الشيفتات بنجاح", result));
    }

    @PostMapping("/shifts/{batchId}/commit")
    public ResponseEntity<ApiResponse<ShiftImportPreviewResponse>> commitShifts(
            @PathVariable UUID batchId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        ShiftImportPreviewResponse result = shiftImportService.commit(file, batchId, supplierId);
        return ResponseEntity.ok(ApiResponse.ok(
                "تم استيراد " + result.getCommittedRows() + " شفت بنجاح", result));
    }

    /** Supplier deductions file — penalty and deduction records from supplier. */
    @PostMapping("/deductions/preview")
    public ResponseEntity<ApiResponse<DeductionImportPreviewResponse>> previewDeductions(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        DeductionImportPreviewResponse result = supplierDeductionImportService.previewDeductions(file);
        return ResponseEntity.ok(ApiResponse.ok("تم تحليل ملف خصومات المورد بنجاح", result));
    }

    @PostMapping("/deductions/{batchId}/commit")
    public ResponseEntity<ApiResponse<DeductionImportPreviewResponse>> commitDeductions(
            @PathVariable UUID batchId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        DeductionImportPreviewResponse result = supplierDeductionImportService.commitDeductions(batchId, file);
        return ResponseEntity.ok(ApiResponse.ok(
                "تم تطبيق خصومات المورد على " + result.getMatchedRows() + " شيفت بنجاح", result));
    }

    /** Supplier vacation commission file — holiday bonus records from supplier. */
    @PostMapping("/vacation-commission/preview")
    public ResponseEntity<ApiResponse<com.kamal.lc.imports.dto.VacationCommissionImportPreviewResponse>> previewVacationCommission(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        com.kamal.lc.imports.dto.VacationCommissionImportPreviewResponse result = vacationCommissionImportService.previewVacationCommission(file, supplierId);
        return ResponseEntity.ok(ApiResponse.ok("تم تحليل ملف عمولة أوقات الإجازة بنجاح", result));
    }

    @PostMapping("/vacation-commission/{batchId}/commit")
    public ResponseEntity<ApiResponse<com.kamal.lc.imports.dto.VacationCommissionImportPreviewResponse>> commitVacationCommission(
            @PathVariable UUID batchId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "supplierId", required = false) UUID supplierId) {
        com.kamal.lc.imports.dto.VacationCommissionImportPreviewResponse result = vacationCommissionImportService.commitVacationCommission(batchId, file, supplierId);
        return ResponseEntity.ok(ApiResponse.ok(
                "تم استيراد عمولة أوقات الإجازة لـ " + result.getMatchedRows() + " كابتن بنجاح", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ImportBatchResponse>>> history(
            @RequestParam(required = false) ImportBatchType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Ordering comes from the derived query (findAllByOrderByUploadedAtDesc);
        // adding a Sort here would produce a query with two order-by clauses.
        Page<ImportBatchResponse> result = importService.getHistory(type, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<ImportBatchResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    @GetMapping("/{batchId}")
    public ResponseEntity<ApiResponse<ImportBatchResponse>> getBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(ApiResponse.ok(importService.getBatch(batchId)));
    }
}
