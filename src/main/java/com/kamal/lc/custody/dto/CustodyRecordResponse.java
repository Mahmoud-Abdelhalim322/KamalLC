package com.kamal.lc.custody.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustodyRecordResponse {
    private UUID id;
    private UUID captainId;
    private String captainName;
    private String captainCode;
    private String assetType;
    private String assetTypeLabelAr;
    private UUID referenceId;
    private String assetLabel;
    private String assignedAt;
    private String returnedAt;
    private String status;
    private String assignedBy;
    private String returnedBy;
    private String notes;
}
