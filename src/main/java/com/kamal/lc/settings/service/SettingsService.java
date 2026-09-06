package com.kamal.lc.settings.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.security.TenantContext;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.settings.dto.SettingResponse;
import com.kamal.lc.settings.entity.AppSetting;
import com.kamal.lc.settings.repository.AppSettingRepository;
import com.kamal.lc.tenant.entity.Tenant;
import com.kamal.lc.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsService {

    private final AppSettingRepository repository;
    private final SettingsResolver resolver;
    private final TenantRepository tenantRepo;
    private final com.kamal.lc.common.service.FileStorageService fileStorage;

    /** All currently active settings, grouped by {@code setting_group} for the settings screen. */
    @Transactional
    public Map<String, List<SettingResponse>> getCurrentGrouped() {
        List<AppSetting> current = repository.findAllCurrent();
        if (current.isEmpty()) {
            UUID tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                Tenant tenant = tenantRepo.findById(tenantId).orElse(null);
                if (tenant != null) {
                    current = initializeDefaultSettingsForTenant(tenant);
                }
            }
        }
        Map<String, List<SettingResponse>> grouped = new LinkedHashMap<>();
        for (AppSetting setting : current) {
            String group = setting.getSettingGroup() != null ? setting.getSettingGroup() : "OTHER";
            grouped.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(toResponse(setting));
        }
        return grouped;
    }

    /** Copies default settings from default tenant template for a newly created tenant or a tenant with 0 settings. */
    @Transactional
    public List<AppSetting> initializeDefaultSettingsForTenant(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) return List.of();

        if (repository.countByTenantIdRaw(tenant.getId()) > 0) {
            return repository.findActiveSettingsByTenantIdRaw(tenant.getId());
        }

        List<AppSetting> defaults = repository.findActiveSettingsByTenantIdRaw(TenantEntityListener.DEFAULT_TENANT_ID);
        if (defaults.isEmpty()) {
            return List.of();
        }

        List<AppSetting> newSettings = defaults.stream()
                .map(d -> AppSetting.builder()
                        .settingKey(d.getSettingKey())
                        .settingGroup(d.getSettingGroup())
                        .labelAr(d.getLabelAr())
                        .value(d.getValue())
                        .valueType(d.getValueType())
                        .unitLabel(d.getUnitLabel())
                        .effectiveFrom(d.getEffectiveFrom())
                        .effectiveTo(null)
                        .tenant(tenant)
                        .build())
                .toList();

        List<AppSetting> saved = repository.saveAll(newSettings);
        resolver.invalidate();
        return saved;
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

        if (newValue.trim().equals(current.getValue())) {
            return toResponse(current);
        }
        validate(current.getValueType(), newValue);

        if (from.equals(current.getEffectiveFrom())) {
            current.setValue(newValue.trim());
            AppSetting saved = repository.save(current);
            resolver.invalidate();
            return toResponse(saved);
        }

        if (from.isBefore(current.getEffectiveFrom())) {
            throw new BusinessException("INVALID_DATE",
                    "تاريخ السريان يجب أن يكون بعد أو يطابق " + current.getEffectiveFrom()
                            + " (تاريخ سريان القيمة الحالية)");
        }

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

    @Transactional
    public SettingResponse uploadLogo(org.springframework.web.multipart.MultipartFile file) {
        String path = fileStorage.store(file, "logos");
        String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        String logoUrl = "/api/settings/logo/file/" + filename;

        UUID tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            tenantRepo.findById(tenantId).ifPresent(t -> {
                t.setLogo(logoUrl);
                tenantRepo.save(t);
            });
        }

        return update("THEME_LOGO", logoUrl, LocalDate.now());
    }

    public org.springframework.core.io.Resource loadLogo(String filename) {
        try {
            return fileStorage.load("logos/" + filename);
        } catch (Exception e) {
            return new org.springframework.core.io.ClassPathResource("branding/logo-ksclc.png");
        }
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
                default -> { /* free text, color, string */ }
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
