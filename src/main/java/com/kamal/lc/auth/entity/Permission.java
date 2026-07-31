package com.kamal.lc.auth.entity;

/**
 * All available system permissions. Each one gates a feature area's view or manage access.
 * The SUPER_ADMIN role implicitly has ALL permissions — this enum is only consulted for
 * manager-level users whose access is defined through their {@link JobTitle}.
 */
public enum Permission {

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
    SETTINGS_MANAGE("إدارة الإعدادات"),
    USERS_MANAGE("إدارة حسابات المستخدمين"),
    IMPORTS_MANAGE("استيراد البيانات");

    private final String labelAr;

    Permission(String labelAr) {
        this.labelAr = labelAr;
    }

    public String getLabelAr() {
        return labelAr;
    }
}
