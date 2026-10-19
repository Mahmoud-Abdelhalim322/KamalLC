package com.kamal.lc.imports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single parsed row from the captain import Excel file.
 * Contains all fields that map to the Captain entity for comprehensive import.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelCaptainRow {
    private int rowNumber;
    private String kamalCode;
    private String ninjaId;
    private String captainName;
    private String fullNameEn;
    private String gender;
    private String phone;
    private String nationality;
    private LocalDate dateOfBirth;
    private String email;
    private String placeOfResidence;
    private String homeAddress;
    private String jobTitle;
    private String appId;

    // Passport
    private String passportNumber;
    private LocalDate passportIssueDate;
    private LocalDate passportExpiry;
    private String passportStatus;

    // Iqama
    private String iqamaNumber;
    private String iqamaOccupation;
    private LocalDate idExpiryDate;
    private LocalDate iqamaExpiry;
    private LocalDate iqamaExpiryRenewed;
    private String borderNumber;
    private String iqamaStatus;

    // Qiwa
    private String qiwaContractNumber;
    private String establishmentNumber;
    private LocalDate qiwaContractStart;
    private LocalDate qiwaContractEnd;
    private String qiwaContractStatus;

    // Work Permit
    private LocalDate workPermitExpiry;
    private String workPermitStatus;

    // Company
    private String affiliatedCompany;
    private String crNumber;

    // Driving Licence
    private String drivingLicenceNo;
    private String licenceType;
    private LocalDate licenceExpiry;
    private boolean hasLicence;
    private String licenceStatus;

    // Insurance
    private String insuranceNumber;
    private String insuranceCategory;
    private LocalDate insuranceIssueDate;
    private String insuranceCompany;
    private LocalDate insuranceExpiry;
    private String insuranceStatus;

    // Personal
    private String bloodType;
    private Integer trialPeriodDays;

    // Bank
    private String bankAccountNumber;
    private String bankName;

    // Salary
    private BigDecimal basicSalary;
    private BigDecimal housingAllowance;
    private BigDecimal transportationAllowance;
    private BigDecimal fuelAllowanceAmount;
    private BigDecimal callInternetAllowance;
    private BigDecimal rewardAllowance;
    private BigDecimal salaryDeductions;
    private BigDecimal netSalary;

    // Vehicle & Fuel
    private boolean usesOwnVehicle;
    private boolean fuelAllowanceEligible;

    // Work Info
    private String workPhone;
    private String department;
    private String section;
    private String supervisor;
    private String workLocation;
    private String workWith;

    // Status
    private String status;
    private LocalDate joinDate;
    private LocalDate leaveDate;
    private String notes;

    /** Cell-level problems found while parsing (unreadable dates, etc.) — reported as warnings. */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /** Returns the captain's external supplier identifier (ID-APP or ninjaId). */
    public String getEffectiveIdentifier() {
        if (appId != null && !appId.trim().isEmpty()) {
            return appId.trim();
        }
        if (ninjaId != null && !ninjaId.trim().isEmpty()) {
            return ninjaId.trim();
        }
        return null;
    }
}
