package com.kamal.lc.captain.controller;

import com.kamal.lc.captain.dto.CaptainDetailResponse;
import com.kamal.lc.captain.dto.CaptainLookupResponse;
import com.kamal.lc.captain.dto.CaptainRequest;
import com.kamal.lc.captain.dto.CaptainResponse;
import com.kamal.lc.captain.service.CaptainService;
import com.kamal.lc.captain.service.MembersExportService;
import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
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
@RequestMapping("/api/captains")
@RequiredArgsConstructor
public class CaptainController {

    private final CaptainService captainService;
    private final MembersExportService membersExportService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CaptainResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String alert,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<CaptainResponse> result = captainService.getCaptains(search, type, alert,
                PageRequest.of(page, size, Sort.by("fullNameAr")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<CaptainResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    /** Short list for search-as-you-type pickers (e.g. linking a supplier ID to a captain). */
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<List<CaptainLookupResponse>>> lookup(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(ApiResponse.ok(captainService.lookup(q)));
    }

    /** Preview of the auto-generated kamal code for a type — shown in the form before saving. */
    @GetMapping("/next-code")
    public ResponseEntity<ApiResponse<Map<String, String>>> nextCode(@RequestParam String type) {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("kamalCode", captainService.previewNextCode(type))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CaptainDetailResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(captainService.getCaptainDetail(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CaptainDetailResponse>> create(@Valid @RequestBody CaptainRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم إضافة الكابتن بنجاح", captainService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CaptainDetailResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CaptainRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم حفظ بيانات الكابتن بنجاح", captainService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        captainService.deleteCaptain(id);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف الكابتن بنجاح", null));
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkDelete(@RequestBody List<UUID> ids) {
        int deleted = 0;
        int skipped = 0;
        for (UUID id : ids) {
            try {
                captainService.deleteCaptain(id);
                deleted++;
            } catch (Exception e) {
                skipped++;
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(
                "تم حذف " + deleted + " كابتن" + (skipped > 0 ? " — تم تخطي " + skipped + " لارتباطهم ببيانات" : ""),
                Map.of("deleted", deleted, "skipped", skipped)));
    }

    /** Export captains as Excel with optional type filter. */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String type) {
        byte[] bytes = membersExportService.exportCaptainsExcel(type);
        String filename = "captains-export.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
