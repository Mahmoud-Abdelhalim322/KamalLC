package com.kamal.lc.settings.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.settings.dto.SettingResponse;
import com.kamal.lc.settings.entity.AppSetting;
import com.kamal.lc.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsService {

    private final AppSettingRepository repository;
    private final SettingsResolver resolver;

    /** All currently active settings, grouped by {@code setting_group} for the settings screen. */
    public Map<String, List<SettingResponse>> getCurrentGrouped() {
        Map<String, List<SettingResponse>> grouped = new LinkedHashMap<>();
        for (AppSetting setting : repository.findAllCurrent()) {
            String group = setting.getSettingGroup() != null ? setting.getSettingGroup() : "OTHER";
            grouped.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(toResponse(setting));
        }
        return grouped;
    }

    public List<SettingResponse> getHistory(String key) {
        return repository.findBySettingKeyOrderByEffectiveFromDesc(key).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Versions a setting: closes the active row the day before {@code effectiveFrom} and
     * opens a new one. Never updates in place — an approved payroll must keep reading the
     * rate it was computed with.
     */
    @Transactional
    public SettingResponse update(String key, String newValue, LocalDate effectiveFrom) {
        AppSetting current = repository.findCurrent(key)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الإعداد غير موجود: " + key));

        LocalDate from = effectiveFrom != null ? effectiveFrom : LocalDate.now();

        if (!from.isAfter(current.getEffectiveFrom())) {
            throw new BusinessException("INVALID_DATE",
                    "تاريخ السريان يجب أن يكون بعد " + current.getEffectiveFrom()
                            + " (تاريخ سريان القيمة الحالية)");
        }
        if (newValue.equals(current.getValue())) {
            throw new BusinessException("NO_CHANGE", "القيمة الجديدة مطابقة للقيمة الحالية");
        }
        validate(current.getValueType(), newValue);

        current.setEffectiveTo(from.minusDays(1));
        repository.save(current);

        AppSetting next = repository.save(AppSetting.builder()
                .settingKey(current.getSettingKey())
                .settingGroup(current.getSettingGroup())
                .labelAr(current.getLabelAr())
                .value(newValue.trim())
                .valueType(current.getValueType())
                .unitLabel(current.getUnitLabel())
                .effectiveFrom(from)
                .build());

        resolver.invalidate();
        return toResponse(next);
    }

    private void validate(String valueType, String value) {
        if (valueType == null) return;
        try {
            switch (valueType) {
                case "INTEGER" -> Integer.parseInt(value.trim());
                case "DECIMAL", "CURRENCY", "PERCENTAGE" -> new java.math.BigDecimal(value.trim());
                case "BOOLEAN" -> {
                    String v = value.trim().toLowerCase();
                    if (!v.equals("true") && !v.equals("false")) {
                        throw new NumberFormatException();
                    }
                }
                default -> { /* free text */ }
            }
        } catch (NumberFormatException e) {
            throw new BusinessException("INVALID_VALUE", "القيمة غير صالحة لهذا النوع (" + valueType + ")");
        }
    }

    private SettingResponse toResponse(AppSetting s) {
        return SettingResponse.builder()
                .id(s.getId())
                .settingKey(s.getSettingKey())
                .settingGroup(s.getSettingGroup())
                .labelAr(s.getLabelAr())
                .value(s.getValue())
                .valueType(s.getValueType())
                .unitLabel(s.getUnitLabel())
                .effectiveFrom(s.getEffectiveFrom().toString())
                .effectiveTo(s.getEffectiveTo() != null ? s.getEffectiveTo().toString() : null)
                .updatedAt(s.getUpdatedAt() != null ? s.getUpdatedAt().toString() : null)
                .build();
    }
}
