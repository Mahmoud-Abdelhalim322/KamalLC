package com.kamal.lc.staff.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create/update payload for a staff member (worker or manager).
 * staffCode is generated server-side on create and never accepted from clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffRequest {

    @NotBlank(message = "نوع الموظف مطلوب")
    private String staffType;            // WORKER | MANAGER

    private String jobTitle;             // for MANAGER only

    @NotBlank(message = "الاسم بالعربية مطلوب")
    private String fullNameAr;

    private String fullNameEn;
    private String gender;
    private String nationality;
    private LocalDate dateOfBirth;
    private String phone;
    private String placeOfResidence;
    private String homeAddress;

    @Email(message = "البريد الإلكتروني غير صالح")
    private String email;

    // Passport
    private String passportNumber;
    private LocalDate passportIssueDate;
    private LocalDate passportExpiry;
    private String passportStatus;

    // Iqama
    private String iqamaNumber;
    private String iqamaOccupation;
    private LocalDate iqamaExpiry;
    private LocalDate iqamaExpiryRenewed;
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

    // Insurance
    private String insuranceNumber;
    private String insuranceCategory;
    private LocalDate insuranceIssueDate;
    private LocalDate insuranceExpiry;
    private String insuranceCompany;
    private String insuranceStatus;

    // Trial & Licence
    private Integer trialPeriodDays;
    private boolean hasLicence;
    private LocalDate licenceExpiry;
    private String licenceStatus;

    // Other
    private String bloodType;
    private String bankAccountNumber;
    private String bankName;

    // Salary
    private BigDecimal basicSalary;
    private BigDecimal housingAllowance;
    private BigDecimal otherAllowances;

    // Status
    private String status;
    private LocalDate joinDate;
    private LocalDate leaveDate;
    private String notes;
}
