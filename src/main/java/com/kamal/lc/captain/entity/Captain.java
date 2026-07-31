package com.kamal.lc.captain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "captain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Captain {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kamal_code", nullable = false, unique = true, length = 50)
    private String kamalCode;

    @Column(name = "captain_type", nullable = false, length = 20)
    private String captainType;

    @Column(name = "full_name_ar", nullable = false, length = 255)
    private String fullNameAr;

    @Column(name = "full_name_en", length = 255)
    private String fullNameEn;

    @Column(length = 100)
    private String nationality;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "iqama_number", length = 50)
    private String iqamaNumber;

    @Column(name = "iqama_expiry")
    private LocalDate iqamaExpiry;

    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "passport_expiry")
    private LocalDate passportExpiry;

    @Column(name = "driving_licence_no", length = 50)
    private String drivingLicenceNo;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    /**
     * The Qiwa work permit — a different document from {@link #licenceExpiry}, which is the
     * driving licence. HR chases this one on a longer notice than the rest.
     */
    @Column(name = "work_permit_expiry")
    private LocalDate workPermitExpiry;

    @Column(name = "insurance_number", length = 50)
    private String insuranceNumber;

    @Column(name = "insurance_company", length = 255)
    private String insuranceCompany;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "uses_own_vehicle", nullable = false)
    @Builder.Default
    private boolean usesOwnVehicle = false;

    /**
     * Whether this captain earns the daily fuel allowance on long shifts. Decided per
     * captain rather than by type, so exceptions don't need a rule change.
     */
    @Column(name = "fuel_allowance_eligible", nullable = false)
    @Builder.Default
    private boolean fuelAllowanceEligible = false;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
