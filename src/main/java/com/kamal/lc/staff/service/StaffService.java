package com.kamal.lc.staff.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.staff.dto.*;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffService {

    private final StaffRepository staffRepo;
    private final StaffCodeGenerator codeGenerator;
    private final StaffDocumentService documentService;
    private final com.kamal.lc.staff.repository.StaffDocumentRepository staffDocumentRepo;
    private final com.kamal.lc.staff.expiry.StaffDocumentAlertEvaluator documentAlerts;
    private final com.kamal.lc.payroll.repository.StaffPayrollLineRepository staffPayrollLineRepo;
    private final com.kamal.lc.payroll.repository.StaffAdjustmentRepository staffAdjustmentRepo;
    private final com.kamal.lc.loan.repository.LoanRepository loanRepo;
    private final com.kamal.lc.auth.repository.UserRepository userRepo;

    @Transactional
    public StaffDetailResponse create(StaffRequest request) {
        String type = normalizeType(request.getStaffType());
        Staff staff = new Staff();
        staff.setStaffCode(codeGenerator.generate(type));
        staff.setStaffType(type);
        applyRequest(staff, request);
        return toDetailResponse(staffRepo.save(staff));
    }

    @Transactional
    public StaffDetailResponse update(UUID id, StaffRequest request) {
        Staff staff = staffRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود"));
        staff.setStaffType(normalizeType(request.getStaffType()));
        applyRequest(staff, request);
        Staff saved = staffRepo.save(staff);
        staffPayrollLineRepo.updateJobTitleForDraftRuns(id, saved.getJobTitle());
        return toDetailResponse(saved);
    }

    public String previewNextCode(String staffType) {
        return codeGenerator.generate(staffType);
    }

    public Page<StaffResponse> list(String search, String type, String alert, Pageable pageable) {
        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasType = type != null && !type.trim().isEmpty();
        boolean hasAlert = alert != null && !alert.trim().isEmpty();

        LocalDate today = LocalDate.now();
        java.util.Map<com.kamal.lc.staff.expiry.StaffDocumentKind, Integer> alertDays = documentAlerts.alertDays(today);

        Page<Staff> page;
        if (hasAlert) {
            java.util.Set<UUID> alertingIds = alertingStaffIds(type, alert.trim(), today, alertDays);
            if (alertingIds.isEmpty()) {
                return Page.empty(pageable);
            }
            if (hasType) {
                page = staffRepo.findByStaffTypeAndIdIn(type.toUpperCase(), alertingIds, pageable);
            } else {
                page = staffRepo.findByIdIn(alertingIds, pageable);
            }
        } else if (hasSearch && hasType) {
            page = staffRepo.searchByType(search.trim(), type.toUpperCase(), pageable);
        } else if (hasSearch) {
            page = staffRepo.search(search.trim(), pageable);
        } else if (hasType) {
            page = staffRepo.findByStaffType(type.toUpperCase(), pageable);
        } else {
            page = staffRepo.findAll(pageable);
        }

        return page.map(s -> toResponse(s, today, alertDays));
    }

    private java.util.Set<UUID> alertingStaffIds(String type, String alert, LocalDate today,
                                                 java.util.Map<com.kamal.lc.staff.expiry.StaffDocumentKind, Integer> alertDays) {
        com.kamal.lc.captain.expiry.DocumentStatus wanted = "ANY".equalsIgnoreCase(alert) ? null : parseAlert(alert);
        List<Staff> activeStaff = (type != null && !type.trim().isEmpty())
                ? staffRepo.findByStaffTypeAndStatus(type.toUpperCase(), "ACTIVE")
                : staffRepo.findByStatus("ACTIVE");

        return activeStaff.stream()
                .filter(s -> documentAlerts.evaluate(s, today, alertDays).stream()
                        .anyMatch(a -> wanted == null ? a.isAlert() : a.status() == wanted))
                .map(Staff::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private com.kamal.lc.captain.expiry.DocumentStatus parseAlert(String alert) {
        try {
            return com.kamal.lc.captain.expiry.DocumentStatus.valueOf(alert.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_FILTER", "فلتر الإنذار غير صالح: " + alert);
        }
    }

    public StaffDetailResponse getDetail(UUID id) {
        Staff staff = staffRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود"));
        return toDetailResponse(staff);
    }

    @Transactional
    public void delete(UUID id) {
        Staff staff = staffRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود"));

        if (staffPayrollLineRepo.existsByStaffId(id)) {
            throw new BusinessException("HAS_DEPENDENCIES",
                    "لا يمكن حذف الموظف لارتباطه بمسيرات رواتب سابقة، يمكنك تحويل حالته إلى غير نشط بدلاً من الحذف");
        }
        if (loanRepo.findActiveLoanBalanceByStaffId(id).compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new BusinessException("HAS_DEPENDENCIES",
                    "لا يمكن حذف الموظف لوجود سُلف/قروض نشطة غير مسددة عليه");
        }

        userRepo.unlinkStaff(id);
        staffDocumentRepo.deleteByStaffId(id);
        loanRepo.deleteByStaffId(id);
        staffAdjustmentRepo.deleteByStaffId(id);
        staffPayrollLineRepo.deleteByStaffId(id);

        staffRepo.delete(staff);
    }

    // ---- helpers ----

    private String normalizeType(String type) {
        codeGenerator.prefixFor(type); // validates
        return type.toUpperCase();
    }

    private void applyRequest(Staff s, StaffRequest r) {
        s.setJobTitle(blank(r.getJobTitle()));
        s.setFullNameAr(r.getFullNameAr().trim());
        s.setFullNameEn(blank(r.getFullNameEn()));
        s.setGender(blank(r.getGender()));
        s.setNationality(blank(r.getNationality()));
        s.setDateOfBirth(r.getDateOfBirth());
        s.setPhone(blank(r.getPhone()));
        s.setPlaceOfResidence(blank(r.getPlaceOfResidence()));
        s.setHomeAddress(blank(r.getHomeAddress()));
        s.setEmail(blank(r.getEmail()));
        s.setAppId(blank(r.getAppId()));

        // Passport
        s.setPassportNumber(blank(r.getPassportNumber()));
        s.setPassportIssueDate(r.getPassportIssueDate());
        s.setPassportExpiry(r.getPassportExpiry());
        s.setPassportStatus(blank(r.getPassportStatus()));

        // Iqama
        s.setIqamaNumber(blank(r.getIqamaNumber()));
        s.setIqamaOccupation(blank(r.getIqamaOccupation()));
        s.setIqamaExpiry(r.getIqamaExpiry());
        s.setIqamaExpiryRenewed(r.getIqamaExpiryRenewed());
        s.setIqamaStatus(blank(r.getIqamaStatus()));

        // Qiwa
        s.setQiwaContractNumber(blank(r.getQiwaContractNumber()));
        s.setEstablishmentNumber(blank(r.getEstablishmentNumber()));
        s.setQiwaContractStart(r.getQiwaContractStart());
        s.setQiwaContractEnd(r.getQiwaContractEnd());
        s.setQiwaContractStatus(blank(r.getQiwaContractStatus()));

        // Work Permit
        s.setWorkPermitExpiry(r.getWorkPermitExpiry());
        s.setWorkPermitStatus(blank(r.getWorkPermitStatus()));

        // Company
        s.setAffiliatedCompany(blank(r.getAffiliatedCompany()));
        s.setCrNumber(blank(r.getCrNumber()));

        // Insurance
        s.setInsuranceNumber(blank(r.getInsuranceNumber()));
        s.setInsuranceCategory(blank(r.getInsuranceCategory()));
        s.setInsuranceIssueDate(r.getInsuranceIssueDate());
        s.setInsuranceExpiry(r.getInsuranceExpiry());
        s.setInsuranceCompany(blank(r.getInsuranceCompany()));
        s.setInsuranceStatus(blank(r.getInsuranceStatus()));

        // Trial & Licence
        s.setTrialPeriodDays(r.getTrialPeriodDays());
        s.setHasLicence(r.isHasLicence());
        s.setLicenceExpiry(r.getLicenceExpiry());
        s.setLicenceStatus(blank(r.getLicenceStatus()));

        // Other
        s.setBloodType(blank(r.getBloodType()));
        s.setBankAccountNumber(blank(r.getBankAccountNumber()));
        s.setBankName(blank(r.getBankName()));

        // Salary & Allowances
        s.setBasicSalary(r.getBasicSalary() != null ? r.getBasicSalary() : BigDecimal.ZERO);
        s.setHousingAllowance(r.getHousingAllowance() != null ? r.getHousingAllowance() : BigDecimal.ZERO);
        s.setOtherAllowances(r.getOtherAllowances() != null ? r.getOtherAllowances() : BigDecimal.ZERO);
        s.setTransportationAllowance(r.getTransportationAllowance() != null ? r.getTransportationAllowance() : BigDecimal.ZERO);
        s.setFuelAllowanceAmount(r.getFuelAllowanceAmount() != null ? r.getFuelAllowanceAmount() : BigDecimal.ZERO);
        s.setCallInternetAllowance(r.getCallInternetAllowance() != null ? r.getCallInternetAllowance() : BigDecimal.ZERO);
        s.setRewardAllowance(r.getRewardAllowance() != null ? r.getRewardAllowance() : BigDecimal.ZERO);

        // Work Info
        s.setWorkPhone(blank(r.getWorkPhone()));
        s.setDepartment(blank(r.getDepartment()));
        s.setSection(blank(r.getSection()));
        s.setSupervisor(blank(r.getSupervisor()));
        s.setWorkLocation(blank(r.getWorkLocation()));
        s.setWorkWith(blank(r.getWorkWith()));

        // Status
        s.setStatus(r.getStatus() != null && !r.getStatus().isBlank() ? r.getStatus().toUpperCase() : "ACTIVE");
        s.setJoinDate(r.getJoinDate());
        s.setLeaveDate(r.getLeaveDate());
        s.setNotes(blank(r.getNotes()));
    }

    private String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private String dateStr(LocalDate d) {
        return d != null ? d.toString() : null;
    }

    StaffResponse toResponse(Staff s, LocalDate today, java.util.Map<com.kamal.lc.staff.expiry.StaffDocumentKind, Integer> alertDays) {
        List<com.kamal.lc.staff.expiry.StaffDocumentAlert> alerts = documentAlerts.isTracked(s)
                ? documentAlerts.evaluate(s, today, alertDays)
                : List.of();

        return StaffResponse.builder()
                .id(s.getId())
                .staffCode(s.getStaffCode())
                .staffType(s.getStaffType())
                .jobTitle(s.getJobTitle())
                .fullNameAr(s.getFullNameAr())
                .fullNameEn(s.getFullNameEn())
                .phone(s.getPhone())
                .nationality(s.getNationality())
                .status(s.getStatus())
                .joinDate(dateStr(s.getJoinDate()))
                .iqamaExpiry(dateStr(s.getIqamaExpiry()))
                .passportExpiry(dateStr(s.getPassportExpiry()))
                .insuranceExpiry(dateStr(s.getInsuranceExpiry()))
                .workPermitExpiry(dateStr(s.getWorkPermitExpiry()))
                .documentAlert(documentAlerts.worst(alerts).name())
                .documentAlertCount(documentAlerts.alertCount(alerts))
                .build();
    }

    StaffDetailResponse toDetailResponse(Staff s) {
        LocalDate today = LocalDate.now();
        List<com.kamal.lc.staff.expiry.StaffDocumentAlert> alerts = documentAlerts.isTracked(s)
                ? documentAlerts.evaluate(s, today)
                : List.of();

        java.util.Map<com.kamal.lc.staff.expiry.StaffDocumentKind, com.kamal.lc.staff.expiry.StaffDocumentAlert> alertMap = alerts.stream()
                .collect(java.util.stream.Collectors.toMap(com.kamal.lc.staff.expiry.StaffDocumentAlert::kind, a -> a, (a1, a2) -> a1));

        String iqamaStatus = s.getIqamaStatus() != null ? s.getIqamaStatus()
                : (alertMap.containsKey(com.kamal.lc.staff.expiry.StaffDocumentKind.IQAMA) ? alertMap.get(com.kamal.lc.staff.expiry.StaffDocumentKind.IQAMA).status().getLabelAr() : null);
        String passportStatus = s.getPassportStatus() != null ? s.getPassportStatus()
                : (alertMap.containsKey(com.kamal.lc.staff.expiry.StaffDocumentKind.PASSPORT) ? alertMap.get(com.kamal.lc.staff.expiry.StaffDocumentKind.PASSPORT).status().getLabelAr() : null);
        String workPermitStatus = s.getWorkPermitStatus() != null ? s.getWorkPermitStatus()
                : (alertMap.containsKey(com.kamal.lc.staff.expiry.StaffDocumentKind.WORK_PERMIT) ? alertMap.get(com.kamal.lc.staff.expiry.StaffDocumentKind.WORK_PERMIT).status().getLabelAr() : null);
        String insuranceStatus = s.getInsuranceStatus() != null ? s.getInsuranceStatus()
                : (alertMap.containsKey(com.kamal.lc.staff.expiry.StaffDocumentKind.INSURANCE) ? alertMap.get(com.kamal.lc.staff.expiry.StaffDocumentKind.INSURANCE).status().getLabelAr() : null);
        String licenceStatus = s.getLicenceStatus() != null ? s.getLicenceStatus()
                : (alertMap.containsKey(com.kamal.lc.staff.expiry.StaffDocumentKind.DRIVING_LICENCE) ? alertMap.get(com.kamal.lc.staff.expiry.StaffDocumentKind.DRIVING_LICENCE).status().getLabelAr() : null);
        String qiwaContractStatus = s.getQiwaContractStatus() != null ? s.getQiwaContractStatus()
                : (alertMap.containsKey(com.kamal.lc.staff.expiry.StaffDocumentKind.QIWA_CONTRACT) ? alertMap.get(com.kamal.lc.staff.expiry.StaffDocumentKind.QIWA_CONTRACT).status().getLabelAr() : null);

        return StaffDetailResponse.builder()
                .id(s.getId())
                .staffCode(s.getStaffCode())
                .staffType(s.getStaffType())
                .jobTitle(s.getJobTitle())
                .fullNameAr(s.getFullNameAr())
                .fullNameEn(s.getFullNameEn())
                .gender(s.getGender())
                .nationality(s.getNationality())
                .dateOfBirth(dateStr(s.getDateOfBirth()))
                .phone(s.getPhone())
                .placeOfResidence(s.getPlaceOfResidence())
                .homeAddress(s.getHomeAddress())
                .email(s.getEmail())
                .appId(s.getAppId())
                .passportNumber(s.getPassportNumber())
                .passportIssueDate(dateStr(s.getPassportIssueDate()))
                .passportExpiry(dateStr(s.getPassportExpiry()))
                .passportStatus(passportStatus)
                .iqamaNumber(s.getIqamaNumber())
                .iqamaOccupation(s.getIqamaOccupation())
                .iqamaExpiry(dateStr(s.getIqamaExpiry()))
                .iqamaExpiryRenewed(dateStr(s.getIqamaExpiryRenewed()))
                .iqamaStatus(iqamaStatus)
                .qiwaContractNumber(s.getQiwaContractNumber())
                .establishmentNumber(s.getEstablishmentNumber())
                .qiwaContractStart(dateStr(s.getQiwaContractStart()))
                .qiwaContractEnd(dateStr(s.getQiwaContractEnd()))
                .qiwaContractStatus(qiwaContractStatus)
                .workPermitExpiry(dateStr(s.getWorkPermitExpiry()))
                .workPermitStatus(workPermitStatus)
                .affiliatedCompany(s.getAffiliatedCompany())
                .crNumber(s.getCrNumber())
                .insuranceNumber(s.getInsuranceNumber())
                .insuranceCategory(s.getInsuranceCategory())
                .insuranceIssueDate(dateStr(s.getInsuranceIssueDate()))
                .insuranceExpiry(dateStr(s.getInsuranceExpiry()))
                .insuranceCompany(s.getInsuranceCompany())
                .insuranceStatus(insuranceStatus)
                .trialPeriodDays(s.getTrialPeriodDays())
                .hasLicence(s.isHasLicence())
                .licenceExpiry(dateStr(s.getLicenceExpiry()))
                .licenceStatus(licenceStatus)
                .bloodType(s.getBloodType())
                .bankAccountNumber(s.getBankAccountNumber())
                .bankName(s.getBankName())
                .basicSalary(s.getBasicSalary())
                .housingAllowance(s.getHousingAllowance())
                .otherAllowances(s.getOtherAllowances())
                .transportationAllowance(s.getTransportationAllowance())
                .fuelAllowanceAmount(s.getFuelAllowanceAmount())
                .callInternetAllowance(s.getCallInternetAllowance())
                .rewardAllowance(s.getRewardAllowance())
                .workPhone(s.getWorkPhone())
                .department(s.getDepartment())
                .section(s.getSection())
                .supervisor(s.getSupervisor())
                .workLocation(s.getWorkLocation())
                .workWith(s.getWorkWith())
                .status(s.getStatus())
                .joinDate(dateStr(s.getJoinDate()))
                .leaveDate(dateStr(s.getLeaveDate()))
                .notes(s.getNotes())
                .documentStatuses(alerts.stream().map(StaffService::toDocumentStatusInfo).toList())
                .worstDocumentStatus(documentAlerts.worst(alerts).name())
                .documents(s.getId() != null ? documentService.listByStaff(s.getId()) : List.of())
                .build();
    }

    private static StaffDetailResponse.DocumentStatusInfo toDocumentStatusInfo(com.kamal.lc.staff.expiry.StaffDocumentAlert alert) {
        return StaffDetailResponse.DocumentStatusInfo.builder()
                .kind(alert.kind().name())
                .labelAr(alert.kind().getLabelAr())
                .number(alert.number())
                .expiry(alert.expiry() != null ? alert.expiry().toString() : null)
                .status(alert.status().name())
                .statusLabelAr(alert.status().getLabelAr())
                .daysLeft(alert.daysLeft())
                .build();
    }
}
