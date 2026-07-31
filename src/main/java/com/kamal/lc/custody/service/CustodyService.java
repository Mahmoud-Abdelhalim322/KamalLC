package com.kamal.lc.custody.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.custody.dto.CaptainCustodySummary;
import com.kamal.lc.custody.dto.CustodyRecordResponse;
import com.kamal.lc.custody.entity.CustodyRecord;
import com.kamal.lc.custody.repository.CustodyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Custody service — the unified ledger that tracks every asset (vehicle, SIM, phone,
 * custom item) assigned to a captain. Other modules call into this service when they
 * assign or return assets; the custody page reads from it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CustodyService {

    private final CustodyRepository custodyRepo;

    // ──────────────────────────── Write operations (called by Fleet / Inventory) ────

    /** Record that an asset was assigned to a captain. */
    public CustodyRecord recordAssignment(Captain captain, String assetType, UUID referenceId,
                                           String assetLabel, String assignedBy) {
        CustodyRecord record = CustodyRecord.builder()
                .captain(captain)
                .assetType(assetType)
                .referenceId(referenceId)
                .assetLabel(assetLabel)
                .assignedAt(OffsetDateTime.now())
                .status("ACTIVE")
                .assignedBy(assignedBy)
                .build();
        return custodyRepo.save(record);
    }

    /** Mark an asset custody as returned. */
    public void recordReturn(UUID referenceId, String returnedBy) {
        CustodyRecord record = custodyRepo.findByReferenceIdAndStatus(referenceId, "ACTIVE")
                .orElseThrow(() -> new BusinessException("CUSTODY_NOT_FOUND",
                        "لم يتم العثور على سجل عهدة نشط لهذا العنصر"));
        record.setReturnedAt(OffsetDateTime.now());
        record.setStatus("RETURNED");
        record.setReturnedBy(returnedBy);
        custodyRepo.save(record);
    }

    // ──────────────────────────── Read operations (Custody page) ────

    @Transactional(readOnly = true)
    public List<CustodyRecordResponse> getActiveCustody(UUID captainId) {
        return custodyRepo.findByCaptainIdAndStatus(captainId, "ACTIVE")
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<CustodyRecordResponse> getCustodyHistory(UUID captainId, Pageable pageable) {
        return custodyRepo.findByCaptainId(captainId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CustodyRecordResponse> getItemCustodyHistory(UUID referenceId, Pageable pageable) {
        return custodyRepo.findByReferenceId(referenceId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CustodyRecordResponse> getAllActive(Pageable pageable) {
        return custodyRepo.findByStatus("ACTIVE", pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<CustodyRecordResponse> getByAssetType(String assetType, Pageable pageable) {
        return custodyRepo.findByAssetType(assetType, pageable).map(this::toResponse);
    }

    /**
     * Summary cards for the custody dashboard — one card per captain holding any active items.
     */
    @Transactional(readOnly = true)
    public List<CaptainCustodySummary> getCaptainSummaries() {
        // Get all active custody records grouped by captain
        List<CustodyRecord> allActive = custodyRepo.findByStatus("ACTIVE",
                Pageable.unpaged()).getContent();

        Map<UUID, List<CustodyRecord>> byCaptain = allActive.stream()
                .collect(Collectors.groupingBy(r -> r.getCaptain().getId()));

        return byCaptain.entrySet().stream().map(entry -> {
            List<CustodyRecord> records = entry.getValue();
            CustodyRecord first = records.get(0);

            long vehicles = records.stream().filter(r -> "VEHICLE".equals(r.getAssetType())).count();
            long sims = records.stream().filter(r -> "SIM_CARD".equals(r.getAssetType())).count();
            long phones = records.stream().filter(r -> "PHONE".equals(r.getAssetType())).count();
            long others = records.size() - vehicles - sims - phones;

            return CaptainCustodySummary.builder()
                    .captainId(first.getCaptain().getId())
                    .captainCode(first.getCaptain().getKamalCode())
                    .captainName(first.getCaptain().getFullNameAr())
                    .activeCount(records.size())
                    .vehicleCount(vehicles)
                    .simCount(sims)
                    .phoneCount(phones)
                    .otherCount(others)
                    .activeItems(records.stream().map(this::toResponse).toList())
                    .build();
        }).sorted(Comparator.comparing(CaptainCustodySummary::getCaptainName))
          .toList();
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return custodyRepo.countByStatus("ACTIVE");
    }

    @Transactional(readOnly = true)
    public long countActiveByCaptain(UUID captainId) {
        return custodyRepo.countByCaptainIdAndStatus(captainId, "ACTIVE");
    }

    // ──────────────────────────── Mapper ────

    private CustodyRecordResponse toResponse(CustodyRecord r) {
        return CustodyRecordResponse.builder()
                .id(r.getId())
                .captainId(r.getCaptain().getId())
                .captainName(r.getCaptain().getFullNameAr())
                .captainCode(r.getCaptain().getKamalCode())
                .assetType(r.getAssetType())
                .assetTypeLabelAr(assetTypeLabel(r.getAssetType()))
                .referenceId(r.getReferenceId())
                .assetLabel(r.getAssetLabel())
                .assignedAt(r.getAssignedAt().toString())
                .returnedAt(r.getReturnedAt() != null ? r.getReturnedAt().toString() : null)
                .status(r.getStatus())
                .assignedBy(r.getAssignedBy())
                .returnedBy(r.getReturnedBy())
                .notes(r.getNotes())
                .build();
    }

    private String assetTypeLabel(String type) {
        return switch (type) {
            case "VEHICLE" -> "سيارة";
            case "SIM_CARD" -> "شريحة";
            case "PHONE" -> "هاتف";
            default -> type;
        };
    }
}
