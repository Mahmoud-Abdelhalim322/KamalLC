package com.kamal.lc.captain.expiry;

import java.time.LocalDate;

/**
 * One document's standing, as of the day it was judged.
 *
 * @param kind      which document
 * @param number    its recorded number, or {@code null}
 * @param expiry    its recorded expiry, or {@code null} when the status is
 *                  {@link DocumentStatus#MISSING}
 * @param status    the verdict
 * @param daysLeft  days until expiry — negative once past, {@code null} with no date
 */
public record DocumentAlert(
        DocumentKind kind,
        String number,
        LocalDate expiry,
        DocumentStatus status,
        Long daysLeft) {

    public boolean isAlert() {
        return status.isAlert();
    }
}
