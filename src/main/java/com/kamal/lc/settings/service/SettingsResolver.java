package com.kamal.lc.settings.service;

import com.kamal.lc.settings.entity.AppSetting;
import com.kamal.lc.settings.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the rate set in force on a given date.
 *
 * <p>Every shift and payroll calculation goes through here, so that changing a rate
 * today cannot silently restate last month's numbers. Results are cached per date;
 * {@link SettingsService} clears the cache whenever a setting is versioned.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsResolver {

    private final AppSettingRepository repository;

    private final Map<LocalDate, EffectiveSettings> cache = new ConcurrentHashMap<>();

    public EffectiveSettings resolve(LocalDate asOf) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        return cache.computeIfAbsent(date, this::load);
    }

    /** Called by {@link SettingsService} after any change so stale rates cannot leak out. */
    public void invalidate() {
        cache.clear();
    }

    private EffectiveSettings load(LocalDate asOf) {
        List<AppSetting> rows = repository.findEffectiveOn(asOf);

        Map<String, String> values = new LinkedHashMap<>();
        // If two rows overlap for a key (shouldn't happen, but data is data), the one
        // that started most recently is the one that was meant to apply.
        rows.stream()
                .sorted(Comparator.comparing(AppSetting::getEffectiveFrom))
                .forEach(s -> values.put(s.getSettingKey(), s.getValue()));

        LocalDate version = rows.stream()
                .map(AppSetting::getEffectiveFrom)
                .max(Comparator.naturalOrder())
                .orElse(asOf);

        return EffectiveSettings.of(values, version);
    }
}
