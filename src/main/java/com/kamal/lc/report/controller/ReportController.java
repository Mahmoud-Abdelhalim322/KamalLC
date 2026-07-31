package com.kamal.lc.report.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.report.dto.CaptainReportResponse;
import com.kamal.lc.report.dto.TeamReportResponse;
import com.kamal.lc.report.service.CaptainReportExportService;
import com.kamal.lc.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final CaptainReportExportService captainExportService;

    @GetMapping("/captain/{captainId}")
    public ResponseEntity<ApiResponse<CaptainReportResponse>> captain(
            @PathVariable UUID captainId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.captainReport(captainId, from, to)));
    }

    /**
     * The individual report as an Excel sheet. It leaves out the period's shift list and
     * the profitability figures, so the file can go to the captain himself.
     */
    @GetMapping("/captain/{captainId}/export")
    public ResponseEntity<byte[]> exportCaptain(
            @PathVariable UUID captainId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        CaptainReportResponse report = reportService.captainReport(captainId, from, to);

        String fileName = URLEncoder.encode(
                "الأداء-" + report.getCaptainCode() + "-" + from + ".xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fileName)
                .body(captainExportService.build(report));
    }

    /** All captains side by side; also the source of the profitability view. */
    @GetMapping("/team")
    public ResponseEntity<ApiResponse<TeamReportResponse>> team(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.teamReport(from, to)));
    }
}
