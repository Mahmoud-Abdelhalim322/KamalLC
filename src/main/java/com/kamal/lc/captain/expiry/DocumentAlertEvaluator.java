package com.kamal.lc.captain.expiry;

import com.kamal.lc.captain.entity.Captain;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a captain's recorded dates into the same verdicts HR writes by hand.
 *
 * <p>This is the only place the rule lives. The dashboard counters, the badge in the captains
 * table and the per-document rows on the detail screen all come through here, so a change to
 * what counts as an alert cannot land in one screen and miss another.
 *
 * <p>Evaluation runs in Java over the loaded captains rather than as a counting query. The
 * comparison would have to be restated in JPQL against five different cut-off dates, which
 * would be a second copy of this rule drifting from the first as soon as either is touched —
 * and the population here is measured in hundreds.
 */
@Service
@RequiredArgsConstructor
public class DocumentAlertEvaluator {

    /** Only an active captain is worth chasing; nothing gets renewed for a leaver. */
    private static final String ACTIVE = "ACTIVE";

    private final DocumentAlertPolicy policy;

    /** Every document of one captain, judged as of {@code today}. */
    public List<DocumentAlert> evaluate(Captain captain, LocalDate today) {
        return evaluate(captain, today, policy.alertDays(today));
    }

    /**
     * The same judgement with a pre-read policy, for callers walking a whole list — the
     * settings are read once for the batch rather than once per captain.
     */
    public List<DocumentAlert> evaluate(Captain captain, LocalDate today,
                                        Map<DocumentKind, Integer> alertDays) {
        List<DocumentAlert> alerts = new ArrayList<>(DocumentKind.values().length);
        for (DocumentKind kind : DocumentKind.values()) {
            LocalDate expiry = kind.expiryOf(captain);
            alerts.add(new DocumentAlert(
                    kind,
                    kind.numberOf(captain),
                    expiry,
                    statusOf(expiry, today, alertDays.get(kind)),
                    expiry != null ? ChronoUnit.DAYS.between(today, expiry) : null));
        }
        return alerts;
    }

    /** The alert window read once, for evaluating a list of captains. */
    public Map<DocumentKind, Integer> alertDays(LocalDate today) {
        return policy.alertDays(today);
    }

    /** The most serious thing any of these documents says. */
    public DocumentStatus worst(List<DocumentAlert> alerts) {
        DocumentStatus worst = DocumentStatus.VALID;
        for (DocumentAlert alert : alerts) {
            if (alert.status().compareTo(worst) > 0) {
                worst = alert.status();
            }
        }
        return worst;
    }

    public long alertCount(List<DocumentAlert> alerts) {
        return alerts.stream().filter(DocumentAlert::isAlert).count();
    }

    /** Whether a captain's documents are judged at all — a leaver's are not. */
    public boolean isTracked(Captain captain) {
        return ACTIVE.equalsIgnoreCase(captain.getStatus());
    }

    private DocumentStatus statusOf(LocalDate expiry, LocalDate today, int alertDays) {
        if (expiry == null) {
            return DocumentStatus.MISSING;
        }
        if (expiry.isBefore(today)) {
            return DocumentStatus.EXPIRED;
        }
        // Expiring on the last day of the window still counts as expiring, so a document is
        // never valid one day and expired the next without a warning in between.
        return expiry.isAfter(today.plusDays(alertDays))
                ? DocumentStatus.VALID
                : DocumentStatus.EXPIRING;
    }
}
