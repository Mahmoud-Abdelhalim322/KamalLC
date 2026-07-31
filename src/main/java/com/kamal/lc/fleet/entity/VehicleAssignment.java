package com.kamal.lc.fleet.entity;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.payroll.entity.CaptainAdjustment;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicle_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "captain_id", nullable = false)
    private Captain captain;

    /** The captain who should receive the vehicle after it is returned — optional. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_captain_id")
    private Captain nextCaptain;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "expected_return_at", nullable = false)
    private OffsetDateTime expectedReturnAt;

    @Column(name = "actual_return_at")
    private OffsetDateTime actualReturnAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    /** Links to the automatic deduction created when the captain returned late. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "late_deduction_id")
    private CaptainAdjustment lateDeduction;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
