package com.kamal.lc.captainapp.controller;

import com.kamal.lc.captainapp.dto.CaptainAppDtos.DocumentRequestReview;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.ReviewDecision;
import com.kamal.lc.captainapp.service.CaptainDocumentRequestService;
import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Supervisors reviewing documents captains renewed from the app (web admin). */
@RestController
@RequestMapping("/api/captains/document-requests")
@RequiredArgsConstructor
public class CaptainDocumentRequestController {

    private final CaptainDocumentRequestService requestService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DocumentRequestReview>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(requestService.list(status, page, size)));
    }

    @GetMapping("/pending-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> pendingCount() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("count", requestService.pendingCount())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('MEMBERS_MANAGE')")
    public ResponseEntity<ApiResponse<DocumentRequestReview>> approve(
            @PathVariable UUID id, @Valid @RequestBody(required = false) ReviewDecision decision) {
        return ResponseEntity.ok(ApiResponse.ok("تم اعتماد المستند وتحديث بيانات الكابتن",
                requestService.approve(id, decision != null ? decision.note() : null)));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('MEMBERS_MANAGE')")
    public ResponseEntity<ApiResponse<DocumentRequestReview>> reject(
            @PathVariable UUID id, @Valid @RequestBody ReviewDecision decision) {
        return ResponseEntity.ok(ApiResponse.ok("تم رفض الطلب", requestService.reject(id, decision.note())));
    }
}
