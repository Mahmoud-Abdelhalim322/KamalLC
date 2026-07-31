package com.kamal.lc.fleet.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class MaintenanceOpenRequest {
    private UUID vehicleId;
    private String workshopName;
    private String faultReason;
    private String costBearer;   // COMPANY | CAPTAIN
    private UUID captainId;      // required when costBearer = CAPTAIN
    private List<PartRequest> parts;
    private String notes;

    @Data
    public static class PartRequest {
        private String partName;
        private BigDecimal partCost;
    }
}
