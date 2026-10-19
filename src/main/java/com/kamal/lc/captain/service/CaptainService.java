package com.kamal.lc.captain.service;

import com.kamal.lc.captain.dto.CaptainDetailResponse;
import com.kamal.lc.captain.dto.CaptainLookupResponse;
import com.kamal.lc.captain.dto.CaptainRequest;
import com.kamal.lc.captain.dto.CaptainResponse;
import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.entity.CaptainDocument;
import com.kamal.lc.captain.expiry.DocumentAlert;
import com.kamal.lc.captain.expiry.DocumentAlertEvaluator;
import com.kamal.lc.captain.expiry.DocumentKind;
import com.kamal.lc.captain.expiry.DocumentStatus;
import com.kamal.lc.captain.repository.CaptainDocumentRepository;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.changelog.service.ChangeLogWriter;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CaptainService {

    private final CaptainRepository captainRepo;
    private final CaptainDocumentRepository documentRepo;
    private final SupplierIdAssignmentRepository assignmentRepo;
    private final CaptainCodeGenerator codeGenerator;
    private final DocumentAlertEvaluator documentAlerts;
    private final com.kamal.lc.auth.service.CaptainAllocationService allocationService;
    private final com.kamal.lc.payroll.repository.PayrollLineRepository payrollLineRepo;
    private final com.kamal.lc.loan.repository.LoanRepository loanRepo;
    private final ChangeLogWriter changeLogWriter;

    /** Arabic labels for captain fields — used by the changelog writer. */
    private static final Map<String, String> CAPTAIN_FIELD_LABELS = Map.ofEntries(
            Map.entry("fullNameAr", "الاسم بالعربي"),
            Map.entry("fullNameEn", "الاسم بالإنجليزي"),
            Map.entry("captainType", "نوع الكابتن"),
            Map.entry("gender", "الجنس"),
            Map.entry("nationality", "الجنسية"),
            Map.entry("phone", "رقم الهاتف"),
            Map.entry("email", "البريد الإلكتروني"),
            Map.entry("status", "الحالة"),
            Map.entry("passportNumber", "رقم الجواز"),
            Map.entry("passportExpiry", "انتهاء الجواز"),
            Map.entry("passportStatus", "حالة الجواز"),
            Map.entry("iqamaNumber", "رقم الإقامة"),
            Map.entry("iqamaExpiry", "انتهاء الإقامة"),
            Map.entry("iqamaStatus", "حالة الإقامة"),
            Map.entry("qiwaContractStatus", "حالة عقد قوى"),
            Map.entry("workPermitExpiry", "انتهاء تصريح العمل"),
            Map.entry("workPermitStatus", "حالة تصريح العمل"),
            Map.entry("licenceStatus", "حالة رخصة القيادة"),
            Map.entry("insuranceStatus", "حالة التأمين"),
            Map.entry("basicSalary", "الراتب الأساسي"),
            Map.entry("housingAllowance", "بدل السكن"),
            Map.entry("transportationAllowance", "بدل المواصلات"),
            Map.entry("usesOwnVehicle", "يستخدم سيارته"),
            Map.entry("bankAccountNumber", "رقم الحساب البنكي"),
            Map.entry("bankName", "اسم البنك"),
            Map.entry("borderNumber", "رقم الحدود"),
            Map.entry("idExpiryDate", "انتهاء الهوية"),
            Map.entry("licenceType", "نوع الرخصة"),
            Map.entry("salaryDeductions", "الخصومات"),
            Map.entry("netSalary", "صافي الراتب")
    );

    /** Creates a captain; the kamal code is generated here and never taken from the client. */
    @Transactional
    public CaptainDetailResponse create(CaptainRequest request) {
        Captain captain = new Captain();
        captain.setKamalCode(codeGenerator.generate(request.getCaptainType()));
        captain.setCaptainType(normalizeType(request.getCaptainType()));
        applyRequest(captain, request);

        return getCaptainDetail(captainRepo.save(captain).getId());
    }

    /** Updates every editable field. The kamal code stays as issued. */
    @Transactional
    public CaptainDetailResponse update(UUID id, CaptainRequest request) {
        Captain captain = captainRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));

        // Snapshot before update
        Map<String, Object> before = ChangeLogWriter.snapshot(captain, CAPTAIN_FIELD_LABELS);

        captain.setCaptainType(normalizeType(request.getCaptainType()));
        applyRequest(captain, request);
        captainRepo.save(captain);

        // Log changes
        Map<String, Object> after = ChangeLogWriter.snapshot(captain, CAPTAIN_FIELD_LABELS);
        String label = captain.getFullNameAr() + " (" + captain.getKamalCode() + ")";
        changeLogWriter.logFieldChanges("CAPTAIN", captain.getId(), label,
                before, after, CAPTAIN_FIELD_LABELS, "نظام");

        return getCaptainDetail(id);
    }

    @Transactional
    public void deleteCaptain(UUID id) {
        Captain captain = captainRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));

        if (payrollLineRepo.existsByCaptainId(id)) {
            throw new BusinessException("HAS_DEPENDENCIES",
                    "لا يمكن حذف الكابتن لارتباطه بمسيرات رواتب سابقة، يمكنك تحويل حالته إلى غير نشط بدلاً من الحذف");
        }
        if (loanRepo.findActiveLoanBalanceByCaptainId(id).compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new BusinessException("HAS_DEPENDENCIES",
                    "لا يمكن حذف الكابتن لوجود سُلف/قروض نشطة غير مسددة عليه");
        }

        assignmentRepo.deleteByCaptainId(id);
        documentRepo.deleteByCaptainId(id);
        captainRepo.delete(captain);
    }

    /** Preview of the code that would be issued next — not reserved. */
    public String previewNextCode(String captainType) {
        return codeGenerator.generate(captainType);
    }

    public List<CaptainLookupResponse> lookup(String query) {
        List<UUID> accessibleIds = allocationService.getCurrentUserAccessibleCaptainIds();
        if (accessibleIds != null && accessibleIds.isEmpty()) {
            return List.of();
        }

        Pageable topTen = PageRequest.of(0, 10, Sort.by("fullNameAr"));
        Page<Captain> page;
        if (accessibleIds != null) {
            page = (query != null && !query.trim().isEmpty())
                    ? captainRepo.searchAndIdIn(query.trim(), accessibleIds, topTen)
                    : captainRepo.findByIdIn(accessibleIds, topTen);
        } else {
            page = (query != null && !query.trim().isEmpty())
                    ? captainRepo.search(query.trim(), topTen)
                    : captainRepo.findAll(topTen);
        }

        return page.getContent().stream().map(c -> CaptainLookupResponse.builder()
                .id(c.getId())
                .kamalCode(c.getKamalCode())
                .fullNameAr(c.getFullNameAr())
                .captainType(c.getCaptainType())
                .build()
        ).toList();
    }

    private void applyRequest(Captain captain, CaptainRequest r) {
        captain.setFullNameAr(r.getFullNameAr());
        captain.setFullNameEn(blankToNull(r.getFullNameEn()));
        captain.setGender(blankToNull(r.getGender()));
        captain.setNationality(blankToNull(r.getNationality()));
        captain.setDateOfBirth(r.getDateOfBirth());
        captain.setPhone(blankToNull(r.getPhone()));
        captain.setEmail(blankToNull(r.getEmail()));
        captain.setPlaceOfResidence(blankToNull(r.getPlaceOfResidence()));
        captain.setHomeAddress(blankToNull(r.getHomeAddress()));
        captain.setJobTitle(blankToNull(r.getJobTitle()));
        captain.setAppId(blankToNull(r.getAppId()));

        // Passport
        captain.setPassportNumber(blankToNull(r.getPassportNumber()));
        captain.setPassportIssueDate(r.getPassportIssueDate());
        captain.setPassportExpiry(r.getPassportExpiry());
        captain.setPassportStatus(blankToNull(r.getPassportStatus()));

        // Iqama
        captain.setIqamaNumber(blankToNull(r.getIqamaNumber()));
        captain.setIqamaOccupation(blankToNull(r.getIqamaOccupation()));
        captain.setIdExpiryDate(r.getIdExpiryDate());
        captain.setIqamaExpiry(r.getIqamaExpiry());
        captain.setIqamaExpiryRenewed(r.getIqamaExpiryRenewed());
        captain.setBorderNumber(blankToNull(r.getBorderNumber()));
        captain.setIqamaStatus(blankToNull(r.getIqamaStatus()));

        // Qiwa
        captain.setQiwaContractNumber(blankToNull(r.getQiwaContractNumber()));
        captain.setEstablishmentNumber(blankToNull(r.getEstablishmentNumber()));
        captain.setQiwaContractStart(r.getQiwaContractStart());
        captain.setQiwaContractEnd(r.getQiwaContractEnd());
        captain.setQiwaContractStatus(blankToNull(r.getQiwaContractStatus()));

        // Work Permit
        captain.setWorkPermitExpiry(r.getWorkPermitExpiry());
        captain.setWorkPermitStatus(blankToNull(r.getWorkPermitStatus()));

        // Company
        captain.setAffiliatedCompany(blankToNull(r.getAffiliatedCompany()));
        captain.setCrNumber(blankToNull(r.getCrNumber()));

        // Driving Licence
        captain.setDrivingLicenceNo(blankToNull(r.getDrivingLicenceNo()));
        captain.setLicenceType(blankToNull(r.getLicenceType()));
        captain.setLicenceExpiry(r.getLicenceExpiry());
        captain.setHasLicence(r.isHasLicence());
        captain.setLicenceStatus(blankToNull(r.getLicenceStatus()));

        // Insurance
        captain.setInsuranceNumber(blankToNull(r.getInsuranceNumber()));
        captain.setInsuranceCategory(blankToNull(r.getInsuranceCategory()));
        captain.setInsuranceIssueDate(r.getInsuranceIssueDate());
        captain.setInsuranceCompany(blankToNull(r.getInsuranceCompany()));
        captain.setInsuranceExpiry(r.getInsuranceExpiry());
        captain.setInsuranceStatus(blankToNull(r.getInsuranceStatus()));

        // Other Personal
        captain.setBloodType(blankToNull(r.getBloodType()));
        captain.setTrialPeriodDays(r.getTrialPeriodDays());

        // Bank
        captain.setBankAccountNumber(blankToNull(r.getBankAccountNumber()));
        captain.setBankName(blankToNull(r.getBankName()));

        // Salary & Allowances
        captain.setBasicSalary(r.getBasicSalary() != null ? r.getBasicSalary() : java.math.BigDecimal.ZERO);
        captain.setHousingAllowance(r.getHousingAllowance() != null ? r.getHousingAllowance() : java.math.BigDecimal.ZERO);
        captain.setTransportationAllowance(r.getTransportationAllowance() != null ? r.getTransportationAllowance() : java.math.BigDecimal.ZERO);
        captain.setFuelAllowanceAmount(r.getFuelAllowanceAmount() != null ? r.getFuelAllowanceAmount() : java.math.BigDecimal.ZERO);
        captain.setCallInternetAllowance(r.getCallInternetAllowance() != null ? r.getCallInternetAllowance() : java.math.BigDecimal.ZERO);
        captain.setRewardAllowance(r.getRewardAllowance() != null ? r.getRewardAllowance() : java.math.BigDecimal.ZERO);
        captain.setSalaryDeductions(r.getSalaryDeductions() != null ? r.getSalaryDeductions() : java.math.BigDecimal.ZERO);

        if (r.getNetSalary() != null) {
            captain.setNetSalary(r.getNetSalary());
        } else {
            java.math.BigDecimal gross = captain.getBasicSalary()
                    .add(captain.getHousingAllowance())
                    .add(captain.getTransportationAllowance())
                    .add(captain.getFuelAllowanceAmount())
                    .add(captain.getCallInternetAllowance())
                    .add(captain.getRewardAllowance());
            captain.setNetSalary(gross.subtract(captain.getSalaryDeductions()));
        }

        // Vehicle & Fuel flags
        captain.setUsesOwnVehicle(r.isUsesOwnVehicle());
        captain.setFuelAllowanceEligible(r.getFuelAllowanceEligible() != null
                ? r.getFuelAllowanceEligible()
                : r.isUsesOwnVehicle());

        // Work Info
        captain.setWorkPhone(blankToNull(r.getWorkPhone()));
        captain.setDepartment(blankToNull(r.getDepartment()));
        captain.setSection(blankToNull(r.getSection()));
        captain.setSupervisor(blankToNull(r.getSupervisor()));
        captain.setWorkLocation(blankToNull(r.getWorkLocation()));
        captain.setWorkWith(blankToNull(r.getWorkWith()));

        // Status
        captain.setStatus(r.getStatus() != null && !r.getStatus().isBlank()
                ? r.getStatus().toUpperCase() : "ACTIVE");
        captain.setJoinDate(r.getJoinDate());
        captain.setLeaveDate(r.getLeaveDate());
        captain.setNotes(blankToNull(r.getNotes()));
    }

    private String normalizeType(String type) {
        // Rejects anything but EMPLOYEE/FREELANCE with an Arabic message.
        codeGenerator.prefixFor(type);
        return type.toUpperCase();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * A page of captains, optionally narrowed to those whose documents raised an alert.
     */
    public Page<CaptainResponse> getCaptains(String search, String type, String alert, Pageable pageable) {
        List<UUID> accessibleIds = allocationService.getCurrentUserAccessibleCaptainIds();
        if (accessibleIds != null && accessibleIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Page<Captain> page;

        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasType = type != null && !type.trim().isEmpty();
        boolean hasAlert = alert != null && !alert.trim().isEmpty();

        if (accessibleIds != null) {
            if (hasAlert) {
                Set<UUID> alerting = alertingCaptainIds(alert.trim());
                alerting.retainAll(accessibleIds);
                if (alerting.isEmpty()) {
                    return Page.empty(pageable);
                }
                page = captainRepo.findByIdIn(alerting, pageable);
            } else if (hasSearch && hasType) {
                page = captainRepo.searchByTypeAndIdIn(search.trim(), type, accessibleIds, pageable);
            } else if (hasSearch) {
                page = captainRepo.searchAndIdIn(search.trim(), accessibleIds, pageable);
            } else if (hasType) {
                page = captainRepo.findByIdInAndCaptainType(accessibleIds, type, pageable);
            } else {
                page = captainRepo.findByIdIn(accessibleIds, pageable);
            }
        } else {
            if (hasAlert) {
                Set<UUID> alerting = alertingCaptainIds(alert.trim());
                if (alerting.isEmpty()) {
                    return Page.empty(pageable);
                }
                page = captainRepo.findByIdIn(alerting, pageable);
            } else if (hasSearch && hasType) {
                page = captainRepo.searchByType(search.trim(), type, pageable);
            } else if (hasSearch) {
                page = captainRepo.search(search.trim(), pageable);
            } else if (hasType) {
                page = captainRepo.findByCaptainType(type, pageable);
            } else {
                page = captainRepo.findAll(pageable);
            }
        }

        LocalDate today = LocalDate.now();
        Map<DocumentKind, Integer> alertDays = documentAlerts.alertDays(today);

        // Batch-load active ninja IDs for all captains in this page (avoids N+1 queries)
        List<UUID> pageIds = page.getContent().stream().map(Captain::getId).toList();
        List<SupplierIdAssignment> allAssignments = pageIds.isEmpty()
                ? List.of()
                : assignmentRepo.findByCaptainIdIn(pageIds);

        // Build lookup: captainId → current ninja ID (active = validTo IS NULL)
        Map<UUID, String> ninjaIdByCaptain = allAssignments.stream()
                .filter(a -> a.getValidTo() == null)
                .collect(java.util.stream.Collectors.toMap(
                        a -> a.getCaptain().getId(),
                        a -> a.getSupplierIdSlot().getExternalId(),
                        (existing, replacement) -> existing  // keep first if duplicates
                ));

        return page.map(c -> toResponse(c, today, alertDays, ninjaIdByCaptain.get(c.getId())));
    }

    /** Active captains matching an alert filter — {@code ANY}, or one status name. */
    private Set<UUID> alertingCaptainIds(String alert) {
        DocumentStatus wanted = "ANY".equalsIgnoreCase(alert) ? null : parseAlert(alert);

        LocalDate today = LocalDate.now();
        Map<DocumentKind, Integer> alertDays = documentAlerts.alertDays(today);

        return captainRepo.findByStatus("ACTIVE").stream()
                .filter(c -> documentAlerts.evaluate(c, today, alertDays).stream()
                        .anyMatch(a -> wanted == null ? a.isAlert() : a.status() == wanted))
                .map(Captain::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private DocumentStatus parseAlert(String alert) {
        try {
            return DocumentStatus.valueOf(alert.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_FILTER", "فلتر الإنذار غير صالح: " + alert);
        }
    }

    public CaptainDetailResponse getCaptainDetail(UUID id) {
        Captain captain = captainRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));

        List<SupplierIdAssignment> assignments = assignmentRepo.findByCaptainIdOrderByValidFromDesc(id);
        List<CaptainDocument> documents = documentRepo.findByCaptainId(id);
        List<DocumentAlert> alerts = documentAlerts.isTracked(captain)
                ? documentAlerts.evaluate(captain, LocalDate.now())
                : List.of();

        return CaptainDetailResponse.builder()
                .id(captain.getId())
                .kamalCode(captain.getKamalCode())
                .captainType(captain.getCaptainType())
                .fullNameAr(captain.getFullNameAr())
                .fullNameEn(captain.getFullNameEn())
                .gender(captain.getGender())
                .nationality(captain.getNationality())
                .dateOfBirth(captain.getDateOfBirth() != null ? captain.getDateOfBirth().toString() : null)
                .phone(captain.getPhone())
                .email(captain.getEmail())
                .placeOfResidence(captain.getPlaceOfResidence())
                .homeAddress(captain.getHomeAddress())
                .jobTitle(captain.getJobTitle())
                .appId(captain.getAppId())
                // Passport
                .passportNumber(captain.getPassportNumber())
                .passportIssueDate(captain.getPassportIssueDate() != null ? captain.getPassportIssueDate().toString() : null)
                .passportExpiry(captain.getPassportExpiry() != null ? captain.getPassportExpiry().toString() : null)
                .passportStatus(captain.getPassportStatus())
                // Iqama
                .iqamaNumber(captain.getIqamaNumber())
                .iqamaOccupation(captain.getIqamaOccupation())
                .idExpiryDate(captain.getIdExpiryDate() != null ? captain.getIdExpiryDate().toString() : null)
                .iqamaExpiry(captain.getIqamaExpiry() != null ? captain.getIqamaExpiry().toString() : null)
                .iqamaExpiryRenewed(captain.getIqamaExpiryRenewed() != null ? captain.getIqamaExpiryRenewed().toString() : null)
                .borderNumber(captain.getBorderNumber())
                .iqamaStatus(captain.getIqamaStatus())
                // Qiwa
                .qiwaContractNumber(captain.getQiwaContractNumber())
                .establishmentNumber(captain.getEstablishmentNumber())
                .qiwaContractStart(captain.getQiwaContractStart() != null ? captain.getQiwaContractStart().toString() : null)
                .qiwaContractEnd(captain.getQiwaContractEnd() != null ? captain.getQiwaContractEnd().toString() : null)
                .qiwaContractStatus(captain.getQiwaContractStatus())
                // Work Permit
                .workPermitExpiry(captain.getWorkPermitExpiry() != null ? captain.getWorkPermitExpiry().toString() : null)
                .workPermitStatus(captain.getWorkPermitStatus())
                // Company
                .affiliatedCompany(captain.getAffiliatedCompany())
                .crNumber(captain.getCrNumber())
                // Driving Licence
                .drivingLicenceNo(captain.getDrivingLicenceNo())
                .licenceType(captain.getLicenceType())
                .licenceExpiry(captain.getLicenceExpiry() != null ? captain.getLicenceExpiry().toString() : null)
                .hasLicence(captain.isHasLicence())
                .licenceStatus(captain.getLicenceStatus())
                // Insurance
                .insuranceNumber(captain.getInsuranceNumber())
                .insuranceCategory(captain.getInsuranceCategory())
                .insuranceIssueDate(captain.getInsuranceIssueDate() != null ? captain.getInsuranceIssueDate().toString() : null)
                .insuranceCompany(captain.getInsuranceCompany())
                .insuranceExpiry(captain.getInsuranceExpiry() != null ? captain.getInsuranceExpiry().toString() : null)
                .insuranceStatus(captain.getInsuranceStatus())
                // Other Personal
                .bloodType(captain.getBloodType())
                .trialPeriodDays(captain.getTrialPeriodDays())
                // Bank
                .bankAccountNumber(captain.getBankAccountNumber())
                .bankName(captain.getBankName())
                // Salary & Allowances
                .basicSalary(captain.getBasicSalary())
                .housingAllowance(captain.getHousingAllowance())
                .transportationAllowance(captain.getTransportationAllowance())
                .fuelAllowanceAmount(captain.getFuelAllowanceAmount())
                .callInternetAllowance(captain.getCallInternetAllowance())
                .rewardAllowance(captain.getRewardAllowance())
                .salaryDeductions(captain.getSalaryDeductions())
                .netSalary(captain.getNetSalary())
                // Vehicle flags
                .usesOwnVehicle(captain.isUsesOwnVehicle())
                .fuelAllowanceEligible(captain.isFuelAllowanceEligible())
                // Work Info
                .workPhone(captain.getWorkPhone())
                .department(captain.getDepartment())
                .section(captain.getSection())
                .supervisor(captain.getSupervisor())
                .workLocation(captain.getWorkLocation())
                .workWith(captain.getWorkWith())
                // Status
                .status(captain.getStatus())
                .joinDate(captain.getJoinDate() != null ? captain.getJoinDate().toString() : null)
                .leaveDate(captain.getLeaveDate() != null ? captain.getLeaveDate().toString() : null)
                .notes(captain.getNotes())
                .documentStatuses(alerts.stream()
                        .map(CaptainService::toDocumentStatusInfo)
                        .toList())
                .worstDocumentStatus(documentAlerts.worst(alerts).name())
                .supplierIds(assignments.stream().map(a -> CaptainDetailResponse.SupplierIdInfo.builder()
                        .slotId(a.getSupplierIdSlot().getId())
                        .externalId(a.getSupplierIdSlot().getExternalId())
                        .supplierName(a.getSupplierIdSlot().getSupplier().getName())
                        .supplierCode(a.getSupplierIdSlot().getSupplier().getCode())
                        .validFrom(a.getValidFrom().toString())
                        .validTo(a.getValidTo() != null ? a.getValidTo().toString() : null)
                        .build()
                ).toList())
                .documents(documents.stream().map(d -> CaptainDetailResponse.DocumentInfo.builder()
                        .id(d.getId())
                        .documentType(d.getDocumentType())
                        .fileName(d.getFileName())
                        .contentType(d.getContentType())
                        .fileSize(d.getFileSize())
                        .uploadedAt(d.getUploadedAt().toString())
                        .note(d.getNote())
                        .build()
                ).toList())
                .build();
    }

    /**
     * Maps a Captain to its list response DTO.
     * @param currentNinjaId pre-loaded ninja ID (may be null) — avoids N+1 query
     */
    private CaptainResponse toResponse(Captain c, LocalDate today,
                                        Map<DocumentKind, Integer> alertDays, String currentNinjaId) {
        List<DocumentAlert> alerts = documentAlerts.isTracked(c)
                ? documentAlerts.evaluate(c, today, alertDays)
                : List.of();

        return CaptainResponse.builder()
                .id(c.getId())
                .kamalCode(c.getKamalCode())
                .captainType(c.getCaptainType())
                .fullNameAr(c.getFullNameAr())
                .fullNameEn(c.getFullNameEn())
                .phone(c.getPhone())
                .nationality(c.getNationality())
                .iqamaNumber(c.getIqamaNumber())
                .borderNumber(c.getBorderNumber())
                .jobTitle(c.getJobTitle())
                .netSalary(c.getNetSalary())
                .status(c.getStatus())
                .currentNinjaId(currentNinjaId)
                .joinDate(c.getJoinDate() != null ? c.getJoinDate().toString() : null)
                .documentAlert(documentAlerts.worst(alerts).name())
                .documentAlertCount(documentAlerts.alertCount(alerts))
                .build();
    }

    private static CaptainDetailResponse.DocumentStatusInfo toDocumentStatusInfo(DocumentAlert alert) {
        return CaptainDetailResponse.DocumentStatusInfo.builder()
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
