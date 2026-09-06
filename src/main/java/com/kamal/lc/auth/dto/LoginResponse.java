package com.kamal.lc.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Login response DTO — returned after successful authentication.
 * Now includes tenant information for multi-tenant support.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String role;
    private String username;
    private String displayName;
    private long expiresIn;
    private Set<String> permissions;

    // ---- Multi-Tenant fields ----
    private String tenantId;
    private String tenantName;
    private String tenantLogo;
    private String tenantPrimaryColor;
    private String tenantSecondaryColor;
}
