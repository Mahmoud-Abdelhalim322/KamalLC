package com.kamal.lc.staff.expiry;

import com.kamal.lc.captain.expiry.DocumentStatus;
import com.kamal.lc.staff.entity.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StaffDocumentAlertEvaluator {

    private static final String ACTIVE = "ACTIVE";

    private final StaffDocumentAlertPolicy policy;

    public List<StaffDocumentAlert> evaluate(Staff staff, LocalDate today) {
        return evaluate(staff, today, policy.alertDays(today));
    }

    public List<StaffDocumentAlert> evaluate(Staff staff, LocalDate today,
                                             Map<StaffDocumentKind, Integer> alertDays) {
        List<StaffDocumentAlert> alerts = new ArrayList<>();
        for (StaffDocumentKind kind : StaffDocumentKind.values()) {
            if (!kind.isTrackedFor(staff)) {
                continue;
            }
            LocalDate expiry = kind.expiryOf(staff);
            alerts.add(new StaffDocumentAlert(
                    kind,
                    kind.numberOf(staff),
                    expiry,
                    statusOf(expiry, today, alertDays.get(kind)),
                    expiry != null ? ChronoUnit.DAYS.between(today, expiry) : null));
        }
        return alerts;
    }

    public Map<StaffDocumentKind, Integer> alertDays(LocalDate today) {
        return policy.alertDays(today);
    }

    public DocumentStatus worst(List<StaffDocumentAlert> alerts) {
        DocumentStatus worst = DocumentStatus.VALID;
        for (StaffDocumentAlert alert : alerts) {
            if (alert.status().compareTo(worst) > 0) {
                worst = alert.status();
            }
        }
        return worst;
    }

    public long alertCount(List<StaffDocumentAlert> alerts) {
        return alerts.stream().filter(StaffDocumentAlert::isAlert).count();
    }

    public boolean isTracked(Staff staff) {
        return ACTIVE.equalsIgnoreCase(staff.getStatus());
    }

    private DocumentStatus statusOf(LocalDate expiry, LocalDate today, int alertDays) {
        if (expiry == null) {
            return DocumentStatus.MISSING;
        }
        if (expiry.isBefore(today)) {
            return DocumentStatus.EXPIRED;
        }
        return expiry.isAfter(today.plusDays(alertDays))
                ? DocumentStatus.VALID
                : DocumentStatus.EXPIRING;
    }
}
