package com.kamal.lc.captain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload for creating or updating a captain.
 *
 * <p>Note: {@code kamalCode} is intentionally absent — it is generated server-side by
 * {@link com.kamal.lc.captain.service.CaptainCodeGenerator} on create and is immutable afterwards.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainRequest {

    @NotBlank(message = "نوع الكابتن مطلوب")
    private String captainType;      // EMPLOYEE | FREELANCE

    @NotBlank(message = "الاسم بالعربية مطلوب")
    private String fullNameAr;

    private String fullNameEn;
    private String gender;
    private String nationality;
    private LocalDate dateOfBirth;
    private String phone;

    @Email(message = "البريد الإلكتروني غير صالح")
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
    private Boolean fuelAllowanceEligible;

    // Work Info
    private String workPhone;
    private String department;
    private String section;
    private String supervisor;
    private String workLocation;
    private String workWith;

    // Status
    private String status;           // ACTIVE | INACTIVE | TERMINATED
    private LocalDate joinDate;
    private LocalDate leaveDate;
    private String notes;
}
