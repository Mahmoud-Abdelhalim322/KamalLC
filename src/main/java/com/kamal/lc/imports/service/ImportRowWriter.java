package com.kamal.lc.imports.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.imports.dto.ExcelCaptainRow;
import com.kamal.lc.imports.dto.ImportPreviewResponse.ImportIssue;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.repository.StaffRepository;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import com.kamal.lc.supplier.entity.SupplierIdSlot;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import com.kamal.lc.supplier.repository.SupplierIdSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writes one captain or staff row, in a transaction of its own.
 *
 * <p>The separate transaction is the point of this class. A row that trips a database
 * constraint marks its transaction rollback-only, and every statement after that fails
 * with "current transaction is aborted" — so an importer that catches per-row exceptions
 * and carries on inside a single transaction does not skip one bad row, it loses the
 * whole file and reports an opaque 500. REQUIRES_NEW gives each row a transaction that
 * can fail on its own without poisoning the rest of the import.
 */
@Service
@RequiredArgsConstructor
public class ImportRowWriter {

    private final CaptainRepository captainRepo;
    private final StaffRepository staffRepo;
    private final SupplierIdSlotRepository slotRepo;
    private final SupplierIdAssignmentRepository assignmentRepo;

    /**
     * Creates or updates the captain, registers the supplier ID, and links the two.
     *
     * @return what happened that the user should know about; empty when the row applied cleanly
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ImportIssue> write(ExcelCaptainRow row, Supplier supplier) {
        List<ImportIssue> issues = new ArrayList<>();
        String rawCode = row.getKamalCode();
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return issues;
        }
        String code = rawCode.trim().toUpperCase();

        if (code.startsWith("KSE")) {
            staffRepo.findByStaffCode(code)
                    .map(existing -> fillMissingStaffDetails(existing, row))
                    .orElseGet(() -> staffRepo.save(newStaff(row, "WORKER")));
            return issues;
        }

        if (code.startsWith("KSM")) {
            staffRepo.findByStaffCode(code)
                    .map(existing -> fillMissingStaffDetails(existing, row))
                    .orElseGet(() -> staffRepo.save(newStaff(row, "MANAGER")));
            return issues;
        }

        if (code.startsWith("KSC") || code.startsWith("KSCFL")) {
            Captain captain = captainRepo.findByKamalCode(code)
                    .map(existing -> fillMissingDetails(existing, row))
                    .orElseGet(() -> captainRepo.save(newCaptain(row)));

            String externalId = row.getEffectiveIdentifier();
            if (externalId == null || externalId.isEmpty() || supplier == null) {
                return issues; // identifier is optional
            }

            SupplierIdSlot slot = slotRepo.findByExternalIdAndSupplierId(externalId, supplier.getId())
                    .orElseGet(() -> slotRepo.save(SupplierIdSlot.builder()
                            .externalId(externalId)
                            .supplier(supplier)
                            .isActive(true)
                            .build()));

            if (assignmentRepo.existsBySupplierIdSlotIdAndCaptainIdAndValidToIsNull(slot.getId(), captain.getId())) {
                return issues; // already linked to this captain — nothing to do
            }

            LocalDate today = LocalDate.now();
            Optional<SupplierIdAssignment> open = assignmentRepo.findBySupplierIdSlotIdAndValidToIsNull(slot.getId());

            if (open.isPresent()) {
                SupplierIdAssignment current = open.get();

                if (!current.getValidFrom().isBefore(today)) {
                    issues.add(issue(row.getRowNumber(), "WARNING", "DUPLICATE_NINJA_ID",
                            "معرف المورد " + externalId + " مربوط بالفعل بكابتن "
                                    + current.getCaptain().getFullNameAr()
                                    + " — لم يتم ربطه بـ " + captain.getFullNameAr()
                                    + "، المعرف مكرر في الملف"));
                    return issues;
                }

                current.setValidTo(today.minusDays(1));
                assignmentRepo.save(current);
                issues.add(issue(row.getRowNumber(), "INFO", "REASSIGNED",
                        "تم نقل معرف " + externalId + " من " + current.getCaptain().getFullNameAr()));
            }

            assignmentRepo.save(SupplierIdAssignment.builder()
                    .supplierIdSlot(slot)
                    .captain(captain)
                    .supplierSideName(captain.getFullNameAr())
                    .validFrom(today)
                    .build());

            return issues;
        }

        return issues;
    }

    private Captain newCaptain(ExcelCaptainRow row) {
        String code = row.getKamalCode().trim().toUpperCase();
        String type = code.startsWith("KSCFL") ? "FREELANCE" : "EMPLOYEE";
        String nameAr = (row.getCaptainName() != null && !row.getCaptainName().isBlank())
                ? row.getCaptainName().trim()
                : (row.getFullNameEn() != null && !row.getFullNameEn().isBlank()
                        ? row.getFullNameEn().trim()
                        : code);

        Captain.CaptainBuilder builder = Captain.builder()
                .kamalCode(code)
                .captainType(type)
                .fullNameAr(nameAr)
                .fullNameEn(row.getFullNameEn())
                .gender(row.getGender())
                .phone(row.getPhone())
                .nationality(row.getNationality())
                .dateOfBirth(row.getDateOfBirth())
                .email(row.getEmail())
                .placeOfResidence(row.getPlaceOfResidence())
                .homeAddress(row.getHomeAddress())
                .jobTitle(row.getJobTitle())
                .appId(row.getEffectiveIdentifier())
                // Passport
                .passportNumber(row.getPassportNumber())
                .passportIssueDate(row.getPassportIssueDate())
                .passportExpiry(row.getPassportExpiry())
                .passportStatus(row.getPassportStatus())
                // Iqama
                .iqamaNumber(row.getIqamaNumber())
                .iqamaOccupation(row.getIqamaOccupation())
                .idExpiryDate(row.getIdExpiryDate())
                .iqamaExpiry(row.getIqamaExpiry())
                .iqamaExpiryRenewed(row.getIqamaExpiryRenewed())
                .borderNumber(row.getBorderNumber())
                .iqamaStatus(row.getIqamaStatus())
                // Qiwa
                .qiwaContractNumber(row.getQiwaContractNumber())
                .establishmentNumber(row.getEstablishmentNumber())
                .qiwaContractStart(row.getQiwaContractStart())
                .qiwaContractEnd(row.getQiwaContractEnd())
                .qiwaContractStatus(row.getQiwaContractStatus())
                // Work Permit
                .workPermitExpiry(row.getWorkPermitExpiry())
                .workPermitStatus(row.getWorkPermitStatus())
                // Company
                .affiliatedCompany(row.getAffiliatedCompany())
                .crNumber(row.getCrNumber())
                // Driving Licence
                .drivingLicenceNo(row.getDrivingLicenceNo())
                .licenceType(row.getLicenceType())
                .licenceExpiry(row.getLicenceExpiry())
                .hasLicence(row.isHasLicence())
                .licenceStatus(row.getLicenceStatus())
                // Insurance
                .insuranceNumber(row.getInsuranceNumber())
                .insuranceCategory(row.getInsuranceCategory())
                .insuranceIssueDate(row.getInsuranceIssueDate())
                .insuranceCompany(row.getInsuranceCompany())
                .insuranceExpiry(row.getInsuranceExpiry())
                .insuranceStatus(row.getInsuranceStatus())
                // Personal
                .bloodType(row.getBloodType())
                .trialPeriodDays(row.getTrialPeriodDays())
                // Bank
                .bankAccountNumber(row.getBankAccountNumber())
                .bankName(row.getBankName())
                // Salary
                .basicSalary(row.getBasicSalary())
                .housingAllowance(row.getHousingAllowance())
                .transportationAllowance(row.getTransportationAllowance())
                .fuelAllowanceAmount(row.getFuelAllowanceAmount())
                .callInternetAllowance(row.getCallInternetAllowance())
                .rewardAllowance(row.getRewardAllowance())
                .salaryDeductions(row.getSalaryDeductions())
                .netSalary(row.getNetSalary())
                // Vehicle
                .usesOwnVehicle(row.isUsesOwnVehicle())
                .fuelAllowanceEligible(row.isFuelAllowanceEligible())
                // Work Info
                .workPhone(row.getWorkPhone())
                .department(row.getDepartment())
                .section(row.getSection())
                .supervisor(row.getSupervisor())
                .workLocation(row.getWorkLocation())
                .workWith(row.getWorkWith());

        // Status: use provided or default to ACTIVE
        String status = row.getStatus();
        builder.status(status != null && !status.isBlank() ? status.toUpperCase() : "ACTIVE");
        builder.joinDate(row.getJoinDate() != null ? row.getJoinDate() : LocalDate.now());
        builder.leaveDate(row.getLeaveDate());
        builder.notes(row.getNotes());

        return builder.build();
    }

    /**
     * Fills in details the sheet carries but the stored captain is missing. Values already
     * captured in the app win — an import must never overwrite data entered by hand.
     */
    private Captain fillMissingDetails(Captain captain, ExcelCaptainRow row) {
        boolean changed = false;

        // Basic info
        if (isBlank(captain.getFullNameAr()) && row.getCaptainName() != null && !row.getCaptainName().isBlank()) {
            captain.setFullNameAr(row.getCaptainName().trim()); changed = true;
        }
        if (isBlank(captain.getFullNameEn()) && row.getFullNameEn() != null) {
            captain.setFullNameEn(row.getFullNameEn()); changed = true;
        }
        if (isBlank(captain.getGender()) && row.getGender() != null) {
            captain.setGender(row.getGender()); changed = true;
        }
        if (isBlank(captain.getPhone()) && row.getPhone() != null) {
            captain.setPhone(row.getPhone()); changed = true;
        }
        if (isBlank(captain.getNationality()) && row.getNationality() != null) {
            captain.setNationality(row.getNationality()); changed = true;
        }
        if (captain.getDateOfBirth() == null && row.getDateOfBirth() != null) {
            captain.setDateOfBirth(row.getDateOfBirth()); changed = true;
        }
        if (isBlank(captain.getEmail()) && row.getEmail() != null) {
            captain.setEmail(row.getEmail()); changed = true;
        }
        if (isBlank(captain.getPlaceOfResidence()) && row.getPlaceOfResidence() != null) {
            captain.setPlaceOfResidence(row.getPlaceOfResidence()); changed = true;
        }
        if (isBlank(captain.getHomeAddress()) && row.getHomeAddress() != null) {
            captain.setHomeAddress(row.getHomeAddress()); changed = true;
        }
        if (isBlank(captain.getJobTitle()) && row.getJobTitle() != null) {
            captain.setJobTitle(row.getJobTitle()); changed = true;
        }
        String effId = row.getEffectiveIdentifier();
        if (effId != null && !effId.isBlank() && !effId.equals(captain.getAppId())) {
            captain.setAppId(effId); changed = true;
        }
        // Passport
        if (isBlank(captain.getPassportNumber()) && row.getPassportNumber() != null) {
            captain.setPassportNumber(row.getPassportNumber()); changed = true;
        }
        if (captain.getPassportIssueDate() == null && row.getPassportIssueDate() != null) {
            captain.setPassportIssueDate(row.getPassportIssueDate()); changed = true;
        }
        if (captain.getPassportExpiry() == null && row.getPassportExpiry() != null) {
            captain.setPassportExpiry(row.getPassportExpiry()); changed = true;
        }
        if (isBlank(captain.getPassportStatus()) && row.getPassportStatus() != null) {
            captain.setPassportStatus(row.getPassportStatus()); changed = true;
        }
        // Iqama
        if (isBlank(captain.getIqamaNumber()) && row.getIqamaNumber() != null) {
            captain.setIqamaNumber(row.getIqamaNumber()); changed = true;
        }
        if (isBlank(captain.getIqamaOccupation()) && row.getIqamaOccupation() != null) {
            captain.setIqamaOccupation(row.getIqamaOccupation()); changed = true;
        }
        if (captain.getIdExpiryDate() == null && row.getIdExpiryDate() != null) {
            captain.setIdExpiryDate(row.getIdExpiryDate()); changed = true;
        }
        if (captain.getIqamaExpiry() == null && row.getIqamaExpiry() != null) {
            captain.setIqamaExpiry(row.getIqamaExpiry()); changed = true;
        }
        if (captain.getIqamaExpiryRenewed() == null && row.getIqamaExpiryRenewed() != null) {
            captain.setIqamaExpiryRenewed(row.getIqamaExpiryRenewed()); changed = true;
        }
        if (isBlank(captain.getBorderNumber()) && row.getBorderNumber() != null) {
            captain.setBorderNumber(row.getBorderNumber()); changed = true;
        }
        if (isBlank(captain.getIqamaStatus()) && row.getIqamaStatus() != null) {
            captain.setIqamaStatus(row.getIqamaStatus()); changed = true;
        }
        // Qiwa
        if (isBlank(captain.getQiwaContractNumber()) && row.getQiwaContractNumber() != null) {
            captain.setQiwaContractNumber(row.getQiwaContractNumber()); changed = true;
        }
        if (isBlank(captain.getEstablishmentNumber()) && row.getEstablishmentNumber() != null) {
            captain.setEstablishmentNumber(row.getEstablishmentNumber()); changed = true;
        }
        if (captain.getQiwaContractStart() == null && row.getQiwaContractStart() != null) {
            captain.setQiwaContractStart(row.getQiwaContractStart()); changed = true;
        }
        if (captain.getQiwaContractEnd() == null && row.getQiwaContractEnd() != null) {
            captain.setQiwaContractEnd(row.getQiwaContractEnd()); changed = true;
        }
        if (isBlank(captain.getQiwaContractStatus()) && row.getQiwaContractStatus() != null) {
            captain.setQiwaContractStatus(row.getQiwaContractStatus()); changed = true;
        }
        // Work Permit
        if (captain.getWorkPermitExpiry() == null && row.getWorkPermitExpiry() != null) {
            captain.setWorkPermitExpiry(row.getWorkPermitExpiry()); changed = true;
        }
        if (isBlank(captain.getWorkPermitStatus()) && row.getWorkPermitStatus() != null) {
            captain.setWorkPermitStatus(row.getWorkPermitStatus()); changed = true;
        }
        // Company
        if (isBlank(captain.getAffiliatedCompany()) && row.getAffiliatedCompany() != null) {
            captain.setAffiliatedCompany(row.getAffiliatedCompany()); changed = true;
        }
        if (isBlank(captain.getCrNumber()) && row.getCrNumber() != null) {
            captain.setCrNumber(row.getCrNumber()); changed = true;
        }
        // Driving Licence
        if (isBlank(captain.getDrivingLicenceNo()) && row.getDrivingLicenceNo() != null) {
            captain.setDrivingLicenceNo(row.getDrivingLicenceNo()); changed = true;
        }
        if (isBlank(captain.getLicenceType()) && row.getLicenceType() != null) {
            captain.setLicenceType(row.getLicenceType()); changed = true;
        }
        if (captain.getLicenceExpiry() == null && row.getLicenceExpiry() != null) {
            captain.setLicenceExpiry(row.getLicenceExpiry()); changed = true;
        }
        if (isBlank(captain.getLicenceStatus()) && row.getLicenceStatus() != null) {
            captain.setLicenceStatus(row.getLicenceStatus()); changed = true;
        }
        // Insurance
        if (isBlank(captain.getInsuranceNumber()) && row.getInsuranceNumber() != null) {
            captain.setInsuranceNumber(row.getInsuranceNumber()); changed = true;
        }
        if (isBlank(captain.getInsuranceCategory()) && row.getInsuranceCategory() != null) {
            captain.setInsuranceCategory(row.getInsuranceCategory()); changed = true;
        }
        if (captain.getInsuranceIssueDate() == null && row.getInsuranceIssueDate() != null) {
            captain.setInsuranceIssueDate(row.getInsuranceIssueDate()); changed = true;
        }
        if (isBlank(captain.getInsuranceCompany()) && row.getInsuranceCompany() != null) {
            captain.setInsuranceCompany(row.getInsuranceCompany()); changed = true;
        }
        if (captain.getInsuranceExpiry() == null && row.getInsuranceExpiry() != null) {
            captain.setInsuranceExpiry(row.getInsuranceExpiry()); changed = true;
        }
        if (isBlank(captain.getInsuranceStatus()) && row.getInsuranceStatus() != null) {
            captain.setInsuranceStatus(row.getInsuranceStatus()); changed = true;
        }
        // Personal
        if (isBlank(captain.getBloodType()) && row.getBloodType() != null) {
            captain.setBloodType(row.getBloodType()); changed = true;
        }
        if (captain.getTrialPeriodDays() == null && row.getTrialPeriodDays() != null) {
            captain.setTrialPeriodDays(row.getTrialPeriodDays()); changed = true;
        }
        // Bank
        if (isBlank(captain.getBankAccountNumber()) && row.getBankAccountNumber() != null) {
            captain.setBankAccountNumber(row.getBankAccountNumber()); changed = true;
        }
        if (isBlank(captain.getBankName()) && row.getBankName() != null) {
            captain.setBankName(row.getBankName()); changed = true;
        }
        // Salary
        if (captain.getBasicSalary() == null && row.getBasicSalary() != null) {
            captain.setBasicSalary(row.getBasicSalary()); changed = true;
        }
        if (captain.getHousingAllowance() == null && row.getHousingAllowance() != null) {
            captain.setHousingAllowance(row.getHousingAllowance()); changed = true;
        }
        if (captain.getTransportationAllowance() == null && row.getTransportationAllowance() != null) {
            captain.setTransportationAllowance(row.getTransportationAllowance()); changed = true;
        }
        if (captain.getFuelAllowanceAmount() == null && row.getFuelAllowanceAmount() != null) {
            captain.setFuelAllowanceAmount(row.getFuelAllowanceAmount()); changed = true;
        }
        if (captain.getCallInternetAllowance() == null && row.getCallInternetAllowance() != null) {
            captain.setCallInternetAllowance(row.getCallInternetAllowance()); changed = true;
        }
        if (captain.getRewardAllowance() == null && row.getRewardAllowance() != null) {
            captain.setRewardAllowance(row.getRewardAllowance()); changed = true;
        }
        if (captain.getSalaryDeductions() == null && row.getSalaryDeductions() != null) {
            captain.setSalaryDeductions(row.getSalaryDeductions()); changed = true;
        }
        if (captain.getNetSalary() == null && row.getNetSalary() != null) {
            captain.setNetSalary(row.getNetSalary()); changed = true;
        }
        // Work Info
        if (isBlank(captain.getWorkPhone()) && row.getWorkPhone() != null) {
            captain.setWorkPhone(row.getWorkPhone()); changed = true;
        }
        if (isBlank(captain.getDepartment()) && row.getDepartment() != null) {
            captain.setDepartment(row.getDepartment()); changed = true;
        }
        if (isBlank(captain.getSection()) && row.getSection() != null) {
            captain.setSection(row.getSection()); changed = true;
        }
        if (isBlank(captain.getSupervisor()) && row.getSupervisor() != null) {
            captain.setSupervisor(row.getSupervisor()); changed = true;
        }
        if (isBlank(captain.getWorkLocation()) && row.getWorkLocation() != null) {
            captain.setWorkLocation(row.getWorkLocation()); changed = true;
        }
        if (isBlank(captain.getWorkWith()) && row.getWorkWith() != null) {
            captain.setWorkWith(row.getWorkWith()); changed = true;
        }
        // Notes
        if (isBlank(captain.getNotes()) && row.getNotes() != null) {
            captain.setNotes(row.getNotes()); changed = true;
        }

        return changed ? captainRepo.save(captain) : captain;
    }

