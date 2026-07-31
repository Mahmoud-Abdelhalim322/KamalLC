package com.kamal.lc.fleet.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A replacement part recorded against a {@link VehicleMaintenance} order.
 */
@Entity
@Table(name = "maintenance_part")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MaintenancePart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maintenance_id", nullable = false)
    private VehicleMaintenance maintenance;

    @Column(name = "part_name", nullable = false)
    private String partName;

    @Column(name = "part_cost", precision = 12, scale = 2)
    private BigDecimal partCost;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
