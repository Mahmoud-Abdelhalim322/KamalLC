package com.kamal.lc.shift.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for updating an existing shift's data.
 * Only the fields that are editable are included here.
 */
@Data
public class ShiftUpdateRequest {
    private LocalDate shiftDate;
    private String ninjaCaptainId;
    private String ninjaCaptainName;
    private String zoneName;
    private String shiftName;
    private String attendanceFlag;
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
    private BigDecimal delayMinutes;
    private BigDecimal supplierDeduction;
    private String supplierDeductionReason;
}