    private Staff newStaff(ExcelCaptainRow row, String staffType) {
        String code = row.getKamalCode().trim().toUpperCase();
        String nameAr = (row.getCaptainName() != null && !row.getCaptainName().isBlank())
                ? row.getCaptainName().trim()
                : (row.getFullNameEn() != null && !row.getFullNameEn().isBlank()
                        ? row.getFullNameEn().trim()
                        : code);

        Staff.StaffBuilder builder = Staff.builder()
                .staffCode(code)
                .staffType(staffType)
                .fullNameAr(nameAr)
                .fullNameEn(row.getFullNameEn())
                .gender(row.getGender())
                .phone(row.getPhone())
                .nationality(row.getNationality())
                .dateOfBirth(row.getDateOfBirth())
                .email(row.getEmail())
                .placeOfResidence(row.getPlaceOfResidence())
                .homeAddress(row.getHomeAddress())
                .jobTitle(row.getJobTitle())
                .appId(row.getAppId())
                // Passport
                .passportNumber(row.getPassportNumber())
                .passportIssueDate(row.getPassportIssueDate())
                .passportExpiry(row.getPassportExpiry())
                .passportStatus(row.getPassportStatus())
                // Iqama
                .iqamaNumber(row.getIqamaNumber())
                .iqamaOccupation(row.getIqamaOccupation())
                .iqamaExpiry(row.getIqamaExpiry())
                .iqamaExpiryRenewed(row.getIqamaExpiryRenewed())
                .iqamaStatus(row.getIqamaStatus())
                // Qiwa
                .qiwaContractNumber(row.getQiwaContractNumber())
                .establishmentNumber(row.getEstablishmentNumber())
                .qiwaContractStart(row.getQiwaContractStart())
                .qiwaContractEnd(row.getQiwaContractEnd())
                .qiwaContractStatus(row.getQiwaContractStatus())
                // Work Permit
                .workPermitExpiry(row.getWorkPermitExpiry())
                .workPermitStatus(row.getWorkPermitStatus())
                // Company
                .affiliatedCompany(row.getAffiliatedCompany())
                .crNumber(row.getCrNumber())
                // Driving Licence
                .hasLicence(row.isHasLicence())
                .licenceExpiry(row.getLicenceExpiry())
                .licenceStatus(row.getLicenceStatus())
                // Insurance
                .insuranceNumber(row.getInsuranceNumber())
                .insuranceCategory(row.getInsuranceCategory())
                .insuranceIssueDate(row.getInsuranceIssueDate())
                .insuranceCompany(row.getInsuranceCompany())
                .insuranceExpiry(row.getInsuranceExpiry())
                .insuranceStatus(row.getInsuranceStatus())
                // Personal
                .bloodType(row.getBloodType())
                .trialPeriodDays(row.getTrialPeriodDays())
                // Bank
                .bankAccountNumber(row.getBankAccountNumber())
                .bankName(row.getBankName())
                // Salary
                .basicSalary(row.getBasicSalary() != null ? row.getBasicSalary() : BigDecimal.ZERO)
                .housingAllowance(row.getHousingAllowance() != null ? row.getHousingAllowance() : BigDecimal.ZERO)
                .transportationAllowance(row.getTransportationAllowance() != null ? row.getTransportationAllowance() : BigDecimal.ZERO)
                .fuelAllowanceAmount(row.getFuelAllowanceAmount() != null ? row.getFuelAllowanceAmount() : BigDecimal.ZERO)
                .callInternetAllowance(row.getCallInternetAllowance() != null ? row.getCallInternetAllowance() : BigDecimal.ZERO)
                .rewardAllowance(row.getRewardAllowance() != null ? row.getRewardAllowance() : BigDecimal.ZERO)
                .otherAllowances(BigDecimal.ZERO)
                // Work Info
                .workPhone(row.getWorkPhone())
                .department(row.getDepartment())
                .section(row.getSection())
                .supervisor(row.getSupervisor())
                .workLocation(row.getWorkLocation())
                .workWith(row.getWorkWith());

        String status = row.getStatus();
        builder.status(status != null && !status.isBlank() ? status.toUpperCase() : "ACTIVE");
        builder.joinDate(row.getJoinDate() != null ? row.getJoinDate() : LocalDate.now());
        builder.leaveDate(row.getLeaveDate());
        builder.notes(row.getNotes());

        return builder.build();
    }

