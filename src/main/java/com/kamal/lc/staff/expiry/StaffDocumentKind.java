package com.kamal.lc.staff.expiry;

import com.kamal.lc.staff.entity.Staff;
import lombok.Getter;

import java.time.LocalDate;
import java.util.function.Function;

@Getter
public enum StaffDocumentKind {

    IQAMA("الإقامة", "IQAMA_ALERT_DAYS", 30,
            Staff::getIqamaExpiry, Staff::getIqamaNumber, s -> true),

    WORK_PERMIT("رخصة العمل (قوى)", "WORK_PERMIT_ALERT_DAYS", 60,
            Staff::getWorkPermitExpiry, s -> null, s -> true),

    INSURANCE("التأمين الطبي", "INSURANCE_ALERT_DAYS", 30,
            Staff::getInsuranceExpiry, Staff::getInsuranceNumber, s -> true),

    PASSPORT("الجواز", "PASSPORT_ALERT_DAYS", 30,
            Staff::getPassportExpiry, Staff::getPassportNumber, s -> true),

    QIWA_CONTRACT("عقد قوى", "QIWA_CONTRACT_ALERT_DAYS", 30,
            Staff::getQiwaContractEnd, Staff::getQiwaContractNumber, s -> true),

    DRIVING_LICENCE("رخصة القيادة", "LICENCE_ALERT_DAYS", 30,
            Staff::getLicenceExpiry, s -> null, s -> s.isHasLicence() || s.getLicenceExpiry() != null);

    private final String labelAr;
    private final String settingKey;
    private final int defaultAlertDays;
    private final Function<Staff, LocalDate> expiry;
    private final Function<Staff, String> number;
    private final Function<Staff, Boolean> trackedCondition;

    StaffDocumentKind(String labelAr, String settingKey, int defaultAlertDays,
                      Function<Staff, LocalDate> expiry, Function<Staff, String> number,
                      Function<Staff, Boolean> trackedCondition) {
        this.labelAr = labelAr;
        this.settingKey = settingKey;
        this.defaultAlertDays = defaultAlertDays;
        this.expiry = expiry;
        this.number = number;
        this.trackedCondition = trackedCondition;
    }

    public LocalDate expiryOf(Staff staff) {
        return expiry.apply(staff);
    }

    public String numberOf(Staff staff) {
        return number.apply(staff);
    }

    public boolean isTrackedFor(Staff staff) {
        return trackedCondition.apply(staff);
    }
}
