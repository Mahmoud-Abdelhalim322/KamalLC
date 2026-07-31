package com.kamal.lc.shift.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ShiftResponse {

    private UUID id;
    private String externalShiftId;
    private String shiftDate;

    private UUID captainId;
    private String captainCode;
    private String captainName;
    /** The supplier's own captain ID and name, kept visible for reconciliation. */
    private String ninjaCaptainId;
    private String ninjaCaptainName;

    private String supplierName;
    private String city;
    private String zoneName;
    private String shiftName;

    private String scheduledStart;
    private String scheduledEnd;
    private String actualCheckin;
    private String attendanceFlag;

    private BigDecimal delayMinutes;
    private BigDecimal workingHours;
    private BigDecimal shiftTypeHours;
    private Integer totalDelivered;
    private BigDecimal offlineTime;
    private BigDecimal freeTime;
    private BigDecimal busyTime;
    private BigDecimal awayFromZone;
    private BigDecimal busyUtilization;
    private BigDecimal deliveryOntime;
    private BigDecimal hourlyUtr;

    private Boolean attended;
    private BigDecimal dailyWage;
    private BigDecimal lateDeduction;
    private Integer extraOrders;
    private BigDecimal dailyIncentive;
    private BigDecimal fuelAllowance;
    private BigDecimal carAllowance;
    private BigDecimal netShiftPay;

    private Boolean qualifiesFullRate;
    private BigDecimal supplierRevenue;
    private BigDecimal supplierExtraRevenue;
    private BigDecimal shiftMargin;

    private String calcNote;
    private boolean excluded;
    private String exclusionReason;
}
