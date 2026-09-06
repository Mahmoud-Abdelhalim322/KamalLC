package com.kamal.lc.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

public class CaptainAllocationDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAllocationSummaryResponse {
        private UUID userId;
        private String username;
        private String displayName;
        private String role;
        private String dataScope;
        private long assignedCaptainCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaptainSimpleDto {
        private UUID id;
        private String fullNameAr;
        private String kamalCode;
        private String phone;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAllocationDetailResponse {
        private UUID userId;
        private String username;
        private String displayName;
        private String role;
        private String dataScope;
        private List<CaptainSimpleDto> assignedCaptains;
        private List<CaptainSimpleDto> availableCaptains;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateUserAllocationRequest {
        private String dataScope; // "ALL_CAPTAINS" or "ASSIGNED_ONLY"
        private List<UUID> captainIds;
    }
}
