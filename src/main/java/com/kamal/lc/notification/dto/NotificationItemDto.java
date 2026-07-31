package com.kamal.lc.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationItemDto {
    private String id;
    private UUID memberId;
    private String memberCode;
    private String memberName;
    private String memberType;
    private String memberTypeLabel;
    private String targetUrl;
    private String documentKind;
    private String documentLabelAr;
    private String expiryDate;
    private String status;
    private String statusLabelAr;
    private Long daysLeft;
    private String message;
    private String severity; // HIGH (EXPIRED), MEDIUM (EXPIRING), LOW (MISSING)
}