    private Staff fillMissingStaffDetails(Staff staff, ExcelCaptainRow row) {
        boolean changed = false;

        if (isBlank(staff.getFullNameAr()) && row.getCaptainName() != null && !row.getCaptainName().isBlank()) {
            staff.setFullNameAr(row.getCaptainName().trim()); changed = true;
        }
        if (isBlank(staff.getFullNameEn()) && row.getFullNameEn() != null) {
            staff.setFullNameEn(row.getFullNameEn()); changed = true;
        }
        if (isBlank(staff.getGender()) && row.getGender() != null) {
            staff.setGender(row.getGender()); changed = true;
        }
        if (isBlank(staff.getPhone()) && row.getPhone() != null) {
            staff.setPhone(row.getPhone()); changed = true;
        }
        if (isBlank(staff.getNationality()) && row.getNationality() != null) {
            staff.setNationality(row.getNationality()); changed = true;
        }
        if (staff.getDateOfBirth() == null && row.getDateOfBirth() != null) {
            staff.setDateOfBirth(row.getDateOfBirth()); changed = true;
        }
        if (isBlank(staff.getEmail()) && row.getEmail() != null) {
            staff.setEmail(row.getEmail()); changed = true;
        }
        if (isBlank(staff.getPlaceOfResidence()) && row.getPlaceOfResidence() != null) {
            staff.setPlaceOfResidence(row.getPlaceOfResidence()); changed = true;
        }
        if (isBlank(staff.getHomeAddress()) && row.getHomeAddress() != null) {
            staff.setHomeAddress(row.getHomeAddress()); changed = true;
        }
        if (isBlank(staff.getJobTitle()) && row.getJobTitle() != null) {
            staff.setJobTitle(row.getJobTitle()); changed = true;
        }
        if (isBlank(staff.getAppId()) && row.getAppId() != null) {
            staff.setAppId(row.getAppId()); changed = true;
        }
        // Passport
        if (isBlank(staff.getPassportNumber()) && row.getPassportNumber() != null) {
            staff.setPassportNumber(row.getPassportNumber()); changed = true;
        }
        if (staff.getPassportIssueDate() == null && row.getPassportIssueDate() != null) {
            staff.setPassportIssueDate(row.getPassportIssueDate()); changed = true;
        }
        if (staff.getPassportExpiry() == null && row.getPassportExpiry() != null) {
            staff.setPassportExpiry(row.getPassportExpiry()); changed = true;
        }
        if (isBlank(staff.getPassportStatus()) && row.getPassportStatus() != null) {
            staff.setPassportStatus(row.getPassportStatus()); changed = true;
        }
        // Iqama
        if (isBlank(staff.getIqamaNumber()) && row.getIqamaNumber() != null) {
            staff.setIqamaNumber(row.getIqamaNumber()); changed = true;
        }
        if (isBlank(staff.getIqamaOccupation()) && row.getIqamaOccupation() != null) {
            staff.setIqamaOccupation(row.getIqamaOccupation()); changed = true;
        }
        if (staff.getIqamaExpiry() == null && row.getIqamaExpiry() != null) {
            staff.setIqamaExpiry(row.getIqamaExpiry()); changed = true;
        }
        if (staff.getIqamaExpiryRenewed() == null && row.getIqamaExpiryRenewed() != null) {
            staff.setIqamaExpiryRenewed(row.getIqamaExpiryRenewed()); changed = true;
        }
        if (isBlank(staff.getIqamaStatus()) && row.getIqamaStatus() != null) {
            staff.setIqamaStatus(row.getIqamaStatus()); changed = true;
        }
        // Qiwa
        if (isBlank(staff.getQiwaContractNumber()) && row.getQiwaContractNumber() != null) {
            staff.setQiwaContractNumber(row.getQiwaContractNumber()); changed = true;
        }
        if (isBlank(staff.getEstablishmentNumber()) && row.getEstablishmentNumber() != null) {
            staff.setEstablishmentNumber(row.getEstablishmentNumber()); changed = true;
        }
        if (staff.getQiwaContractStart() == null && row.getQiwaContractStart() != null) {
            staff.setQiwaContractStart(row.getQiwaContractStart()); changed = true;
        }
        if (staff.getQiwaContractEnd() == null && row.getQiwaContractEnd() != null) {
            staff.setQiwaContractEnd(row.getQiwaContractEnd()); changed = true;
        }
        if (isBlank(staff.getQiwaContractStatus()) && row.getQiwaContractStatus() != null) {
            staff.setQiwaContractStatus(row.getQiwaContractStatus()); changed = true;
        }
        // Work Permit
        if (staff.getWorkPermitExpiry() == null && row.getWorkPermitExpiry() != null) {
            staff.setWorkPermitExpiry(row.getWorkPermitExpiry()); changed = true;
        }
        if (isBlank(staff.getWorkPermitStatus()) && row.getWorkPermitStatus() != null) {
            staff.setWorkPermitStatus(row.getWorkPermitStatus()); changed = true;
        }
        // Company
        if (isBlank(staff.getAffiliatedCompany()) && row.getAffiliatedCompany() != null) {
            staff.setAffiliatedCompany(row.getAffiliatedCompany()); changed = true;
        }
        if (isBlank(staff.getCrNumber()) && row.getCrNumber() != null) {
            staff.setCrNumber(row.getCrNumber()); changed = true;
        }
        // Driving Licence
        if (staff.getLicenceExpiry() == null && row.getLicenceExpiry() != null) {
            staff.setLicenceExpiry(row.getLicenceExpiry()); changed = true;
        }
        if (isBlank(staff.getLicenceStatus()) && row.getLicenceStatus() != null) {
            staff.setLicenceStatus(row.getLicenceStatus()); changed = true;
        }
        if (!staff.isHasLicence() && row.isHasLicence()) {
            staff.setHasLicence(true); changed = true;
        }
        // Insurance
        if (isBlank(staff.getInsuranceNumber()) && row.getInsuranceNumber() != null) {
            staff.setInsuranceNumber(row.getInsuranceNumber()); changed = true;
        }
        if (isBlank(staff.getInsuranceCategory()) && row.getInsuranceCategory() != null) {
            staff.setInsuranceCategory(row.getInsuranceCategory()); changed = true;
        }
        if (staff.getInsuranceIssueDate() == null && row.getInsuranceIssueDate() != null) {
            staff.setInsuranceIssueDate(row.getInsuranceIssueDate()); changed = true;
        }
        if (staff.getInsuranceExpiry() == null && row.getInsuranceExpiry() != null) {
            staff.setInsuranceExpiry(row.getInsuranceExpiry()); changed = true;
        }
        if (isBlank(staff.getInsuranceCompany()) && row.getInsuranceCompany() != null) {
            staff.setInsuranceCompany(row.getInsuranceCompany()); changed = true;
        }
        if (isBlank(staff.getInsuranceStatus()) && row.getInsuranceStatus() != null) {
            staff.setInsuranceStatus(row.getInsuranceStatus()); changed = true;
        }
        // Personal
        if (isBlank(staff.getBloodType()) && row.getBloodType() != null) {
            staff.setBloodType(row.getBloodType()); changed = true;
        }
        if (staff.getTrialPeriodDays() == null && row.getTrialPeriodDays() != null) {
            staff.setTrialPeriodDays(row.getTrialPeriodDays()); changed = true;
        }
        // Bank
        if (isBlank(staff.getBankAccountNumber()) && row.getBankAccountNumber() != null) {
            staff.setBankAccountNumber(row.getBankAccountNumber()); changed = true;
        }
        if (isBlank(staff.getBankName()) && row.getBankName() != null) {
            staff.setBankName(row.getBankName()); changed = true;
        }
        // Salary
        if ((staff.getBasicSalary() == null || staff.getBasicSalary().compareTo(BigDecimal.ZERO) == 0) && row.getBasicSalary() != null) {
            staff.setBasicSalary(row.getBasicSalary()); changed = true;
        }
        if ((staff.getHousingAllowance() == null || staff.getHousingAllowance().compareTo(BigDecimal.ZERO) == 0) && row.getHousingAllowance() != null) {
            staff.setHousingAllowance(row.getHousingAllowance()); changed = true;
        }
        if ((staff.getTransportationAllowance() == null || staff.getTransportationAllowance().compareTo(BigDecimal.ZERO) == 0) && row.getTransportationAllowance() != null) {
            staff.setTransportationAllowance(row.getTransportationAllowance()); changed = true;
        }
        if ((staff.getFuelAllowanceAmount() == null || staff.getFuelAllowanceAmount().compareTo(BigDecimal.ZERO) == 0) && row.getFuelAllowanceAmount() != null) {
            staff.setFuelAllowanceAmount(row.getFuelAllowanceAmount()); changed = true;
        }
        if ((staff.getCallInternetAllowance() == null || staff.getCallInternetAllowance().compareTo(BigDecimal.ZERO) == 0) && row.getCallInternetAllowance() != null) {
            staff.setCallInternetAllowance(row.getCallInternetAllowance()); changed = true;
        }
        if ((staff.getRewardAllowance() == null || staff.getRewardAllowance().compareTo(BigDecimal.ZERO) == 0) && row.getRewardAllowance() != null) {
            staff.setRewardAllowance(row.getRewardAllowance()); changed = true;
        }
        // Work Info
        if (isBlank(staff.getWorkPhone()) && row.getWorkPhone() != null) {
            staff.setWorkPhone(row.getWorkPhone()); changed = true;
        }
        if (isBlank(staff.getDepartment()) && row.getDepartment() != null) {
            staff.setDepartment(row.getDepartment()); changed = true;
        }
        if (isBlank(staff.getSection()) && row.getSection() != null) {
            staff.setSection(row.getSection()); changed = true;
        }
        if (isBlank(staff.getSupervisor()) && row.getSupervisor() != null) {
            staff.setSupervisor(row.getSupervisor()); changed = true;
        }
        if (isBlank(staff.getWorkLocation()) && row.getWorkLocation() != null) {
            staff.setWorkLocation(row.getWorkLocation()); changed = true;
        }
        if (isBlank(staff.getWorkWith()) && row.getWorkWith() != null) {
            staff.setWorkWith(row.getWorkWith()); changed = true;
        }
        if (isBlank(staff.getNotes()) && row.getNotes() != null) {
            staff.setNotes(row.getNotes()); changed = true;
        }

        return changed ? staffRepo.save(staff) : staff;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ImportIssue issue(int row, String severity, String code, String message) {
        return ImportIssue.builder()
                .rowNumber(row)
                .severity(severity)
                .issueCode(code)
                .message(message)
                .build();
    }
}
