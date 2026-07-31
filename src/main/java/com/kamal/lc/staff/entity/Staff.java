package com.kamal.lc.staff.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A staff member — either a WORKER or a MANAGER.
 * Mirrors the 42-column Excel spreadsheet for employee data.
 */
@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "staff_code", nullable = false, unique = true, length = 50)
    private String staffCode;

    /** WORKER or MANAGER */
    @Column(name = "staff_type", nullable = false, length = 20)
    private String staffType;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    // ---- Personal Info ----

    @Column(name = "full_name_ar", nullable = false, length = 255)
    private String fullNameAr;

    @Column(name = "full_name_en", length = 255)
    private String fullNameEn;

    @Column(length = 20)
    private String gender;

    @Column(length = 100)
    private String nationality;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String phone;

    @Column(name = "place_of_residence", length = 255)
    private String placeOfResidence;

    @Column(name = "home_address", length = 500)
    private String homeAddress;

    @Column(length = 255)
    private String email;

    // ---- Passport ----

    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "passport_issue_date")
    private LocalDate passportIssueDate;

    @Column(name = "passport_expiry")
    private LocalDate passportExpiry;

    @Column(name = "passport_status", length = 50)
    private String passportStatus;

    // ---- Iqama / Residence ----

    @Column(name = "iqama_number", length = 50)
    private String iqamaNumber;

    @Column(name = "iqama_occupation", length = 255)
    private String iqamaOccupation;

    @Column(name = "iqama_expiry")
    private LocalDate iqamaExpiry;

    @Column(name = "iqama_expiry_renewed")
    private LocalDate iqamaExpiryRenewed;

    @Column(name = "iqama_status", length = 50)
    private String iqamaStatus;

    // ---- Qiwa Contract ----

    @Column(name = "qiwa_contract_number", length = 50)
    private String qiwaContractNumber;

    @Column(name = "establishment_number", length = 50)
    private String establishmentNumber;

    @Column(name = "qiwa_contract_start")
    private LocalDate qiwaContractStart;

    @Column(name = "qiwa_contract_end")
    private LocalDate qiwaContractEnd;

    @Column(name = "qiwa_contract_status", length = 50)
    private String qiwaContractStatus;

    // ---- Work Permit ----

    @Column(name = "work_permit_expiry")
    private LocalDate workPermitExpiry;

    @Column(name = "work_permit_status", length = 50)
    private String workPermitStatus;

    // ---- Company ----

    @Column(name = "affiliated_company", length = 255)
    private String affiliatedCompany;

    @Column(name = "cr_number", length = 50)
    private String crNumber;

    // ---- Insurance ----

    @Column(name = "insurance_number", length = 50)
    private String insuranceNumber;

    @Column(name = "insurance_category", length = 20)
    private String insuranceCategory;

    @Column(name = "insurance_issue_date")
    private LocalDate insuranceIssueDate;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "insurance_company", length = 255)
    private String insuranceCompany;

    @Column(name = "insurance_status", length = 50)
    private String insuranceStatus;

    // ---- Trial & Licence ----

    @Column(name = "trial_period_days")
    private Integer trialPeriodDays;

    @Column(name = "has_licence")
    @Builder.Default
    private boolean hasLicence = false;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    @Column(name = "licence_status", length = 50)
    private String licenceStatus;

    // ---- Other ----

    @Column(name = "blood_type", length = 10)
    private String bloodType;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    // ---- Salary ----

    @Column(name = "basic_salary", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "housing_allowance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal housingAllowance = BigDecimal.ZERO;

    @Column(name = "other_allowances", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal otherAllowances = BigDecimal.ZERO;

    // ---- Status ----

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
