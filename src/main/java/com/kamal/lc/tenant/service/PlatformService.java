package com.kamal.lc.tenant.service;

import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.auth.repository.UserRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.inventory.service.InventoryService;
import com.kamal.lc.settings.service.SettingsService;
import com.kamal.lc.tenant.dto.PlatformDtos.*;
import com.kamal.lc.tenant.entity.*;
import com.kamal.lc.tenant.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Platform-level management service — tenants, subscriptions, plans, invoices.
 * Only callable by PLATFORM_ADMIN users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformService {

    private final TenantRepository tenantRepo;
    private final SubscriptionPlanRepository planRepo;
    private final TenantSubscriptionRepository subscriptionRepo;
    private final TenantInvoiceRepository invoiceRepo;
    private final AuditLogRepository auditLogRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settingsService;
    private final InventoryService inventoryService;

    // ================================================================ Tenants

    public List<TenantResponse> listTenants() {
        return tenantRepo.findAll().stream().map(this::toTenantResponse).toList();
    }

    public TenantResponse getTenant(UUID id) {
        return toTenantResponse(findTenant(id));
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest req, String actorUsername) {
        if (req.getName() == null || req.getName().isBlank())
            throw new BusinessException("INVALID_INPUT", "اسم الشركة مطلوب");
        if (req.getSlug() == null || req.getSlug().isBlank())
            throw new BusinessException("INVALID_INPUT", "معرف الشركة مطلوب");
        if (tenantRepo.existsBySlug(req.getSlug().trim().toLowerCase()))
            throw new BusinessException("DUPLICATE", "معرف الشركة موجود بالفعل");
        if (req.getAdminUsername() == null || req.getAdminUsername().isBlank())
            throw new BusinessException("INVALID_INPUT", "اسم مستخدم المدير مطلوب");
        if (userRepo.existsByUsername(req.getAdminUsername().trim()))
            throw new BusinessException("DUPLICATE", "اسم المستخدم موجود بالفعل");

        // Create tenant
        Tenant tenant = Tenant.builder()
                .name(req.getName().trim())
                .slug(req.getSlug().trim().toLowerCase())
                .contactEmail(req.getContactEmail())
                .contactPhone(req.getContactPhone())
                .address(req.getAddress())
                .logo(req.getLogo())
                .primaryColor(req.getPrimaryColor() != null ? req.getPrimaryColor() : "#1976D2")
                .secondaryColor(req.getSecondaryColor() != null ? req.getSecondaryColor() : "#424242")
                .status("ACTIVE")
                .build();
        tenant = tenantRepo.save(tenant);

        // Seed default platform settings for the newly created tenant
        settingsService.initializeDefaultSettingsForTenant(tenant);

        // Seed default inventory categories for the newly created tenant
        inventoryService.initializeDefaultCategoriesForTenant(tenant);

        // Create admin user for the tenant
        AppUser admin = AppUser.builder()
                .username(req.getAdminUsername().trim())
                .passwordHash(passwordEncoder.encode(
                        req.getAdminPassword() != null ? req.getAdminPassword() : "Admin@12345"))
                .displayName(req.getAdminDisplayName() != null ? req.getAdminDisplayName() : "مدير " + req.getName())
                .role("ADMIN")
                .active(true)
                .tenant(tenant)
                .build();
        userRepo.save(admin);

        // Create subscription if plan specified
        if (req.getPlanId() != null) {
            SubscriptionPlan plan = planRepo.findById(req.getPlanId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "خطة الاشتراك غير موجودة"));
            String cycle = req.getBillingCycle() != null ? req.getBillingCycle() : "MONTHLY";
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = "YEARLY".equals(cycle) ? startDate.plusYears(1) : startDate.plusMonths(1);
            BigDecimal amount = "YEARLY".equals(cycle) ? plan.getYearlyPrice() : plan.getMonthlyPrice();

            TenantSubscription sub = TenantSubscription.builder()
                    .tenant(tenant)
                    .plan(plan)
                    .startDate(startDate)
                    .endDate(endDate)
                    .status("ACTIVE")
                    .paidAmount(amount)
                    .billingCycle(cycle)
                    .build();
            subscriptionRepo.save(sub);
        }

        logAudit(actorUsername, "CREATE_TENANT", "Tenant", tenant.getId().toString(),
                "{\"name\":\"" + tenant.getName() + "\",\"slug\":\"" + tenant.getSlug() + "\"}", tenant.getId());

        return toTenantResponse(tenant);
    }

    @Transactional
    public TenantResponse updateTenant(UUID id, UpdateTenantRequest req, String actorUsername) {
        Tenant tenant = findTenant(id);

        if (req.getName() != null && !req.getName().isBlank()) tenant.setName(req.getName().trim());
        if (req.getContactEmail() != null) tenant.setContactEmail(req.getContactEmail());
        if (req.getContactPhone() != null) tenant.setContactPhone(req.getContactPhone());
        if (req.getAddress() != null) tenant.setAddress(req.getAddress());
        if (req.getLogo() != null) tenant.setLogo(req.getLogo());
        if (req.getPrimaryColor() != null) tenant.setPrimaryColor(req.getPrimaryColor());
        if (req.getSecondaryColor() != null) tenant.setSecondaryColor(req.getSecondaryColor());

        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            changeTenantPassword(id, req.getNewPassword(), actorUsername);
        }

        logAudit(actorUsername, "UPDATE_TENANT", "Tenant", id.toString(),
                "{\"name\":\"" + tenant.getName() + "\"}", tenant.getId());

        return toTenantResponse(tenantRepo.save(tenant));
    }

    @Transactional
    public void changeTenantPassword(UUID tenantId, String newPassword, String actorUsername) {
        if (newPassword == null || newPassword.trim().length() < 4)
            throw new BusinessException("INVALID_INPUT", "كلمة المرور يجب أن تكون 4 أحرف على الأقل");

        Tenant tenant = findTenant(tenantId);
        List<AppUser> tenantUsers = userRepo.findAll().stream()
                .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(tenantId))
                .toList();

        List<AppUser> admins = tenantUsers.stream().filter(AppUser::isAdmin).toList();
        List<AppUser> targetUsers = admins.isEmpty() ? tenantUsers : admins;

        if (targetUsers.isEmpty()) {
            throw new BusinessException("NOT_FOUND", "لم يتم العثور على مستخدمين لهذه الشركة لتغيير كلمة المرور");
        }

        String encoded = passwordEncoder.encode(newPassword.trim());
        for (AppUser user : targetUsers) {
            user.setPasswordHash(encoded);
            userRepo.save(user);
        }

        logAudit(actorUsername, "CHANGE_TENANT_PASSWORD", "Tenant", tenantId.toString(),
                "{\"tenantId\":\"" + tenantId + "\"}", tenantId);
    }

    @Transactional
    public TenantResponse changeTenantStatus(UUID id, TenantStatusRequest req, String actorUsername) {
        Tenant tenant = findTenant(id);
        String oldStatus = tenant.getStatus();
        tenant.setStatus(req.getStatus());
        tenant = tenantRepo.save(tenant);

        // If suspending/cancelling, deactivate all tenant users
        if ("SUSPENDED".equals(req.getStatus()) || "CANCELLED".equals(req.getStatus())) {
            List<AppUser> users = userRepo.findAll().stream()
                    .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(id))
                    .toList();
            users.forEach(u -> u.setActive(false));
            userRepo.saveAll(users);
        }

        // If reactivating, reactivate admin users
        if ("ACTIVE".equals(req.getStatus()) && !"ACTIVE".equals(oldStatus)) {
            List<AppUser> admins = userRepo.findAll().stream()
                    .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(id) && u.isAdmin())
                    .toList();
            admins.forEach(u -> u.setActive(true));
            userRepo.saveAll(admins);
        }

        logAudit(actorUsername, "CHANGE_TENANT_STATUS", "Tenant", id.toString(),
                "{\"from\":\"" + oldStatus + "\",\"to\":\"" + req.getStatus() + "\",\"reason\":\"" +
                        (req.getReason() != null ? req.getReason() : "") + "\"}", tenant.getId());

        return toTenantResponse(tenant);
    }

    // ================================================================ Plans

    public List<PlanResponse> listPlans() {
        return planRepo.findAll().stream().map(this::toPlanResponse).toList();
    }

    @Transactional
    public PlanResponse createPlan(PlanRequest req, String actorUsername) {
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .name(req.getName())
                .nameAr(req.getNameAr())
                .description(req.getDescription())
                .descriptionAr(req.getDescriptionAr())
                .monthlyPrice(req.getMonthlyPrice())
                .yearlyPrice(req.getYearlyPrice())
                .features(req.getFeatures())
                .sortOrder(req.getSortOrder())
                .build();
        plan = planRepo.save(plan);
        logAudit(actorUsername, "CREATE_PLAN", "SubscriptionPlan", plan.getId().toString(),
                toJsonDetails(buildDetailsMap("name", plan.getName(), "monthlyPrice", plan.getMonthlyPrice())), null);
        return toPlanResponse(plan);
    }

    @Transactional
    public PlanResponse updatePlan(UUID id, PlanRequest req, String actorUsername) {
        SubscriptionPlan plan = planRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "خطة الاشتراك غير موجودة"));

        if (req.getName() != null && !req.getName().isBlank()) plan.setName(req.getName());
        if (req.getNameAr() != null && !req.getNameAr().isBlank()) plan.setNameAr(req.getNameAr());
        if (req.getDescription() != null) plan.setDescription(req.getDescription());
        if (req.getDescriptionAr() != null) plan.setDescriptionAr(req.getDescriptionAr());
        if (req.getMonthlyPrice() != null) plan.setMonthlyPrice(req.getMonthlyPrice());
        plan.setYearlyPrice(req.getYearlyPrice());
        if (req.getFeatures() != null) plan.setFeatures(req.getFeatures());
        if (req.getSortOrder() > 0) plan.setSortOrder(req.getSortOrder());

        SubscriptionPlan saved = planRepo.save(plan);
        logAudit(actorUsername, "UPDATE_PLAN", "SubscriptionPlan", id.toString(),
                toJsonDetails(buildDetailsMap("name", saved.getName(), "monthlyPrice", saved.getMonthlyPrice())), null);
        return toPlanResponse(saved);
    }

    @Transactional
    public void deletePlan(UUID id, String actorUsername) {
        SubscriptionPlan plan = planRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "خطة الاشتراك غير موجودة"));
        plan.setActive(false);
        planRepo.save(plan);
        logAudit(actorUsername, "DELETE_PLAN", "SubscriptionPlan", id.toString(), null, null);
    }

    // ================================================================ Subscriptions

    public List<SubscriptionResponse> listSubscriptions() {
        return subscriptionRepo.findAll().stream().map(this::toSubscriptionResponse).toList();
    }

    public List<SubscriptionResponse> listSubscriptionsForTenant(UUID tenantId) {
        return subscriptionRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toSubscriptionResponse).toList();
    }

    @Transactional
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest req, String actorUsername) {
        Tenant tenant = findTenant(req.getTenantId());
        SubscriptionPlan plan = planRepo.findById(req.getPlanId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "خطة الاشتراك غير موجودة"));

        LocalDate startDate = req.getStartDate() != null ? LocalDate.parse(req.getStartDate()) : LocalDate.now();
        String cycle = req.getBillingCycle() != null ? req.getBillingCycle() : "MONTHLY";
        LocalDate endDate;
        if (req.getEndDate() != null) {
            endDate = LocalDate.parse(req.getEndDate());
        } else {
            endDate = "YEARLY".equals(cycle) ? startDate.plusYears(1) : startDate.plusMonths(1);
        }

        BigDecimal paidAmount = req.getPaidAmount();
        if (paidAmount == null) {
            paidAmount = "YEARLY".equals(cycle) ?
                    (plan.getYearlyPrice() != null ? plan.getYearlyPrice() : plan.getMonthlyPrice().multiply(BigDecimal.valueOf(12)))
                    : plan.getMonthlyPrice();
        }

        TenantSubscription sub = TenantSubscription.builder()
                .tenant(tenant)
                .plan(plan)
                .startDate(startDate)
                .endDate(endDate)
                .status("ACTIVE")
                .paidAmount(paidAmount)
                .billingCycle(cycle)
                .autoRenew(req.isAutoRenew())
                .notes(req.getNotes())
                .build();
        sub = subscriptionRepo.save(sub);

        logAudit(actorUsername, "CREATE_SUBSCRIPTION", "TenantSubscription", sub.getId().toString(),
                toJsonDetails(buildDetailsMap("tenant", tenant.getName(), "plan", plan.getName(), "amount", paidAmount)), tenant.getId());

        return toSubscriptionResponse(sub);
    }

    // ================================================================ Invoices

    public List<InvoiceResponse> listInvoices() {
        return invoiceRepo.findAll().stream().map(this::toInvoiceResponse).toList();
    }

    public List<InvoiceResponse> listInvoicesForTenant(UUID tenantId) {
        return invoiceRepo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toInvoiceResponse).toList();
    }

    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest req, String actorUsername) {
        Tenant tenant = findTenant(req.getTenantId());
        BigDecimal tax = req.getTaxAmount() != null ? req.getTaxAmount() : BigDecimal.ZERO;
        BigDecimal total = req.getAmount().add(tax);

        long count = invoiceRepo.countByTenantId(tenant.getId());
        String invoiceNumber = String.format("INV-%s-%04d", tenant.getSlug().toUpperCase(), count + 1);

        TenantInvoice invoice = TenantInvoice.builder()
                .tenant(tenant)
                .invoiceNumber(invoiceNumber)
                .amount(req.getAmount())
                .taxAmount(tax)
                .totalAmount(total)
                .dueDate(req.getDueDate() != null ? LocalDate.parse(req.getDueDate()) : LocalDate.now().plusDays(30))
                .notes(req.getNotes())
                .build();

        if (req.getSubscriptionId() != null) {
            invoice.setSubscription(subscriptionRepo.findById(req.getSubscriptionId()).orElse(null));
        }

        invoice = invoiceRepo.save(invoice);
        logAudit(actorUsername, "CREATE_INVOICE", "TenantInvoice", invoice.getId().toString(),
                "{\"invoiceNumber\":\"" + invoiceNumber + "\",\"amount\":" + total + "}", tenant.getId());

        return toInvoiceResponse(invoice);
    }

    @Transactional
    public InvoiceResponse payInvoice(UUID id, PayInvoiceRequest req, String actorUsername) {
        TenantInvoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الفاتورة غير موجودة"));

        invoice.setStatus("PAID");
        invoice.setPaidAt(OffsetDateTime.now());
        invoice.setPaymentMethod(req.getPaymentMethod());
        invoice.setPaymentRef(req.getPaymentRef());
        if (req.getNotes() != null) invoice.setNotes(req.getNotes());

        logAudit(actorUsername, "PAY_INVOICE", "TenantInvoice", id.toString(),
                "{\"invoiceNumber\":\"" + invoice.getInvoiceNumber() + "\"}", invoice.getTenant().getId());

        return toInvoiceResponse(invoiceRepo.save(invoice));
    }

    // ================================================================ Users

    @Transactional
    public void changeUserPassword(UUID userId, String newPassword, String actorUsername) {
        if (newPassword == null || newPassword.length() < 4)
            throw new BusinessException("INVALID_INPUT", "كلمة المرور يجب أن تكون 4 أحرف على الأقل");

        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المستخدم غير موجود"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);

        logAudit(actorUsername, "CHANGE_PASSWORD", "AppUser", userId.toString(),
                "{\"username\":\"" + user.getUsername() + "\"}", 
                user.getTenant() != null ? user.getTenant().getId() : null);
    }

    @Transactional
    public void updateUserByPlatform(UUID userId, UpdateUserByPlatformRequest req, String actorUsername) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المستخدم غير موجود"));

        if (req.getDisplayName() != null) user.setDisplayName(req.getDisplayName().trim());
        if (req.getActive() != null) user.setActive(req.getActive());
        if (req.getRole() != null && !user.isPlatformAdmin()) user.setRole(req.getRole());

        userRepo.save(user);
        logAudit(actorUsername, "UPDATE_USER", "AppUser", userId.toString(),
                "{\"username\":\"" + user.getUsername() + "\"}",
                user.getTenant() != null ? user.getTenant().getId() : null);
    }

    // ================================================================ Dashboard

    public PlatformDashboardResponse getDashboard() {
        List<Tenant> tenants = tenantRepo.findAll();
        List<TenantSubscription> subs = subscriptionRepo.findAll();
        List<TenantInvoice> invoices = invoiceRepo.findAll();

        long activeTenants = tenants.stream().filter(t -> "ACTIVE".equals(t.getStatus())).count();
        long suspendedTenants = tenants.stream().filter(t -> "SUSPENDED".equals(t.getStatus())).count();
        long trialTenants = tenants.stream().filter(t -> "TRIAL".equals(t.getStatus())).count();

        long activeSubs = subs.stream().filter(s -> "ACTIVE".equals(s.getStatus())).count();

        BigDecimal totalRevenue = invoices.stream()
                .filter(i -> "PAID".equals(i.getStatus()))
                .map(TenantInvoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TenantInvoice> pendingInvs = invoices.stream()
                .filter(i -> "PENDING".equals(i.getStatus())).toList();
        BigDecimal pendingAmount = pendingInvs.stream()
                .map(TenantInvoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // MRR: sum of monthly prices for active subs
        BigDecimal mrr = subs.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .map(s -> s.getPaidAmount() != null ? 
                        ("YEARLY".equals(s.getBillingCycle()) ? 
                                s.getPaidAmount().divide(BigDecimal.valueOf(12), 2, BigDecimal.ROUND_HALF_UP) :
                                s.getPaidAmount()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PlatformDashboardResponse.builder()
                .totalTenants(tenants.size())
                .activeTenants(activeTenants)
                .suspendedTenants(suspendedTenants)
                .trialTenants(trialTenants)
                .totalUsers(userRepo.count())
                .totalSubscriptions(subs.size())
                .activeSubscriptions(activeSubs)
                .totalRevenue(totalRevenue)
                .monthlyRecurringRevenue(mrr)
                .pendingInvoices(pendingInvs.size())
                .pendingInvoiceAmount(pendingAmount)
                .build();
    }

    // ================================================================ Audit

    public List<AuditLogResponse> getAuditLogs(int page, int size) {
        return auditLogRepo.findByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(page, size))
                .stream().map(this::toAuditLogResponse).toList();
    }

    // ================================================================ Helpers

    private Tenant findTenant(UUID id) {
        return tenantRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الشركة غير موجودة"));
    }

    private Map<String, Object> buildDetailsMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length - 1; i += 2) {
            if (keyValues[i] != null && keyValues[i + 1] != null) {
                map.put(keyValues[i].toString(), keyValues[i + 1]);
            }
        }
        return map;
    }

    private String toJsonDetails(Object obj) {
        if (obj == null) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private void logAudit(String actor, String action, String entityType, String entityId,
                          String details, UUID tenantId) {
        try {
            String actorUsername = (actor != null && !actor.isBlank()) ? actor : "system";
            auditLogRepo.save(AuditLog.builder()
                    .actorUsername(actorUsername)
                    .actorRole("PLATFORM_ADMIN")
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .details(details)
                    .tenantId(tenantId)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save audit log: {}", e.getMessage());
        }
    }

    private TenantResponse toTenantResponse(Tenant t) {
        // Get active subscription info
        var activeSub = subscriptionRepo.findFirstByTenantIdAndStatusOrderByEndDateDesc(t.getId(), "ACTIVE");

        return TenantResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .slug(t.getSlug())
                .logo(t.getLogo())
                .primaryColor(t.getPrimaryColor())
                .secondaryColor(t.getSecondaryColor())
                .contactEmail(t.getContactEmail())
                .contactPhone(t.getContactPhone())
                .address(t.getAddress())
                .status(t.getStatus())
                .trialEndsAt(t.getTrialEndsAt() != null ? t.getTrialEndsAt().toString() : null)
                .createdAt(t.getCreatedAt() != null ? t.getCreatedAt().toString() : null)
                .currentPlan(activeSub.map(s -> s.getPlan().getNameAr()).orElse(null))
                .subscriptionStatus(activeSub.map(TenantSubscription::getStatus).orElse(null))
                .subscriptionEndDate(activeSub.map(s -> s.getEndDate().toString()).orElse(null))
                .build();
    }

    private PlanResponse toPlanResponse(SubscriptionPlan p) {
        long tenantCount = subscriptionRepo.countByPlanId(p.getId());
        return PlanResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .nameAr(p.getNameAr())
                .description(p.getDescription())
                .descriptionAr(p.getDescriptionAr())
                .monthlyPrice(p.getMonthlyPrice())
                .yearlyPrice(p.getYearlyPrice())
                .active(p.isActive())
                .features(p.getFeatures())
                .sortOrder(p.getSortOrder())
                .tenantCount(tenantCount)
                .build();
    }

    private SubscriptionResponse toSubscriptionResponse(TenantSubscription s) {
        return SubscriptionResponse.builder()
                .id(s.getId())
                .tenantId(s.getTenant().getId())
                .tenantName(s.getTenant().getName())
                .planId(s.getPlan().getId())
                .planName(s.getPlan().getNameAr())
                .startDate(s.getStartDate().toString())
                .endDate(s.getEndDate().toString())
                .status(s.getStatus())
                .paidAmount(s.getPaidAmount())
                .billingCycle(s.getBillingCycle())
                .autoRenew(s.isAutoRenew())
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt() != null ? s.getCreatedAt().toString() : null)
                .build();
    }

    private InvoiceResponse toInvoiceResponse(TenantInvoice i) {
        return InvoiceResponse.builder()
                .id(i.getId())
                .tenantId(i.getTenant().getId())
                .tenantName(i.getTenant().getName())
                .invoiceNumber(i.getInvoiceNumber())
                .amount(i.getAmount())
                .taxAmount(i.getTaxAmount())
                .totalAmount(i.getTotalAmount())
                .status(i.getStatus())
                .dueDate(i.getDueDate().toString())
                .paidAt(i.getPaidAt() != null ? i.getPaidAt().toString() : null)
                .paymentMethod(i.getPaymentMethod())
                .paymentRef(i.getPaymentRef())
                .notes(i.getNotes())
                .createdAt(i.getCreatedAt() != null ? i.getCreatedAt().toString() : null)
                .build();
    }

    private AuditLogResponse toAuditLogResponse(AuditLog a) {
        String tenantName = null;
        if (a.getTenantId() != null) {
            tenantName = tenantRepo.findById(a.getTenantId()).map(Tenant::getName).orElse(null);
        }
        return AuditLogResponse.builder()
                .id(a.getId())
                .actorUsername(a.getActorUsername())
                .actorRole(a.getActorRole())
                .tenantId(a.getTenantId())
                .tenantName(tenantName)
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .details(a.getDetails())
                .ipAddress(a.getIpAddress())
                .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
                .build();
    }
}
