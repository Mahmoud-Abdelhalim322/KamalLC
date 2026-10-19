package com.kamal.lc.captain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainDetailResponse {
    private UUID id;
    private String kamalCode;
    private String captainType;
    private String fullNameAr;
    private String fullNameEn;
    private String gender;
    private String nationality;
    private String dateOfBirth;
    private String phone;
    private String email;
    private String placeOfResidence;
    private String homeAddress;
    private String jobTitle;
    private String appId;

    // Passport
    private String passportNumber;
    private String passportIssueDate;
    private String passportExpiry;
    private String passportStatus;

    // Iqama
    private String iqamaNumber;
    private String iqamaOccupation;
    private String idExpiryDate;
    private String iqamaExpiry;
    private String iqamaExpiryRenewed;
    private String borderNumber;
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

    // Driving Licence
    private String drivingLicenceNo;
    private String licenceType;
    private String licenceExpiry;
    private boolean hasLicence;
    private String licenceStatus;

    // Insurance
    private String insuranceNumber;
    private String insuranceCategory;
    private String insuranceIssueDate;
    private String insuranceCompany;
    private String insuranceExpiry;
    private String insuranceStatus;

    // Other Personal
    private String bloodType;
    private Integer trialPeriodDays;

    // Bank
    private String bankAccountNumber;
    private String bankName;

    // Salary & Allowances
    private BigDecimal basicSalary;
    private BigDecimal housingAllowance;
    private BigDecimal transportationAllowance;
    private BigDecimal fuelAllowanceAmount;
    private BigDecimal callInternetAllowance;
    private BigDecimal rewardAllowance;
    private BigDecimal salaryDeductions;
    private BigDecimal netSalary;

    // Vehicle & Fuel flags
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
    private String joinDate;
    private String leaveDate;
    private String notes;

    /** Every tracked document with its verdict — what the الوثائق الرسمية card renders. */
    private List<DocumentStatusInfo> documentStatuses;

    /** The worst of the above, for the warning banner. */
    private String worstDocumentStatus;

    private List<SupplierIdInfo> supplierIds;
    private List<DocumentInfo> documents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierIdInfo {
        private UUID slotId;
        private String externalId;
        private String supplierName;
        private String supplierCode;
        private String validFrom;
        private String validTo;
    }

    /** One tracked document as the detail screen shows it: what it is, and where it stands. */
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
        /** Negative once the date has passed; null when no date is recorded. */
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
