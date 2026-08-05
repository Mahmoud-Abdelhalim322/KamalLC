package com.kamal.lc.supplier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class SupplierRevenueDtos {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SupplierAdjustmentRequest {
        private UUID supplierId;
        /** "ADDITION" or "DEDUCTION" */
        private String type;
        /** Category / Item description e.g. "خطأ في حساب الطلبات", "تعديل تسعيرة" */
        private String category;
        private BigDecimal amount;
        private LocalDate adjustmentDate;
        private String notes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SupplierAdjustmentResponse {
        private UUID id;
        private UUID supplierId;
        private String supplierName;
        private String type;
        private String category;
        private BigDecimal amount;
        private LocalDate adjustmentDate;
        private String notes;
        private String createdBy;
        private OffsetDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SupplierRowSummary {
        private UUID supplierId;
        private String supplierCode;
        private String supplierName;
        private String color;
        private int totalShifts;
        private int totalDelivered;
        private BigDecimal workingHours;
        private BigDecimal baseRevenue;
        private BigDecimal extraRevenue;
        private BigDecimal additionsTotal;
        private BigDecimal deductionsTotal;
        private BigDecimal netRevenue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SupplierRevenueSummaryResponse {
        private String periodFrom;
        private String periodTo;
        private int totalSuppliers;
        private int totalShifts;
        private int totalOrders;
        private BigDecimal grossRevenue;
        private BigDecimal netAdjustments;
        private BigDecimal netRevenue;
        private List<SupplierRowSummary> rows;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SupplierShiftDetail {
        private UUID shiftId;
        private LocalDate shiftDate;
        private String externalShiftId;
        private String ninjaCaptainId;
        private String captainName;
        private String zoneName;
        private int totalDelivered;
        private BigDecimal workingHours;
        private BigDecimal supplierRevenue;
        private BigDecimal supplierExtraRevenue;
        private boolean excluded;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SupplierStatementResponse {
        private UUID supplierId;
        private String supplierName;
        private String supplierCode;
        private String color;
        private String periodFrom;
        private String periodTo;
        private int totalShifts;
        private int totalOrders;
        private BigDecimal workingHours;
        private BigDecimal baseRevenue;
        private BigDecimal extraRevenue;
        private BigDecimal additionsTotal;
        private BigDecimal deductionsTotal;
        private BigDecimal netRevenue;
        private List<SupplierShiftDetail> shifts;
        private List<SupplierAdjustmentResponse> adjustments;
    }
}
