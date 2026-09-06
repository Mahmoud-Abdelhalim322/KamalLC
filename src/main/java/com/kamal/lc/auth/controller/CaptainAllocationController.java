package com.kamal.lc.auth.controller;

import com.kamal.lc.auth.dto.CaptainAllocationDtos.*;
import com.kamal.lc.auth.service.CaptainAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CaptainAllocationController {

    private final CaptainAllocationService allocationService;

    @GetMapping("/admin/allocations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserAllocationSummaryResponse>> getAllAllocations() {
        return ResponseEntity.ok(allocationService.getAllUserAllocations());
    }

    @GetMapping("/admin/allocations/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAllocationDetailResponse> getUserAllocationDetail(@PathVariable UUID userId) {
        return ResponseEntity.ok(allocationService.getUserAllocationDetail(userId));
    }

    @PutMapping("/admin/allocations/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserAllocationDetailResponse> updateUserAllocation(
            @PathVariable UUID userId,
            @RequestBody UpdateUserAllocationRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(allocationService.updateUserAllocation(userId, request, authentication.getName()));
    }

    @GetMapping("/auth/me/data-scope")
    public ResponseEntity<Map<String, Object>> getMyDataScope() {
        List<UUID> accessibleIds = allocationService.getCurrentUserAccessibleCaptainIds();
        Map<String, Object> response = new HashMap<>();
        if (accessibleIds == null) {
            response.put("dataScope", "ALL_CAPTAINS");
            response.put("restricted", false);
            response.put("assignedCaptainCount", null);
        } else {
            response.put("dataScope", "ASSIGNED_ONLY");
            response.put("restricted", true);
            response.put("assignedCaptainCount", accessibleIds.size());
        }
        return ResponseEntity.ok(response);
    }
}
