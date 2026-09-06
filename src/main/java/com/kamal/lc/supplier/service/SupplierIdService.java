package com.kamal.lc.supplier.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.supplier.dto.SupplierIdSlotRequest;
import com.kamal.lc.supplier.dto.SupplierIdSlotResponse;
import com.kamal.lc.supplier.dto.SupplierResponse;
import com.kamal.lc.supplier.entity.Supplier;
import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import com.kamal.lc.supplier.entity.SupplierIdSlot;
import com.kamal.lc.supplier.repository.SupplierIdAssignmentRepository;
import com.kamal.lc.supplier.repository.SupplierIdSlotRepository;
import com.kamal.lc.supplier.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierIdService {

    private final SupplierRepository supplierRepo;
    private final SupplierIdSlotRepository slotRepo;
    private final SupplierIdAssignmentRepository assignmentRepo;
    private final CaptainRepository captainRepo;

    public List<SupplierResponse> getAllSuppliers() {
        return supplierRepo.findAll().stream().map(s -> SupplierResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .code(s.getCode())
                .color(s.getColor())
                .active(s.isActive())
                .slotCount(slotRepo.countBySupplierId(s.getId()))
                .build()
        ).toList();
    }

    public Page<SupplierIdSlotResponse> getSlots(UUID supplierId, String q, Pageable pageable) {
        Page<SupplierIdSlot> page;
        if (q != null && !q.trim().isEmpty()) {
            page = slotRepo.searchSlots(supplierId, q.trim(), pageable);
        } else if (supplierId != null) {
            page = slotRepo.findBySupplierId(supplierId, pageable);
        } else {
            page = slotRepo.findAll(pageable);
        }
        return page.map(this::toSlotResponse);
    }

    @Transactional
    public void deleteSupplier(UUID supplierId) {
        Supplier supplier = supplierRepo.findById(supplierId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المورد غير موجود"));

        List<SupplierIdSlot> slots = slotRepo.findBySupplierId(supplierId);
        for (SupplierIdSlot slot : slots) {
            assignmentRepo.deleteBySupplierIdSlotId(slot.getId());
        }
        slotRepo.deleteAll(slots);
        supplierRepo.delete(supplier);
    }

    @Transactional
    public void deleteSlot(UUID slotId) {
        if (!slotRepo.existsById(slotId)) {
            throw new BusinessException("NOT_FOUND", "المعرف غير موجود");
        }
        assignmentRepo.deleteBySupplierIdSlotId(slotId);
        slotRepo.deleteById(slotId);
    }

    @Transactional
    public void deleteSlots(List<UUID> slotIds) {
        for (UUID id : slotIds) {
            deleteSlot(id);
        }
    }

    public List<AssignmentHistoryItem> getSlotHistory(UUID slotId) {
        if (!slotRepo.existsById(slotId)) {
            throw new BusinessException("NOT_FOUND", "المعرف غير موجود");
        }

        List<SupplierIdAssignment> assignments = assignmentRepo.findBySupplierIdSlotIdOrderByValidFromDesc(slotId);
        return assignments.stream().map(a -> AssignmentHistoryItem.builder()
                .assignmentId(a.getId())
                .captainId(a.getCaptain().getId())
                .captainName(a.getCaptain().getFullNameAr())
                .captainCode(a.getCaptain().getKamalCode())
                .validFrom(a.getValidFrom().toString())
                .validTo(a.getValidTo() != null ? a.getValidTo().toString() : null)
                .supplierSideName(a.getSupplierSideName())
                .reason(a.getReason())
                .build()
        ).toList();
    }

    @Transactional
    public SupplierResponse createSupplier(String name, String code, String color) {
        if (supplierRepo.existsByCode(code)) {
            throw new BusinessException("DUPLICATE_CODE", "كود المورد موجود بالفعل");
        }
        Supplier supplier = Supplier.builder()
                .name(name)
                .code(code.toUpperCase())
                .color(color != null ? color : "#4CAF50")
                .isActive(true)
                .build();
        supplier = supplierRepo.save(supplier);

        return SupplierResponse.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .code(supplier.getCode())
                .color(supplier.getColor())
                .active(true)
                .slotCount(0)
                .build();
    }

    /**
     * Registers a new supplier ID, optionally assigning it to a captain right away.
     *
     * <p>Mirrors the slot/assignment handling in {@code ImportService.commit}: any assignment
     * currently open on the slot is closed before a new one is opened, which is what the
     * {@code no_overlapping_assignments} exclusion constraint (V4) requires.
     */
    @Transactional
    public SupplierIdSlotResponse createSlot(SupplierIdSlotRequest request) {
        Supplier supplier = supplierRepo.findById(request.getSupplierId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المورد غير موجود"));

        String externalId = request.getExternalId().trim();
        if (slotRepo.existsByExternalIdAndSupplierId(externalId, supplier.getId())) {
            throw new BusinessException("DUPLICATE_ID", "هذا المعرف موجود بالفعل لدى هذا المورد");
        }

        SupplierIdSlot slot = slotRepo.save(SupplierIdSlot.builder()
                .externalId(externalId)
                .supplier(supplier)
                .isActive(true)
                .notes(blankToNull(request.getNotes()))
                .build());

        if (request.getCaptainId() != null) {
            Captain captain = captainRepo.findById(request.getCaptainId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));

            LocalDate validFrom = request.getValidFrom() != null ? request.getValidFrom() : LocalDate.now();

            // A freshly created slot cannot have an open assignment, but closing defensively keeps
            // this method safe to reuse if it ever runs against an existing slot.
            assignmentRepo.findBySupplierIdSlotIdAndValidToIsNull(slot.getId()).ifPresent(existing -> {
                existing.setValidTo(validFrom);
                assignmentRepo.save(existing);
            });

            assignmentRepo.save(SupplierIdAssignment.builder()
                    .supplierIdSlot(slot)
                    .captain(captain)
                    .validFrom(validFrom)
                    .supplierSideName(request.getSupplierSideName())
                    .reason(request.getReason())
                    .build());
        }

        return toSlotResponse(slot);
    }

    /** Slot picker for linking an existing ID to a captain. */
    public List<SupplierIdSlotResponse> lookupSlots(String query, UUID supplierId, boolean onlyAvailable) {
        String term = query == null ? "" : query.trim();
        // Over-fetch, then drop the taken ones — the available subset is what the picker shows.
        Pageable window = PageRequest.of(0, 60, Sort.by("externalId"));

        Page<SupplierIdSlot> page = (supplierId != null)
                ? slotRepo.findBySupplierIdAndExternalIdContainingIgnoreCase(supplierId, term, window)
                : slotRepo.findByExternalIdContainingIgnoreCase(term, window);

        return page.getContent().stream()
                .map(this::toSlotResponse)
                .filter(s -> !onlyAvailable || s.getCurrentCaptainId() == null)
                .limit(15)
                .toList();
    }

    /**
     * Links a slot to a captain from {@code validFrom} onwards, closing whoever holds it now.
     *
     * <p>The V4 exclusion constraint compares inclusive date ranges, so the outgoing assignment
     * must end the day <em>before</em> the new one starts — ending it on the same day would still
     * count as an overlap and the insert would be rejected by Postgres.
     */
    @Transactional
    public SupplierIdSlotResponse assign(UUID slotId, UUID captainId, LocalDate validFrom,
                                         String supplierSideName, String reason) {
        SupplierIdSlot slot = slotRepo.findById(slotId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المعرف غير موجود"));
        Captain captain = captainRepo.findById(captainId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));

        LocalDate from = validFrom != null ? validFrom : LocalDate.now();

        Optional<SupplierIdAssignment> open = assignmentRepo.findBySupplierIdSlotIdAndValidToIsNull(slotId);
        if (open.isPresent()) {
            SupplierIdAssignment current = open.get();
            if (current.getCaptain().getId().equals(captainId)) {
                throw new BusinessException("ALREADY_ASSIGNED", "هذا المعرف مربوط بنفس الكابتن بالفعل");
            }
            if (!current.getValidFrom().isBefore(from)) {
                throw new BusinessException("INVALID_DATE",
                        "تاريخ بدء التخصيص يجب أن يكون بعد " + current.getValidFrom()
                                + " (تاريخ بدء التخصيص الحالي)");
            }
            current.setValidTo(from.minusDays(1));
            assignmentRepo.save(current);
        }

        // Guards against overlapping a historical period that was closed after this date.
        if (assignmentRepo.countOverlapping(slotId, from) > 0) {
            throw new BusinessException("OVERLAPPING_ASSIGNMENT",
                    "يوجد تخصيص سابق لهذا المعرف يتداخل مع التاريخ المختار");
        }

        assignmentRepo.save(SupplierIdAssignment.builder()
                .supplierIdSlot(slot)
                .captain(captain)
                .validFrom(from)
                .supplierSideName(blankToNull(supplierSideName))
                .reason(blankToNull(reason))
                .build());

        return toSlotResponse(slot);
    }

    /** Ends the slot's active assignment ("فك الربط") without deleting its history. */
    @Transactional
    public SupplierIdSlotResponse unassign(UUID slotId, LocalDate validTo, String reason) {
        SupplierIdSlot slot = slotRepo.findById(slotId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المعرف غير موجود"));

        SupplierIdAssignment current = assignmentRepo.findBySupplierIdSlotIdAndValidToIsNull(slotId)
                .orElseThrow(() -> new BusinessException("NOT_ASSIGNED", "هذا المعرف غير مربوط بأي كابتن"));

        LocalDate to = validTo != null ? validTo : LocalDate.now();
        if (to.isBefore(current.getValidFrom())) {
            throw new BusinessException("INVALID_DATE",
                    "تاريخ الفك يجب ألا يسبق تاريخ بدء التخصيص (" + current.getValidFrom() + ")");
        }

        current.setValidTo(to);
        if (reason != null && !reason.isBlank()) {
            current.setReason(reason.trim());
        }
        assignmentRepo.save(current);

        return toSlotResponse(slot);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private SupplierIdSlotResponse toSlotResponse(SupplierIdSlot slot) {
        SupplierIdSlotResponse.SupplierIdSlotResponseBuilder builder = SupplierIdSlotResponse.builder()
                .id(slot.getId())
                .externalId(slot.getExternalId())
                .supplierId(slot.getSupplier().getId())
                .supplierName(slot.getSupplier().getName())
                .supplierCode(slot.getSupplier().getCode())
                .supplierColor(slot.getSupplier().getColor())
                .active(slot.isActive());

        // Find current active assignment
        assignmentRepo.findBySupplierIdSlotIdAndValidToIsNull(slot.getId()).ifPresent(a -> {
            builder.currentCaptainName(a.getCaptain().getFullNameAr());
            builder.currentCaptainCode(a.getCaptain().getKamalCode());
            builder.currentCaptainId(a.getCaptain().getId());
            builder.assignedSince(a.getValidFrom().toString());
        });

        return builder.build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AssignmentHistoryItem {
        private UUID assignmentId;
        private UUID captainId;
        private String captainName;
        private String captainCode;
        private String validFrom;
        private String validTo;
        private String supplierSideName;
        private String reason;
    }
}
