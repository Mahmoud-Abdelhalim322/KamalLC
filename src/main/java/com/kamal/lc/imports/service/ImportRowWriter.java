package com.kamal.lc.imports.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.imports.dto.ExcelCaptainRow;
import com.kamal.lc.imports.dto.ImportPreviewResponse.ImportIssue;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import com.kamal.lc.supplier.entity.SupplierIdSlot;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import com.kamal.lc.supplier.repository.SupplierIdSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writes one captain row, in a transaction of its own.
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

        Captain captain = captainRepo.findByKamalCode(row.getKamalCode())
                .map(existing -> fillMissingDetails(existing, row))
                .orElseGet(() -> captainRepo.save(newCaptain(row)));

        SupplierIdSlot slot = slotRepo.findByExternalIdAndSupplierId(row.getNinjaId(), supplier.getId())
                .orElseGet(() -> slotRepo.save(SupplierIdSlot.builder()
                        .externalId(row.getNinjaId())
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

            // A link that already starts today cannot be closed to make room for another:
            // the ranges would still touch, and one ID genuinely cannot belong to two
            // captains on the same day. In practice this is the same ID listed twice in
            // one sheet, so the first row keeps it and this one is reported.
            if (!current.getValidFrom().isBefore(today)) {
                issues.add(issue(row.getRowNumber(), "WARNING", "DUPLICATE_NINJA_ID",
                        "معرف نينجا " + row.getNinjaId() + " مربوط بالفعل بكابتن "
                                + current.getCaptain().getFullNameAr()
                                + " — لم يتم ربطه بـ " + captain.getFullNameAr()
                                + "، المعرف مكرر في الملف"));
                return issues;
            }

            // The constraint's ranges are inclusive at both ends, so the old period has to
            // end the day before the new one starts or the two still overlap.
            current.setValidTo(today.minusDays(1));
            assignmentRepo.save(current);
            issues.add(issue(row.getRowNumber(), "INFO", "REASSIGNED",
                    "تم نقل معرف " + row.getNinjaId() + " من " + current.getCaptain().getFullNameAr()));
        }

        assignmentRepo.save(SupplierIdAssignment.builder()
                .supplierIdSlot(slot)
                .captain(captain)
                .validFrom(today)
                .build());

        return issues;
    }

    private Captain newCaptain(ExcelCaptainRow row) {
        String type = row.getKamalCode().toUpperCase().startsWith("KSCFL") ? "FREELANCE" : "EMPLOYEE";
        return Captain.builder()
                .kamalCode(row.getKamalCode())
                .captainType(type)
                .fullNameAr(row.getCaptainName())
                .phone(row.getPhone())
                .nationality(row.getNationality())
                .dateOfBirth(row.getDateOfBirth())
                .email(row.getEmail())
                .iqamaNumber(row.getIqamaNumber())
                .iqamaExpiry(row.getIqamaExpiry())
                .passportNumber(row.getPassportNumber())
                .passportExpiry(row.getPassportExpiry())
                .drivingLicenceNo(row.getDrivingLicenceNo())
                .licenceExpiry(row.getLicenceExpiry())
                .workPermitExpiry(row.getWorkPermitExpiry())
                .insuranceNumber(row.getInsuranceNumber())
                .insuranceCompany(row.getInsuranceCompany())
                .insuranceExpiry(row.getInsuranceExpiry())
                .status("ACTIVE")
                .joinDate(LocalDate.now())
                .build();
    }

    /**
     * Fills in details the sheet carries but the stored captain is missing. Values already
     * captured in the app win — an import must never overwrite data entered by hand.
     */
    private Captain fillMissingDetails(Captain captain, ExcelCaptainRow row) {
        boolean changed = false;

        if (isBlank(captain.getPhone()) && row.getPhone() != null) {
            captain.setPhone(row.getPhone());
            changed = true;
        }
        if (isBlank(captain.getNationality()) && row.getNationality() != null) {
            captain.setNationality(row.getNationality());
            changed = true;
        }
        if (captain.getDateOfBirth() == null && row.getDateOfBirth() != null) {
            captain.setDateOfBirth(row.getDateOfBirth());
            changed = true;
        }
        if (isBlank(captain.getEmail()) && row.getEmail() != null) {
            captain.setEmail(row.getEmail());
            changed = true;
        }
        if (isBlank(captain.getIqamaNumber()) && row.getIqamaNumber() != null) {
            captain.setIqamaNumber(row.getIqamaNumber());
            changed = true;
        }
        if (captain.getIqamaExpiry() == null && row.getIqamaExpiry() != null) {
            captain.setIqamaExpiry(row.getIqamaExpiry());
            changed = true;
        }
        if (isBlank(captain.getPassportNumber()) && row.getPassportNumber() != null) {
            captain.setPassportNumber(row.getPassportNumber());
            changed = true;
        }
        if (captain.getPassportExpiry() == null && row.getPassportExpiry() != null) {
            captain.setPassportExpiry(row.getPassportExpiry());
            changed = true;
        }
        if (isBlank(captain.getDrivingLicenceNo()) && row.getDrivingLicenceNo() != null) {
            captain.setDrivingLicenceNo(row.getDrivingLicenceNo());
            changed = true;
        }
        if (captain.getLicenceExpiry() == null && row.getLicenceExpiry() != null) {
            captain.setLicenceExpiry(row.getLicenceExpiry());
            changed = true;
        }
        if (captain.getWorkPermitExpiry() == null && row.getWorkPermitExpiry() != null) {
            captain.setWorkPermitExpiry(row.getWorkPermitExpiry());
            changed = true;
        }
        if (isBlank(captain.getInsuranceNumber()) && row.getInsuranceNumber() != null) {
            captain.setInsuranceNumber(row.getInsuranceNumber());
            changed = true;
        }
        if (isBlank(captain.getInsuranceCompany()) && row.getInsuranceCompany() != null) {
            captain.setInsuranceCompany(row.getInsuranceCompany());
            changed = true;
        }
        if (captain.getInsuranceExpiry() == null && row.getInsuranceExpiry() != null) {
            captain.setInsuranceExpiry(row.getInsuranceExpiry());
            changed = true;
        }

        return changed ? captainRepo.save(captain) : captain;
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
