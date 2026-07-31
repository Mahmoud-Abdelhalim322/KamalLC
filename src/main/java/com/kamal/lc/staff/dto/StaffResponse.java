package com.kamal.lc.staff.dto;

import lombok.*;

import java.util.UUID;

/**
 * Lightweight staff record for list views.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {
    private UUID id;
    private String staffCode;
    private String staffType;
    private String jobTitle;
    private String fullNameAr;
    private String fullNameEn;
    private String phone;
    private String nationality;
    private String status;
    private String joinDate;
    private String iqamaExpiry;
    private String passportExpiry;
    private String insuranceExpiry;
    private String workPermitExpiry;

    private String documentAlert;
    private long documentAlertCount;
}
