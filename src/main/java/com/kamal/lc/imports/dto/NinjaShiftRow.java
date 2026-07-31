package com.kamal.lc.imports.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** One raw shift row read from the supplier's daily file. */
@Data
@Builder
public class NinjaShiftRow {

    /** 1-based row number in the sheet, for issue messages. */
    private int rowNumber;

    private String supplierExternalId;
    private String supplierName;
    private String externalShiftId;
    private String city;
    private String zoneName;
    private String shiftName;
    private String ninjaCaptainId;
    private String ninjaCaptainName;

    private OffsetDateTime scheduledStart;
    private OffsetDateTime scheduledEnd;
    private OffsetDateTime actualCheckin;
    /** KSA calendar date the shift starts on — the key everything is grouped by. */
    private LocalDate shiftDate;

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

    @Builder.Default
    private List<String> warnings = List.of();
}
