package com.kamal.lc.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "asset_category_field_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetCategoryFieldTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private AssetCategory category;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "label_ar", nullable = false, length = 100)
    private String labelAr;

    @Column(name = "field_order", nullable = false)
    @Builder.Default
    private int fieldOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = false;
}
