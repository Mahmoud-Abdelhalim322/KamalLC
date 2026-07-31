package com.kamal.lc.captain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String nationality;
    private LocalDate dateOfBirth;
    private String phone;

    @Email(message = "البريد الإلكتروني غير صالح")
    private String email;

    private String iqamaNumber;
    private LocalDate iqamaExpiry;
    private String passportNumber;
    private LocalDate passportExpiry;
    private String drivingLicenceNo;
    private LocalDate licenceExpiry;
    private LocalDate workPermitExpiry;
    private String insuranceNumber;
    private String insuranceCompany;
    private LocalDate insuranceExpiry;
    private boolean usesOwnVehicle;

    /**
     * Nullable on purpose: when the client leaves it out, eligibility follows
     * {@code usesOwnVehicle} instead of being silently cleared.
     */
    private Boolean fuelAllowanceEligible;

    private String status;           // ACTIVE | INACTIVE | TERMINATED
    private LocalDate joinDate;
    private LocalDate leaveDate;
    private String notes;
}
