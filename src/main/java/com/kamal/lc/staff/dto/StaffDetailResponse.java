package com.kamal.lc.staff.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Full staff detail — all 42+ columns from the Excel spreadsheet plus uploaded documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffDetailResponse {
    private UUID id;
    private String staffCode;
    private String staffType;
    private String jobTitle;

    // Personal
    private String fullNameAr;
    private String fullNameEn;
    private String gender;
    private String nationality;
    private String dateOfBirth;
    private String phone;
    private String placeOfResidence;
    private String homeAddress;
    private String email;

    private String appId;

    // Passport
    private String passportNumber;
    private String passportIssueDate;
    private String passportExpiry;
    private String passportStatus;

    // Iqama
    private String iqamaNumber;
    private String iqamaOccupation;
    private String iqamaExpiry;
    private String iqamaExpiryRenewed;
    private String iqamaStatus;

    // Qiwa
    private String qiwaContractNumber;
    private String establishmentNumber;
    private String qiwaContractStart;
    private String qiwaContractEnd;
    private String qiwaContractStatus;

    // Work Permit
    private String workPermitExpiry;
    private String workPermitStatus;

    // Company
    private String affiliatedCompany;
    private String crNumber;

    // Insurance
    private String insuranceNumber;
    private String insuranceCategory;
    private String insuranceIssueDate;
    private String insuranceExpiry;
    private String insuranceCompany;
    private String insuranceStatus;

    // Trial & Licence
    private Integer trialPeriodDays;
    private boolean hasLicence;
    private String licenceExpiry;
    private String licenceStatus;

    // Other
    private String bloodType;
    private String bankAccountNumber;
    private String bankName;

    // Salary & Allowances
    private BigDecimal basicSalary;
    private BigDecimal housingAllowance;
    private BigDecimal otherAllowances;
    private BigDecimal transportationAllowance;
    private BigDecimal fuelAllowanceAmount;
    private BigDecimal callInternetAllowance;
    private BigDecimal rewardAllowance;

    // Work Info
    private String workPhone;
    private String department;
    private String section;
    private String supervisor;
    private String workLocation;
    private String workWith;

    // Status
    private String status;
    private String joinDate;
    private String leaveDate;
    private String notes;

    // Document Alerts & Statuses
    private List<DocumentStatusInfo> documentStatuses;
    private String worstDocumentStatus;

    // Documents
    private List<DocumentInfo> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentStatusInfo {
        private String kind;
        private String labelAr;
        private String number;
        private String expiry;
        private String status;
        private String statusLabelAr;
        private Long daysLeft;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentInfo {
        private UUID id;
        private String documentType;
        private String fileName;
        private String contentType;
        private Long fileSize;
        private String uploadedAt;
        private String note;
    }
}
