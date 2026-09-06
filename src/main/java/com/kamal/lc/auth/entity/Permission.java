package com.kamal.lc.auth.entity;

/**
 * All available system permissions. Each one gates a feature area's view or manage access.
 * <ul>
 *   <li>{@code PLATFORM_ADMIN} has ALL permissions (including platform-level ones).</li>
 *   <li>{@code ADMIN} (company admin) has all tenant-level permissions (not PLATFORM_*).</li>
 *   <li>Manager-level users get their permissions through their {@link JobTitle}.</li>
 * </ul>
 */
public enum Permission {

    // ---- Tenant-level permissions (available to company users) ----
    MEMBERS_VIEW("عرض بيانات الأعضاء"),
    MEMBERS_MANAGE("إضافة/تعديل الأعضاء"),
    FLEET_VIEW("عرض أسطول السيارات"),
    FLEET_MANAGE("إدارة الأسطول"),
    SHIFTS_VIEW("عرض الشيفتات"),
    SHIFTS_MANAGE("إدارة الشيفتات"),
    PAYROLL_VIEW("عرض الرواتب"),
    PAYROLL_MANAGE("اعتماد/تعديل الرواتب"),
    REPORTS_VIEW("عرض التقارير"),
    INVENTORY_VIEW("عرض المخزن"),
    INVENTORY_MANAGE("إدارة المخزن"),
    CUSTODY_VIEW("عرض سجل العهد"),
    LOANS_VIEW("عرض السُلف"),
    LOANS_MANAGE("إدارة السُلف"),
    SETTINGS_MANAGE("إدارة الإعدادات"),
    USERS_MANAGE("إدارة حسابات المستخدمين"),
    IMPORTS_MANAGE("استيراد البيانات"),

    // ---- Platform-level permissions (platform admin only) ----
    PLATFORM_TENANTS_MANAGE("إدارة الشركات"),
    PLATFORM_SUBSCRIPTIONS_MANAGE("إدارة الاشتراكات"),
    PLATFORM_PLANS_MANAGE("إدارة خطط الاشتراك"),
    PLATFORM_REPORTS("تقارير المنصة"),
    PLATFORM_USERS_MANAGE("إدارة مستخدمي المنصة"),
    PLATFORM_INVOICES_MANAGE("إدارة الفواتير"),
    PLATFORM_AUDIT_VIEW("عرض سجل المراجعة");

    private final String labelAr;

    Permission(String labelAr) {
        this.labelAr = labelAr;
    }

    public String getLabelAr() {
        return labelAr;
    }

    /** Whether this is a platform-level permission (not for regular tenant users). */
    public boolean isPlatformLevel() {
        return name().startsWith("PLATFORM_");
    }
}
