package com.kamal.lc.fleet.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MaintenanceCloseRequest {
    private BigDecimal totalCost;
    private Integer odometerOnClose;
    private List<MaintenanceOpenRequest.PartRequest> parts;  // can update parts on close
    private String notes;
}
