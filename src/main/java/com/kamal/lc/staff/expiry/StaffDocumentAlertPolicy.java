package com.kamal.lc.staff.expiry;

import com.kamal.lc.settings.entity.AppSetting;
import com.kamal.lc.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class StaffDocumentAlertPolicy {

    private final AppSettingRepository settingRepo;

    public Map<StaffDocumentKind, Integer> alertDays(LocalDate asOf) {
        Map<String, String> values = new java.util.HashMap<>();
        for (AppSetting setting : settingRepo.findEffectiveOn(asOf)) {
            values.put(setting.getSettingKey(), setting.getValue());
        }

        Map<StaffDocumentKind, Integer> days = new EnumMap<>(StaffDocumentKind.class);
        for (StaffDocumentKind kind : StaffDocumentKind.values()) {
            days.put(kind, parse(kind, values.get(kind.getSettingKey())));
        }
        return days;
    }

    private int parse(StaffDocumentKind kind, String raw) {
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
