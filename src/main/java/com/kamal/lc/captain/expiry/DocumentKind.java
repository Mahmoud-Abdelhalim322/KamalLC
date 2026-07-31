package com.kamal.lc.captain.expiry;

import com.kamal.lc.captain.entity.Captain;
import lombok.Getter;

import java.time.LocalDate;
import java.util.function.Function;

/**
 * The captain documents whose expiry is tracked, each carrying everything needed to judge
 * it: where its date lives, what the setting key for its alert window is called, and the
 * window to fall back on when that setting cannot be read.
 *
 * <p>Adding a seventh document later is one entry here — nothing downstream enumerates them.
 */
@Getter
public enum DocumentKind {

    IQAMA("الإقامة", "IQAMA_ALERT_DAYS", 30,
            Captain::getIqamaExpiry, Captain::getIqamaNumber),

    /** The Qiwa work permit. HR chases it 60 days out, twice the notice of the rest. */
    WORK_PERMIT("رخصة العمل (قوى)", "WORK_PERMIT_ALERT_DAYS", 60,
            Captain::getWorkPermitExpiry, c -> null),

    INSURANCE("التأمين الطبي", "INSURANCE_ALERT_DAYS", 30,
            Captain::getInsuranceExpiry, Captain::getInsuranceNumber),

    PASSPORT("الجواز", "PASSPORT_ALERT_DAYS", 30,
            Captain::getPassportExpiry, Captain::getPassportNumber),

    DRIVING_LICENCE("رخصة القيادة", "LICENCE_ALERT_DAYS", 30,
            Captain::getLicenceExpiry, Captain::getDrivingLicenceNo);

    private final String labelAr;
    private final String settingKey;
    private final int defaultAlertDays;
    private final Function<Captain, LocalDate> expiry;

    /** The document's own number, where one is recorded — the work permit has none. */
    private final Function<Captain, String> number;

    DocumentKind(String labelAr, String settingKey, int defaultAlertDays,
                 Function<Captain, LocalDate> expiry, Function<Captain, String> number) {
        this.labelAr = labelAr;
        this.settingKey = settingKey;
        this.defaultAlertDays = defaultAlertDays;
        this.expiry = expiry;
        this.number = number;
    }

    public LocalDate expiryOf(Captain captain) {
        return expiry.apply(captain);
    }

    public String numberOf(Captain captain) {
        return number.apply(captain);
    }
}
