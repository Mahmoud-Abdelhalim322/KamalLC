package com.kamal.lc.fleet.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {
    private UUID id;
    private String plateNumber;
    private String ownershipType;
    private String brand;
    private String model;
    private Integer year;
    private String color;
    private Integer odometer;
    private String inspectionDate;
    private String status;

    // Rental fields
    private String rentalAgentName;
    private String rentalStartDate;
    private String rentalEndDate;

    // Extended document fields
    private String registrationNumber;
    private String vin;
    private String registrationExpiryDate;
    private String operatingCardNumber;
    private String insuranceCompany;
    private String inspectionExpiryDate;

    // Document Alert Status
    private String documentAlertStatus; // VALID | EXPIRING | EXPIRED
    private java.util.List<String> documentAlerts;

    // Current assignment info (denormalised for list view)
    private String currentCaptainName;
    private String currentCaptainCode;
    private UUID currentCaptainId;
    private String assignedSince;

    private String notes;
}
