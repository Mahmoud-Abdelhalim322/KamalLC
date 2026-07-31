package com.kamal.lc.custody.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.custody.dto.CaptainCustodySummary;
import com.kamal.lc.custody.dto.CustodyRecordResponse;
import com.kamal.lc.custody.service.CustodyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/custody")
@RequiredArgsConstructor
public class CustodyController {

    private final CustodyService service;

    /** All captains who hold at least one active item, with their items. */
    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<List<CaptainCustodySummary>>> summaries() {
        return ResponseEntity.ok(ApiResponse.ok(service.getCaptainSummaries()));
    }

    /** Active custody items for a specific captain. */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<CustodyRecordResponse>>> activeByCaptain(
            @RequestParam UUID captainId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getActiveCustody(captainId)));
    }

    /** Full custody history (active + returned) for a captain. */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<CustodyRecordResponse>>> history(
            @RequestParam UUID captainId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<CustodyRecordResponse> result = service.getCustodyHistory(captainId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assignedAt")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<CustodyRecordResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    /** Full custody history (active + returned) for a specific item (by referenceId). */
    @GetMapping("/item-history")
    public ResponseEntity<ApiResponse<PageResponse<CustodyRecordResponse>>> itemHistory(
            @RequestParam UUID referenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<CustodyRecordResponse> result = service.getItemCustodyHistory(referenceId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assignedAt")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<CustodyRecordResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    /** All active custody records across all captains, filterable by asset type. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustodyRecordResponse>>> allActive(
            @RequestParam(required = false) String assetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<CustodyRecordResponse> result;
        if (assetType != null && !assetType.isBlank()) {
            result = service.getByAssetType(assetType,
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assignedAt")));
        } else {
            result = service.getAllActive(
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assignedAt")));
        }

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<CustodyRecordResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }
}
