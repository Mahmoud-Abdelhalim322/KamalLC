package com.kamal.lc.fleet.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleAssignmentResponse {
    private UUID id;
    private UUID vehicleId;
    private String vehiclePlate;
    private String vehicleBrand;
    private String vehicleModel;

    private UUID captainId;
    private String captainName;
    private String captainCode;

    private UUID nextCaptainId;
    private String nextCaptainName;
    private String nextCaptainCode;

    private String assignedAt;
    private String expectedReturnAt;
    private String actualReturnAt;
    private String status;
    private boolean late;

    private UUID lateDeductionId;
    private String notes;
}
