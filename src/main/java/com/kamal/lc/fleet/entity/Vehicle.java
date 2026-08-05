package com.kamal.lc.fleet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plate_number", nullable = false, unique = true, length = 30)
    private String plateNumber;

    @Column(name = "ownership_type", nullable = false, length = 20)
    @Builder.Default
    private String ownershipType = "OWNED";

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    private Integer year;

    @Column(length = 50)
    private String color;

    private Integer odometer;

    @Column(name = "inspection_date")
    private LocalDate inspectionDate;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "AVAILABLE";

    // Rental-agent fields (populated only when ownershipType = 'RENTED')
    @Column(name = "rental_agent_name", length = 255)
    private String rentalAgentName;

    @Column(name = "rental_start_date")
    private LocalDate rentalStartDate;

    @Column(name = "rental_end_date")
    private LocalDate rentalEndDate;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(length = 100)
    private String vin;

    @Column(name = "registration_expiry_date")
    private LocalDate registrationExpiryDate;

    @Column(name = "operating_card_number", length = 100)
    private String operatingCardNumber;

    @Column(name = "insurance_company", length = 100)
    private String insuranceCompany;

    @Column(name = "inspection_expiry_date")
    private LocalDate inspectionExpiryDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
