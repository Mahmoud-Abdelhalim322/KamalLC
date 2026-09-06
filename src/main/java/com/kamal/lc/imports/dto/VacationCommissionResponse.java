package com.kamal.lc.imports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacationCommissionResponse {
    private UUID id;
    private String daysCode;
    private UUID supplierId;
    private String supplierName;
    private String externalCaptainId;
    private UUID captainId;
    private String captainCode;
    private String captainName;
    private String zoneName;
    private String flag;
    private String note;
    private BigDecimal amount;
    private LocalDate vacationDate;
    private UUID importBatchId;
    private OffsetDateTime createdAt;
}
