package com.kamal.lc.custody.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Summary of a captain's active custody — used for the custody dashboard cards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainCustodySummary {
    private UUID captainId;
    private String captainCode;
    private String captainName;
    private long activeCount;
    private long vehicleCount;
    private long simCount;
    private long phoneCount;
    private long otherCount;
    private List<CustodyRecordResponse> activeItems;
}
