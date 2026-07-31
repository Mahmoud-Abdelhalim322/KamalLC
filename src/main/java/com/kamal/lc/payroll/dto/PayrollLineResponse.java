package com.kamal.lc.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PayrollLineResponse {
    private UUID id;
    private UUID captainId;
    private String captainCode;
    private String captainName;
    private String captainType;
    private String supplierExternalIds;

    private int attendedShifts;
    private int absentShifts;
    private int totalOrders;
    private BigDecimal totalHours;
    private BigDecimal avgBusyUtilization;
    private BigDecimal avgHourlyUtr;
    private BigDecimal avgDeliveryOntime;

    private BigDecimal basePay;
    private BigDecimal lateDeductions;
    private BigDecimal dailyIncentives;
    private BigDecimal fuelAllowances;
    private BigDecimal carAllowances;
    private BigDecimal targetBonus;
    private BigDecimal extraOrderPay;
    private BigDecimal gross;
    private BigDecimal adjustmentsTotal;
    private BigDecimal net;

    private BigDecimal supplierRevenueTotal;
    private BigDecimal margin;

    private String notes;
    private List<AdjustmentResponse> adjustments;
}
