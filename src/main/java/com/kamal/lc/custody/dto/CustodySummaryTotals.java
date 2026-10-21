package com.kamal.lc.custody.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Totals over every captain matching the custody list search, for the KPI strip. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustodySummaryTotals {
    private long captainCount;
    private long activeCount;
    private long vehicleCount;
    private long simCount;
    private long phoneCount;
    private long otherCount;
}
