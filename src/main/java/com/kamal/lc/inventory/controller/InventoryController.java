package com.kamal.lc.inventory.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.inventory.dto.*;
import com.kamal.lc.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService service;

    // ──── Categories ────

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<AssetCategoryResponse>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAllCategories()));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<AssetCategoryResponse>> createCategory(
            @Valid @RequestBody AssetCategoryRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم إضافة الفئة بنجاح", service.createCategory(req)));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        service.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف الفئة"));
    }

    // ──── Items ────

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<PageResponse<AssetItemResponse>>> items(
            @RequestParam UUID categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {

        Page<AssetItemResponse> result = service.listItems(categoryId, status, search,
                PageRequest.of(page, size, Sort.by("serialCode")));

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.<AssetItemResponse>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build()));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<AssetItemResponse>> createItem(@Valid @RequestBody AssetItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم إضافة العنصر بنجاح", service.createItem(req)));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ApiResponse<AssetItemResponse>> updateItem(@PathVariable UUID id,
                                                                       @Valid @RequestBody AssetItemRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث العنصر", service.updateItem(id, req)));
    }

    @PatchMapping("/items/{id}/status")
    public ResponseEntity<ApiResponse<AssetItemResponse>> updateStatus(@PathVariable UUID id,
                                                                         @RequestBody Map<String, String> body) {
        String status = body.get("status");
        return ResponseEntity.ok(ApiResponse.ok("تم تغيير الحالة", service.updateStatus(id, status)));
    }

    @PostMapping("/items/{id}/assign")
    public ResponseEntity<ApiResponse<AssetItemResponse>> assignItem(@PathVariable UUID id,
                                                                       @Valid @RequestBody AssetAssignRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("تم إسناد العنصر للكابتن", service.assignItem(id, req)));
    }

    @PostMapping("/items/{id}/return")
    public ResponseEntity<ApiResponse<AssetItemResponse>> returnItem(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok("تم استرجاع العنصر", service.returnItem(id)));
    }
}
