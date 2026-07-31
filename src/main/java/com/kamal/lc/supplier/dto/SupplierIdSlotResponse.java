package com.kamal.lc.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierIdSlotResponse {
    private UUID id;
    private String externalId;
    private UUID supplierId;
    private String supplierName;
    private String supplierCode;
    private String supplierColor;
    private boolean active;
    private String currentCaptainName;
    private String currentCaptainCode;
    private UUID currentCaptainId;
    private String assignedSince;
}
