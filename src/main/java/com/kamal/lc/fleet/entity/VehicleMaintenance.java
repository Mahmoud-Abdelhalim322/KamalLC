package com.kamal.lc.fleet.entity;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A maintenance order on a vehicle — opened when work begins, closed when
 * the cost is known. Optionally charges the responsible captain.
 */
@Entity
@Table(name = "vehicle_maintenance")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class VehicleMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "IN_PROGRESS";   // IN_PROGRESS | COMPLETED | CANCELLED

    @Column(name = "workshop_name")
    private String workshopName;

    @Column(name = "fault_reason", nullable = false, columnDefinition = "TEXT")
    private String faultReason;

    /** Who pays: COMPANY or CAPTAIN. */
    @Column(name = "cost_bearer", nullable = false, length = 20)
    @Builder.Default
    private String costBearer = "COMPANY";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captain_id")
    private Captain captain;

    /** Filled when status moves to COMPLETED. */
    @Column(name = "total_cost", precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "odometer_on_close")
    private Integer odometerOnClose;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deduction_id")
    private CaptainAdjustment deduction;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "opened_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime openedAt = OffsetDateTime.now();

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @OneToMany(mappedBy = "maintenance", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaintenancePart> parts = new ArrayList<>();

    public void addPart(MaintenancePart part) {
        part.setMaintenance(this);
        parts.add(part);
    }

    public void replaceParts(List<MaintenancePart> newParts) {
        parts.clear();
        newParts.forEach(this::addPart);
    }
}
