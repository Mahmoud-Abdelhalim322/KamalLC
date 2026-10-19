package com.kamal.lc.changelog.controller;

import com.kamal.lc.changelog.dto.ChangeLogFilter;
import com.kamal.lc.changelog.dto.ChangeLogResponse;
import com.kamal.lc.changelog.service.ChangeLogExportService;
import com.kamal.lc.changelog.service.ChangeLogService;
import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/changelog")
@RequiredArgsConstructor
public class ChangeLogController {

    private final ChangeLogService service;
    private final ChangeLogExportService exportService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ChangeLogResponse>>> search(
            @ModelAttribute ChangeLogFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<ChangeLogResponse> result = service.search(filter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "changedAt")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<ChangeLogResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    /** List of distinct users who made changes — for the filter dropdown. */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<String>>> users() {
        return ResponseEntity.ok(ApiResponse.ok(service.getChangedByUsers()));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(@ModelAttribute ChangeLogFilter filter) {
        byte[] bytes = exportService.exportExcel(filter);
        String fileName = URLEncoder.encode("سجل-التغييرات.xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(bytes);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@ModelAttribute ChangeLogFilter filter) {
        byte[] bytes = exportService.exportPdf(filter);
        String fileName = URLEncoder.encode("تقرير-التغييرات.pdf", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(bytes);
    }
}
