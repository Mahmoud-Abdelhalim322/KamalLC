package com.kamal.lc.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {

    @NotBlank(message = "رقم اللوحة مطلوب")
    private String plateNumber;

    private String ownershipType;  // OWNED | RENTED
    private String brand;
    private String model;
    private Integer year;
    private String color;
    private Integer odometer;
    private LocalDate inspectionDate;
    private String status;

    // Rental-agent fields
    private String rentalAgentName;
    private LocalDate rentalStartDate;
    private LocalDate rentalEndDate;

    private String notes;
}
