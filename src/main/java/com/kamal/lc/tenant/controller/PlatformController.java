package com.kamal.lc.tenant.controller;

import com.kamal.lc.common.dto.ApiResponse;
import com.kamal.lc.common.dto.PageResponse;
import com.kamal.lc.common.dto.SummaryPageResponse;
import com.kamal.lc.tenant.dto.PlatformDtos.*;
import com.kamal.lc.tenant.service.PlatformService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Platform admin controller — PLATFORM_ADMIN only.
 * Manages tenants, subscription plans, subscriptions, invoices, audit logs, and users.
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformService service;

    // ================================================================ Dashboard

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<PlatformDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(service.getDashboard()));
    }

    // ================================================================ Tenants

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<SummaryPageResponse<TenantResponse, TenantListSummary>>> listTenants(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.listTenantsPage(filter, search, page, size)));
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenant(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getTenant(id)));
    }

    private String getActorName(Authentication auth) {
        return (auth != null && auth.getName() != null) ? auth.getName() : "system";
    }

    @PostMapping("/tenants")
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
            @RequestBody CreateTenantRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("تم إنشاء الشركة بنجاح",
                service.createTenant(request, getActorName(auth))));
    }

    @PutMapping("/tenants/{id}")
    public ResponseEntity<ApiResponse<TenantResponse>> updateTenant(
            @PathVariable UUID id, @RequestBody UpdateTenantRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث بيانات الشركة",
                service.updateTenant(id, request, getActorName(auth))));
    }

    @PutMapping("/tenants/{id}/password")
    public ResponseEntity<ApiResponse<Void>> changeTenantPassword(
            @PathVariable UUID id, @RequestBody ChangePasswordRequest request, Authentication auth) {
        service.changeTenantPassword(id, request.getNewPassword(), getActorName(auth));
        return ResponseEntity.ok(ApiResponse.ok("تم تغيير كلمة المرور لمدير الشركة بنجاح"));
    }

    @PatchMapping("/tenants/{id}/status")
    public ResponseEntity<ApiResponse<TenantResponse>> changeTenantStatus(
            @PathVariable UUID id, @RequestBody TenantStatusRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("تم تغيير حالة الشركة",
                service.changeTenantStatus(id, request, getActorName(auth))));
    }

    // ================================================================ Plans

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> listPlans() {
        return ResponseEntity.ok(ApiResponse.ok(service.listPlans()));
    }

    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
            @RequestBody PlanRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("تم إنشاء الخطة بنجاح",
                service.createPlan(request, getActorName(auth))));
    }

    @PutMapping("/plans/{id}")
    public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(
            @PathVariable UUID id, @RequestBody PlanRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث الخطة",
                service.updatePlan(id, request, getActorName(auth))));
    }

    @DeleteMapping("/plans/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable UUID id, Authentication auth) {
        service.deletePlan(id, getActorName(auth));
        return ResponseEntity.ok(ApiResponse.ok("تم تعطيل الخطة"));
    }

    // ================================================================ Subscriptions

    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<SummaryPageResponse<SubscriptionResponse, SubscriptionListSummary>>> listSubscriptions(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.listSubscriptionsPage(filter, search, page, size)));
    }

    @GetMapping("/tenants/{tenantId}/subscriptions")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> listTenantSubscriptions(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(service.listSubscriptionsForTenant(tenantId)));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> createSubscription(
            @RequestBody CreateSubscriptionRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("تم إنشاء الاشتراك بنجاح",
                service.createSubscription(request, getActorName(auth))));
    }

    // ================================================================ Invoices

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<SummaryPageResponse<InvoiceResponse, InvoiceListSummary>>> listInvoices(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.listInvoicesPage(filter, search, page, size)));
    }

    @GetMapping("/tenants/{tenantId}/invoices")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> listTenantInvoices(
            @PathVariable UUID tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(service.listInvoicesForTenant(tenantId)));
    }

    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<InvoiceResponse>> createInvoice(
            @RequestBody CreateInvoiceRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("تم إنشاء الفاتورة بنجاح",
                service.createInvoice(request, getActorName(auth))));
    }

    @PatchMapping("/invoices/{id}/pay")
    public ResponseEntity<ApiResponse<InvoiceResponse>> payInvoice(
            @PathVariable UUID id, @RequestBody PayInvoiceRequest request, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok("تم تسجيل الدفع بنجاح",
                service.payInvoice(id, request, getActorName(auth))));
    }

    // ================================================================ Users

    @PutMapping("/users/{id}/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable UUID id, @RequestBody ChangePasswordRequest request, Authentication auth) {
        service.changeUserPassword(id, request.getNewPassword(), getActorName(auth));
        return ResponseEntity.ok(ApiResponse.ok("تم تغيير كلمة المرور بنجاح"));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> updateUser(
            @PathVariable UUID id, @RequestBody UpdateUserByPlatformRequest request, Authentication auth) {
        service.updateUserByPlatform(id, request, getActorName(auth));
        return ResponseEntity.ok(ApiResponse.ok("تم تحديث بيانات المستخدم"));
    }

    // ================================================================ Audit Logs

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<SummaryPageResponse<AuditLogResponse, AuditLogFilters>>> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAuditLogs(action, tenantId, search, page, size)));
    }
}
