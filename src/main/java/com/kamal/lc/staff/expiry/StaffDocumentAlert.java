package com.kamal.lc.staff.expiry;

import com.kamal.lc.captain.expiry.DocumentStatus;

import java.time.LocalDate;

public record StaffDocumentAlert(
        StaffDocumentKind kind,
        String number,
        LocalDate expiry,
        DocumentStatus status,
        Long daysLeft
) {
    public boolean isAlert() {
        return status != null && status.isAlert();
    }
}
