package com.kamal.lc.auth.dto;

import lombok.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * DTOs for user management and job titles.
 */
public class UserManagementDtos {

    // ---- Job Title ----

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class JobTitleResponse {
        private UUID id;
        private String name;
        private String labelAr;
        private boolean systemDefined;
        private boolean active;
        private Set<String> permissions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class JobTitleRequest {
        private String name;
        private String labelAr;
        private Set<String> permissions;
    }

    // ---- User ----

    /** Account counts over the whole user list, for the accounts card header. */
    @Data @NoArgsConstructor @AllArgsConstructor
    public static class UserListSummary {
        private long total;
        private long active;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserResponse {
        private UUID id;
        private String username;
        private String displayName;
        private String role;
        private boolean active;
        private UUID staffId;
        private String staffName;
        private String staffCode;
        private UUID jobTitleId;
        private String jobTitleLabel;
        private Set<String> permissions;
        private String createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateUserRequest {
        private String username;
        private String password;
        private String displayName;
        private UUID staffId;
        private UUID jobTitleId;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateUserRequest {
        private String displayName;
        private UUID jobTitleId;
        private UUID staffId;
        private Boolean active;
        private String password;     // null = don't change
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PermissionInfo {
        private String name;
        private String labelAr;
    }
}
