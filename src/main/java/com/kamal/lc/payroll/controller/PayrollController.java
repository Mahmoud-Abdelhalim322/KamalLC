package com.kamal.lc.payroll.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.payroll.dto.*;
import com.kamal.lc.payroll.service.PayrollExportService;
import com.kamal.lc.payroll.service.PayrollService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollExportService exportService;
    private final com.kamal.lc.common.export.PdfExportService pdfExportService;

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<PageResponse<PayrollRunResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PayrollRunResponse> result = payrollService.list(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<PayrollRunResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> get(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok(payrollService.get(runId)));
    }

    @GetMapping("/runs/{runId}/lines")
    public ResponseEntity<ApiResponse<List<PayrollLineResponse>>> lines(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok(payrollService.getLines(runId)));
    }

    @GetMapping("/runs/{runId}/staff-lines")
    public ResponseEntity<ApiResponse<List<StaffPayrollLineResponse>>> staffLines(
            @PathVariable UUID runId,
            @RequestParam(required = false) String staffType) {
        return ResponseEntity.ok(ApiResponse.ok(payrollService.getStaffLines(runId, staffType)));
    }

    /** Builds the run for a period, or rebuilds it if it is still a draft. */
    @PostMapping("/runs/generate")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> generate(@Valid @RequestBody GenerateRequest request) {
        PayrollRunResponse run = payrollService.generate(
                request.getPeriodFrom(), request.getPeriodTo(), request.getTitle());
        return ResponseEntity.ok(ApiResponse.ok("تم توليد المسير بنجاح", run));
    }

    @PostMapping("/runs/{runId}/approve")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> approve(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "تم اعتماد المسير — لن تتغير أرقامه بعد الآن", payrollService.approve(runId)));
    }

    @PostMapping("/runs/{runId}/pay")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> markPaid(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.ok("تم تسجيل المسير كمدفوع", payrollService.markPaid(runId)));
    }

    @PostMapping("/runs/{runId}/reopen")
    public ResponseEntity<ApiResponse<PayrollRunResponse>> reopen(
            @PathVariable UUID runId,
            @RequestBody ReopenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "تم إعادة فتح المسير كمسودة", payrollService.reopen(runId, request.getReason())));
    }

    @DeleteMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID runId) {
        payrollService.delete(runId);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف المسير"));
    }

    @PostMapping("/runs/{runId}/adjustments")
    public ResponseEntity<ApiResponse<PayrollLineResponse>> addAdjustment(
            @PathVariable UUID runId,
            @Valid @RequestBody AdjustmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "تمت إضافة البند", payrollService.addAdjustment(runId, request)));
    }

    @DeleteMapping("/runs/{runId}/adjustments/{adjustmentId}")
    public ResponseEntity<ApiResponse<PayrollLineResponse>> deleteAdjustment(
            @PathVariable UUID runId,
            @PathVariable UUID adjustmentId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "تم حذف البند", payrollService.deleteAdjustment(runId, adjustmentId)));
    }

    @GetMapping("/runs/{runId}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID runId,
            @RequestParam(required = false) String staffType) {
        PayrollRunResponse run = payrollService.get(runId);
        byte[] workbook;
        String rawFileName;

        if (staffType != null && !staffType.isBlank() && !"CAPTAIN".equalsIgnoreCase(staffType)) {
            List<StaffPayrollLineResponse> staffLines = payrollService.getStaffLines(runId, staffType);
            workbook = exportService.buildStaff(run, staffLines, staffType);

            String prefix = "WORKER".equalsIgnoreCase(staffType) ? "مسير-رواتب-العاملين-"
                    : "MANAGER".equalsIgnoreCase(staffType) ? "مسير-رواتب-الإداريين-" : "مسير-رواتب-الموظفين-";
            rawFileName = prefix + run.getPeriodFrom() + ".xlsx";
        } else {
            workbook = exportService.build(run, payrollService.getLines(runId));
            rawFileName = "مسير-رواتب-" + run.getPeriodFrom() + ".xlsx";
        }

        String fileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fileName)
                .body(workbook);
    }

    @GetMapping("/runs/{runId}/export-pdf")
    public ResponseEntity<byte[]> exportPdf(
            @PathVariable UUID runId,
            @RequestParam(required = false) String staffType) {
        PayrollRunResponse run = payrollService.get(runId);
        byte[] pdf;
        String rawFileName;

        if (staffType != null && !staffType.isBlank() && !"CAPTAIN".equalsIgnoreCase(staffType)) {
            List<StaffPayrollLineResponse> staffLines = payrollService.getStaffLines(runId, staffType);
            pdf = pdfExportService.buildStaffPayrollPdf(run, staffLines, staffType);

            String prefix = "WORKER".equalsIgnoreCase(staffType) ? "مسير-رواتب-العاملين-"
                    : "MANAGER".equalsIgnoreCase(staffType) ? "مسير-رواتب-الإداريين-" : "مسير-رواتب-الموظفين-";
            rawFileName = prefix + run.getPeriodFrom() + ".pdf";
        } else {
            pdf = pdfExportService.buildPayrollPdf(run, payrollService.getLines(runId));
            rawFileName = "مسير-رواتب-" + run.getPeriodFrom() + ".pdf";
        }

        String fileName = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(pdf);
    }

    @GetMapping("/runs/{runId}/payslip/{captainId}")
    public ResponseEntity<byte[]> captainPayslip(
            @PathVariable UUID runId,
            @PathVariable UUID captainId) {
        PayrollRunResponse run = payrollService.get(runId);
        PayrollLineResponse line = payrollService.getLines(runId).stream()
                .filter(l -> captainId.equals(l.getCaptainId()))
                .findFirst()
                .orElseThrow(() -> new com.kamal.lc.common.exception.BusinessException("NOT_FOUND", "الكابتن غير موجود في المسير"));

        byte[] pdf = pdfExportService.buildPayslipPdf(run, line);
        String fileName = URLEncoder.encode("مسير-فردي-" + line.getCaptainCode() + ".pdf", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(pdf);
    }

    @GetMapping("/runs/{runId}/bulk-payslips")
    public ResponseEntity<byte[]> bulkPayslips(@PathVariable UUID runId) {
        PayrollRunResponse run = payrollService.get(runId);
        List<PayrollLineResponse> lines = payrollService.getLines(runId);

        byte[] pdf = pdfExportService.buildBulkPayslipsPdf(run, lines);
        String fileName = URLEncoder.encode("مسيرات-فردية-مجمعة-" + run.getPeriodFrom() + ".pdf", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(pdf);
    }

    @GetMapping("/runs/{runId}/staff-payslip/{staffId}")
    public ResponseEntity<byte[]> staffPayslip(
            @PathVariable UUID runId,
            @PathVariable UUID staffId) {
        PayrollRunResponse run = payrollService.get(runId);
        StaffPayrollLineResponse line = payrollService.getStaffLines(runId, null).stream()
                .filter(l -> staffId.equals(l.getStaffId()))
                .findFirst()
                .orElseThrow(() -> new com.kamal.lc.common.exception.BusinessException(
                        "NOT_FOUND", "الموظف غير موجود في المسير"));

        byte[] pdf = pdfExportService.buildStaffPayslipPdf(run, line);
        String fileName = URLEncoder.encode("مسير-فردي-" + line.getStaffCode() + ".pdf", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(pdf);
    }

    @GetMapping("/runs/{runId}/bulk-staff-payslips")
    public ResponseEntity<byte[]> bulkStaffPayslips(
            @PathVariable UUID runId,
            @RequestParam(required = false) String staffType) {
        PayrollRunResponse run = payrollService.get(runId);
        List<StaffPayrollLineResponse> staffLines = payrollService.getStaffLines(runId, staffType);

        byte[] pdf = pdfExportService.buildBulkStaffPayslipsPdf(run, staffLines);

        String prefix = "WORKER".equalsIgnoreCase(staffType) ? "مسيرات-فردية-العاملين-"
                : "MANAGER".equalsIgnoreCase(staffType) ? "مسيرات-فردية-الإداريين-" : "مسيرات-فردية-الموظفين-";
        String fileName = URLEncoder.encode(prefix + run.getPeriodFrom() + ".pdf", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(pdf);
    }

    @PostMapping("/runs/{runId}/staff-adjustments")
    public ResponseEntity<ApiResponse<StaffPayrollLineResponse>> addStaffAdjustment(
            @PathVariable UUID runId,
            @Valid @RequestBody StaffAdjustmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                "تمت إضافة البند للموظف", payrollService.addStaffAdjustment(runId, request)));
    }

    @DeleteMapping("/runs/{runId}/staff-adjustments/{adjustmentId}")
    public ResponseEntity<ApiResponse<StaffPayrollLineResponse>> deleteStaffAdjustment(
            @PathVariable UUID runId,
            @PathVariable UUID adjustmentId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "تم حذف البند", payrollService.deleteStaffAdjustment(runId, adjustmentId)));
    }

    @Data
    public static class GenerateRequest {
        private LocalDate periodFrom;
        private LocalDate periodTo;
        private String title;
    }

    @Data
    public static class ReopenRequest {
        private String reason;
    }
}
