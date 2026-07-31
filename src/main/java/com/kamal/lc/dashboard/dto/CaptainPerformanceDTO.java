package com.kamal.lc.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainPerformanceDTO {
    private UUID captainId;
    private String kamalCode;
    private String captainName;
    private String captainType;
    private int totalOrders;
    private int attendedShifts;
    private BigDecimal attendanceRate;
    private BigDecimal netPay;
}
