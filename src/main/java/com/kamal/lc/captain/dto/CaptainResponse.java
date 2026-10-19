package com.kamal.lc.captain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainResponse {
    private UUID id;
    private String kamalCode;
    private String captainType;
    private String fullNameAr;
    private String fullNameEn;
    private String phone;
    private String nationality;
    private String iqamaNumber;
    private String borderNumber;
    private String jobTitle;
    private java.math.BigDecimal netSalary;
    private String status;
    private String currentNinjaId;
    private String joinDate;

    /**
     * The most serious thing this captain's documents say — the row badge. A leaver reads
     * VALID: nothing gets renewed for him.
     */
    private String documentAlert;

    /** How many of his documents are not simply valid, for the "(2)" beside the badge. */
    private long documentAlertCount;
}
