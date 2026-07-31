package com.kamal.lc.captain.expiry;

import lombok.Getter;

/**
 * How a single document stands today.
 *
 * <p>Declared from harmless to most serious so {@link Enum#compareTo} picks the one worth
 * showing when a captain carries several — the row badge says the worst thing about him.
 *
 * <p>{@link #MISSING} has no column in the HR sheet, which marks a blank date منتهى by hand.
 * It is kept apart because an unrecorded date is a data-entry gap, not a renewal that is due:
 * folding the two together hides which of the two someone has to go and fix.
 */
@Getter
public enum DocumentStatus {

    VALID("ساري"),
    EXPIRING("على وشك الانتهاء"),
    EXPIRED("منتهى"),
    MISSING("غير مسجل");

    private final String labelAr;

    DocumentStatus(String labelAr) {
        this.labelAr = labelAr;
    }

    /** Whether this status is worth counting on the dashboard or badging in a row. */
    public boolean isAlert() {
        return this != VALID;
    }
}
