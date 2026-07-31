package com.kamal.lc.settings.repository;

import com.kamal.lc.settings.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppSettingRepository extends JpaRepository<AppSetting, UUID> {

    /**
     * Every setting value in force on {@code asOf}. A row applies when the date falls
     * inside its [effectiveFrom, effectiveTo] window, with a NULL end meaning open-ended.
     */
    @Query("SELECT s FROM AppSetting s WHERE s.effectiveFrom <= :asOf "
            + "AND (s.effectiveTo IS NULL OR s.effectiveTo >= :asOf)")
    List<AppSetting> findEffectiveOn(@Param("asOf") LocalDate asOf);

    @Query("SELECT s FROM AppSetting s WHERE s.settingKey = :key AND s.effectiveTo IS NULL")
    Optional<AppSetting> findCurrent(@Param("key") String key);

    List<AppSetting> findBySettingKeyOrderByEffectiveFromDesc(String settingKey);

    @Query("SELECT s FROM AppSetting s WHERE s.effectiveTo IS NULL ORDER BY s.settingGroup, s.settingKey")
    List<AppSetting> findAllCurrent();
}
