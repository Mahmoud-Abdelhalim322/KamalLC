package com.kamal.lc.captain.entity;

import com.kamal.lc.common.security.TenantAware;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "captain")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Captain implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "kamal_code", nullable = false, unique = true, length = 50)
    private String kamalCode;

    @Column(name = "captain_type", nullable = false, length = 20)
    private String captainType;

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

    @Column(length = 255)
    private String email;

    @Column(name = "place_of_residence", length = 255)
    private String placeOfResidence;

    @Column(name = "home_address", length = 255)
    private String homeAddress;

    @Column(name = "job_title", length = 255)
    private String jobTitle;

    @Column(name = "app_id", length = 100)
    private String appId;

    // ---- Passport ----
    @Column(name = "passport_number", length = 50)
    private String passportNumber;

    @Column(name = "passport_issue_date")
    private LocalDate passportIssueDate;

    @Column(name = "passport_expiry")
    private LocalDate passportExpiry;

    @Column(name = "passport_status", length = 50)
    private String passportStatus;

    // ---- Iqama ----
    @Column(name = "iqama_number", length = 50)
    private String iqamaNumber;

    @Column(name = "iqama_occupation", length = 255)
    private String iqamaOccupation;

    @Column(name = "id_expiry_date")
    private LocalDate idExpiryDate;

    @Column(name = "iqama_expiry")
    private LocalDate iqamaExpiry;

    @Column(name = "iqama_expiry_renewed")
    private LocalDate iqamaExpiryRenewed;

    @Column(name = "border_number", length = 50)
    private String borderNumber;

    @Column(name = "iqama_status", length = 50)
    private String iqamaStatus;

    // ---- Qiwa Contract ----
    @Column(name = "qiwa_contract_number", length = 100)
    private String qiwaContractNumber;

    @Column(name = "establishment_number", length = 100)
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

    @Column(name = "cr_number", length = 100)
    private String crNumber;

    // ---- Driving Licence ----
    @Column(name = "driving_licence_no", length = 50)
    private String drivingLicenceNo;

    @Column(name = "licence_type", length = 50)
    private String licenceType;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    @Column(name = "has_licence", nullable = false)
    @Builder.Default
    private boolean hasLicence = false;

    @Column(name = "licence_status", length = 50)
    private String licenceStatus;

    // ---- Insurance ----
    @Column(name = "insurance_number", length = 50)
    private String insuranceNumber;

    @Column(name = "insurance_category", length = 100)
    private String insuranceCategory;

    @Column(name = "insurance_issue_date")
    private LocalDate insuranceIssueDate;

    @Column(name = "insurance_company", length = 255)
    private String insuranceCompany;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "insurance_status", length = 50)
    private String insuranceStatus;

    // ---- Other Personal ----
    @Column(name = "blood_type", length = 10)
    private String bloodType;

    @Column(name = "trial_period_days")
    private Integer trialPeriodDays;

    // ---- Bank ----
    @Column(name = "bank_account_number", length = 100)
    private String bankAccountNumber;

    @Column(name = "bank_name", length = 255)
    private String bankName;

    // ---- Salary & Allowances ----
    @Column(name = "basic_salary", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "housing_allowance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal housingAllowance = BigDecimal.ZERO;

    @Column(name = "transportation_allowance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal transportationAllowance = BigDecimal.ZERO;

    @Column(name = "fuel_allowance_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal fuelAllowanceAmount = BigDecimal.ZERO;

    @Column(name = "call_internet_allowance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal callInternetAllowance = BigDecimal.ZERO;

    @Column(name = "reward_allowance", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal rewardAllowance = BigDecimal.ZERO;

    @Column(name = "salary_deductions", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal salaryDeductions = BigDecimal.ZERO;

    @Column(name = "net_salary", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal netSalary = BigDecimal.ZERO;

    // ---- Vehicle & Fuel Flags ----
    @Column(name = "uses_own_vehicle", nullable = false)
    @Builder.Default
    private boolean usesOwnVehicle = false;

    @Column(name = "fuel_allowance_eligible", nullable = false)
    @Builder.Default
    private boolean fuelAllowanceEligible = false;

    // ---- Work Info ----
    @Column(name = "work_phone", length = 30)
    private String workPhone;

    @Column(length = 255)
    private String department;

    @Column(length = 255)
    private String section;

    @Column(length = 255)
    private String supervisor;

    @Column(name = "work_location", length = 255)
    private String workLocation;

    @Column(name = "work_with", length = 255)
    private String workWith;

    // ---- Status & Dates ----
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "join_date")
    private LocalDate joinDate;

    @Column(name = "leave_date")
    private LocalDate leaveDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
