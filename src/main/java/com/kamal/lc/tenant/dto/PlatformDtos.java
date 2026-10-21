package com.kamal.lc.tenant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTOs for tenant/platform management APIs.
 */
public class PlatformDtos {

    // ---- Tenant ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TenantResponse {
        private UUID id;
        private String name;
        private String slug;
        private String logo;
        private String primaryColor;
        private String secondaryColor;
        private String contactEmail;
        private String contactPhone;
        private String address;
        private String status;
        private String trialEndsAt;
        private String createdAt;
        private long userCount;
        private long captainCount;
        private String currentPlan;
        private String subscriptionStatus;
        private String subscriptionEndDate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateTenantRequest {
        private String name;
        private String slug;
        private String contactEmail;
        private String contactPhone;
        private String address;
        private String logo;
        private String primaryColor;
        private String secondaryColor;
        // Initial admin user
        private String adminUsername;
        private String adminPassword;
        private String adminDisplayName;
        // Initial subscription
        private UUID planId;
        private String billingCycle; // MONTHLY or YEARLY
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateTenantRequest {
        private String name;
        private String contactEmail;
        private String contactPhone;
        private String address;
        private String logo;
        private String primaryColor;
        private String secondaryColor;
        private String newPassword;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TenantStatusRequest {
        private String status; // ACTIVE, SUSPENDED, CANCELLED
        private String reason;
    }

    // ---- Subscription Plan ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PlanResponse {
        private UUID id;
        private String name;
        private String nameAr;
        private String description;
        private String descriptionAr;
        private BigDecimal monthlyPrice;
        private BigDecimal yearlyPrice;
        private boolean active;
        private String features;
        private int sortOrder;
        private long tenantCount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PlanRequest {
        private String name;
        private String nameAr;
        private String description;
        private String descriptionAr;
        private BigDecimal monthlyPrice;
        private BigDecimal yearlyPrice;
        private String features;
        private int sortOrder;
    }

    // ---- Subscription ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SubscriptionResponse {
        private UUID id;
        private UUID tenantId;
        private String tenantName;
        private UUID planId;
        private String planName;
        private String startDate;
        private String endDate;
        private String status;
        private BigDecimal paidAmount;
        private String billingCycle;
        private boolean autoRenew;
        private String notes;
        private String createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateSubscriptionRequest {
        private UUID tenantId;
        private UUID planId;
        private String startDate;
        private String endDate;
        private BigDecimal paidAmount;
        private String billingCycle;
        private boolean autoRenew;
        private String notes;
    }

    // ---- Invoice ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InvoiceResponse {
        private UUID id;
        private UUID tenantId;
        private String tenantName;
        private String invoiceNumber;
        private BigDecimal amount;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private String status;
        private String dueDate;
        private String paidAt;
        private String paymentMethod;
        private String paymentRef;
        private String notes;
        private String createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateInvoiceRequest {
        private UUID tenantId;
        private UUID subscriptionId;
        private BigDecimal amount;
        private BigDecimal taxAmount;
        private String dueDate;
        private String notes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PayInvoiceRequest {
        private String paymentMethod;
        private String paymentRef;
        private String notes;
    }

    // ---- Audit Log ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AuditLogResponse {
        private UUID id;
        private String actorUsername;
        private String actorRole;
        private UUID tenantId;
        private String tenantName;
        private String action;
        private String entityType;
        private String entityId;
        private String details;
        private String ipAddress;
        private String createdAt;
    }

    // ---- Platform Dashboard ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PlatformDashboardResponse {
        private long totalTenants;
        private long activeTenants;
        private long suspendedTenants;
        private long trialTenants;
        private long totalUsers;
        private long totalCaptains;
        private long totalSubscriptions;
        private long activeSubscriptions;
        private BigDecimal totalRevenue;
        private BigDecimal monthlyRecurringRevenue;
        private long pendingInvoices;
        private BigDecimal pendingInvoiceAmount;
    }

    // ---- Change Password (no old password required) ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ChangePasswordRequest {
        private String newPassword;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateUserByPlatformRequest {
        private String displayName;
        private String username;
        private Boolean active;
        private String role;
    }

    // ---- List summaries (KPI strips over the whole filtered set) ----

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TenantListSummary {
        private long total;
        private long active;
        private long trial;
        private long suspended;
        private long users;
        private long captains;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SubscriptionListSummary {
        private long total;
        private long active;
        private long expiring;
        private long expired;
        private long yearly;
        private long autoRenew;
        private BigDecimal totalPaid;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InvoiceListSummary {
        private long total;
        private long paidCount;
        private BigDecimal paidTotal;
        private long pendingCount;
        private BigDecimal pendingTotal;
        private long overdueCount;
        private BigDecimal overdueTotal;
    }

    /** Filter choices for the audit log: actions that occur in the log, and the tenants. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AuditLogFilters {
        private java.util.List<String> actions;
        private java.util.List<TenantOption> tenants;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TenantOption {
        private UUID id;
        private String name;
    }
}
