package com.kamal.lc.captain.expiry;

import com.kamal.lc.settings.entity.AppSetting;
import com.kamal.lc.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * How much notice each document gets before it lapses.
 *
 * <p>Read from {@code app_setting} on the same effective-date window
 * {@link com.kamal.lc.settings.service.SettingsResolver} uses, so a window scheduled to
 * change next month does not start applying today.
 *
 * <p>These keys deliberately stay out of {@code EffectiveSettings}: that snapshot is the set
 * of pay rates a payroll run was computed with, and its {@code require()} would reject every
 * date before V20 seeded these rows.
 *
 * <p>A key that is absent or unparseable falls back to the document's default rather than
 * throwing. A misconfigured window is a smaller problem than a whole screen of alerts
 * disappearing behind an error.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class DocumentAlertPolicy {

    private final AppSettingRepository settingRepo;

    /** The alert window of every document, as configured on {@code asOf}. */
    public Map<DocumentKind, Integer> alertDays(LocalDate asOf) {
        Map<String, String> values = new java.util.HashMap<>();
        for (AppSetting setting : settingRepo.findEffectiveOn(asOf)) {
            values.put(setting.getSettingKey(), setting.getValue());
        }

        Map<DocumentKind, Integer> days = new EnumMap<>(DocumentKind.class);
        for (DocumentKind kind : DocumentKind.values()) {
            days.put(kind, parse(kind, values.get(kind.getSettingKey())));
        }
        return days;
    }

    private int parse(DocumentKind kind, String raw) {
        if (raw == null || raw.isBlank()) {
            return kind.getDefaultAlertDays();
        }
        try {
            int days = Integer.parseInt(raw.trim());
            return days >= 0 ? days : kind.getDefaultAlertDays();
        } catch (NumberFormatException e) {
            log.warn("Alert window {} is not a whole number of days ({}); using {}",
                    kind.getSettingKey(), raw, kind.getDefaultAlertDays());
            return kind.getDefaultAlertDays();
        }
    }
}
