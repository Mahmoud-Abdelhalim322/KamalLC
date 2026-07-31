package com.kamal.lc.settings.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One versioned setting value.
 *
 * <p>Rows are never edited in place: changing a rate closes the current row with an
 * {@code effectiveTo} and inserts a new one. That is what lets an approved payroll
 * keep the rates it was computed with.
 */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_group", length = 50)
    private String settingGroup;

    @Column(name = "label_ar", length = 255)
    private String labelAr;

    @Column(nullable = false, length = 255)
    private String value;

    @Column(name = "value_type", length = 30)
    private String valueType;

    @Column(name = "unit_label", length = 50)
    private String unitLabel;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** NULL means this row is the currently active value. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
