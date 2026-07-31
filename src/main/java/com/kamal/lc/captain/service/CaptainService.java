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

        captain.setCaptainType(normalizeType(request.getCaptainType()));
        applyRequest(captain, request);
        captainRepo.save(captain);

        return getCaptainDetail(id);
    }

    /** Preview of the code that would be issued next — not reserved. */
    public String previewNextCode(String captainType) {
        return codeGenerator.generate(captainType);
    }

    public List<CaptainLookupResponse> lookup(String query) {
        Pageable topTen = PageRequest.of(0, 10, Sort.by("fullNameAr"));
        Page<Captain> page = (query != null && !query.trim().isEmpty())
                ? captainRepo.search(query.trim(), topTen)
                : captainRepo.findAll(topTen);

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
        captain.setNationality(blankToNull(r.getNationality()));
        captain.setDateOfBirth(r.getDateOfBirth());
        captain.setPhone(blankToNull(r.getPhone()));
        captain.setEmail(blankToNull(r.getEmail()));
        captain.setIqamaNumber(blankToNull(r.getIqamaNumber()));
        captain.setIqamaExpiry(r.getIqamaExpiry());
        captain.setPassportNumber(blankToNull(r.getPassportNumber()));
        captain.setPassportExpiry(r.getPassportExpiry());
        captain.setDrivingLicenceNo(blankToNull(r.getDrivingLicenceNo()));
        captain.setLicenceExpiry(r.getLicenceExpiry());
        captain.setWorkPermitExpiry(r.getWorkPermitExpiry());
        captain.setInsuranceNumber(blankToNull(r.getInsuranceNumber()));
        captain.setInsuranceCompany(blankToNull(r.getInsuranceCompany()));
        captain.setInsuranceExpiry(r.getInsuranceExpiry());
        captain.setUsesOwnVehicle(r.isUsesOwnVehicle());
        // A captain driving his own vehicle carries the fuel cost, so eligibility follows the
        // vehicle flag by default. Sending it explicitly still wins, which is how the rare
        // exception (own vehicle without the allowance, or the reverse) is recorded.
        captain.setFuelAllowanceEligible(r.getFuelAllowanceEligible() != null
                ? r.getFuelAllowanceEligible()
                : r.isUsesOwnVehicle());
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
     *
     * <p>{@code alert} is {@code ANY} for every kind of problem, or one
     * {@link DocumentStatus} name to see just the expired, just the expiring, or just the
     * ones nobody has recorded a date for.
     *
     * <p>{@code alert} takes precedence over {@code search} and {@code type}: the screen
     * offers them as one row of mutually exclusive tabs, so they never arrive together.
     */
    public Page<CaptainResponse> getCaptains(String search, String type, String alert, Pageable pageable) {
        Page<Captain> page;

        boolean hasSearch = search != null && !search.trim().isEmpty();
        boolean hasType = type != null && !type.trim().isEmpty();
        boolean hasAlert = alert != null && !alert.trim().isEmpty();

        if (hasAlert) {
            // The evaluator decides who is alerting, then the page is taken from that set —
            // so this filter cannot disagree with the badge it filters on, and paging still
            // counts what it should.
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

        LocalDate today = LocalDate.now();
        Map<DocumentKind, Integer> alertDays = documentAlerts.alertDays(today);
        return page.map(c -> toResponse(c, today, alertDays));
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
                .nationality(captain.getNationality())
                .dateOfBirth(captain.getDateOfBirth() != null ? captain.getDateOfBirth().toString() : null)
                .phone(captain.getPhone())
                .email(captain.getEmail())
                .iqamaNumber(captain.getIqamaNumber())
                .iqamaExpiry(captain.getIqamaExpiry() != null ? captain.getIqamaExpiry().toString() : null)
                .passportNumber(captain.getPassportNumber())
                .passportExpiry(captain.getPassportExpiry() != null ? captain.getPassportExpiry().toString() : null)
                .drivingLicenceNo(captain.getDrivingLicenceNo())
                .licenceExpiry(captain.getLicenceExpiry() != null ? captain.getLicenceExpiry().toString() : null)
                .workPermitExpiry(captain.getWorkPermitExpiry() != null
                        ? captain.getWorkPermitExpiry().toString() : null)
                .insuranceNumber(captain.getInsuranceNumber())
                .insuranceCompany(captain.getInsuranceCompany())
                .insuranceExpiry(captain.getInsuranceExpiry() != null
                        ? captain.getInsuranceExpiry().toString() : null)
                .usesOwnVehicle(captain.isUsesOwnVehicle())
                .fuelAllowanceEligible(captain.isFuelAllowanceEligible())
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

    private CaptainResponse toResponse(Captain c, LocalDate today, Map<DocumentKind, Integer> alertDays) {
        // Find current ninja ID for this captain
        List<SupplierIdAssignment> activeAssignments = assignmentRepo.findByCaptainIdOrderByValidFromDesc(c.getId());
        String currentNinjaId = activeAssignments.stream()
                .filter(a -> a.getValidTo() == null)
                .map(a -> a.getSupplierIdSlot().getExternalId())
                .findFirst()
                .orElse(null);

        List<DocumentAlert> alerts = documentAlerts.isTracked(c)
                ? documentAlerts.evaluate(c, today, alertDays)
                : List.of();

        return CaptainResponse.builder()
                .id(c.getId())
                .kamalCode(c.getKamalCode())
                .captainType(c.getCaptainType())
                .fullNameAr(c.getFullNameAr())
                .phone(c.getPhone())
                .nationality(c.getNationality())
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
