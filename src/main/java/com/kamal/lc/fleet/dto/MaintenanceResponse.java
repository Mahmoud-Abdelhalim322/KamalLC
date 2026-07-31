package com.kamal.lc.fleet.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MaintenanceResponse {
    private UUID id;
    private UUID vehicleId;
    private String vehiclePlate;
    private String vehicleBrand;
    private String vehicleModel;
    private String status;
    private String workshopName;
    private String faultReason;
    private String costBearer;
    private UUID captainId;
    private String captainName;
    private String captainCode;
    private BigDecimal totalCost;
    private Integer odometerOnClose;
    private UUID deductionId;
    private String notes;
    private String openedAt;
    private String closedAt;
    private String createdBy;
    private List<PartResponse> parts;

    @Data
    @Builder
    public static class PartResponse {
        private UUID id;
        private String partName;
        private BigDecimal partCost;
        private int sortOrder;
    }
}
