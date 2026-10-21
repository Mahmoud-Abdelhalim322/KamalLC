package com.kamal.lc.auth.controller;

import com.kamal.lc.auth.dto.UserManagementDtos.*;
import com.kamal.lc.auth.service.UserManagementService;
import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.common.dto.SummaryPageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * User and job-title management — SUPER_ADMIN only.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService service;

    // ---- Permissions ----

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<PermissionInfo>>> allPermissions() {
        return ResponseEntity.ok(ApiResponse.ok(service.allPermissions()));
    }

    // ---- Job Titles ----

    @GetMapping("/job-titles")
    public ResponseEntity<ApiResponse<List<JobTitleResponse>>> listJobTitles() {
        return ResponseEntity.ok(ApiResponse.ok(service.listJobTitles()));
    }

    @PostMapping("/job-titles")
    public ResponseEntity<ApiResponse<JobTitleResponse>> createJobTitle(
            @Valid @RequestBody JobTitleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم إنشاء الوظيفة بنجاح", service.createJobTitle(request)));
    }

    @PutMapping("/job-titles/{id}")
    public ResponseEntity<ApiResponse<JobTitleResponse>> updateJobTitle(
            @PathVariable UUID id,
            @Valid @RequestBody JobTitleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث الوظيفة بنجاح", service.updateJobTitle(id, request)));
    }

    @DeleteMapping("/job-titles/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteJobTitle(@PathVariable UUID id) {
        service.deleteJobTitle(id);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف الوظيفة"));
    }

    // ---- Users ----

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<SummaryPageResponse<UserResponse, UserListSummary>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        List<UserResponse> all = service.listUsers();
        UserListSummary summary = new UserListSummary(all.size(), all.stream().filter(UserResponse::isActive).count());
        return ResponseEntity.ok(ApiResponse.ok(SummaryPageResponse.of(PageResponse.slice(all, page, size), summary)));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم إنشاء الحساب بنجاح", service.createUser(request)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث الحساب بنجاح", service.updateUser(id, request)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID id) {
        service.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.ok("تم حذف الحساب"));
    }
}
