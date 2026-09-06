package com.kamal.lc.supplier.repository;

import com.kamal.lc.supplier.entity.SupplierIdAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierIdAssignmentRepository extends JpaRepository<SupplierIdAssignment, UUID> {

    List<SupplierIdAssignment> findBySupplierIdSlotIdOrderByValidFromDesc(UUID slotId);

    List<SupplierIdAssignment> findByCaptainIdOrderByValidFromDesc(UUID captainId);

    /** Batch-load assignments for multiple captains to avoid N+1 queries during payroll generation. */
    @Query("SELECT a FROM SupplierIdAssignment a JOIN FETCH a.supplierIdSlot WHERE a.captain.id IN :captainIds")
    List<SupplierIdAssignment> findByCaptainIdIn(@Param("captainIds") Collection<UUID> captainIds);

    void deleteBySupplierIdSlotId(UUID slotId);

    void deleteByCaptainId(UUID captainId);

    /** Find the currently active assignment for a slot (valid_to IS NULL). */
    Optional<SupplierIdAssignment> findBySupplierIdSlotIdAndValidToIsNull(UUID slotId);

    /** Check if a slot is currently assigned to a specific captain. */
    boolean existsBySupplierIdSlotIdAndCaptainIdAndValidToIsNull(UUID slotId, UUID captainId);

    /**
     * Who held this slot on {@code date}.
     *
     * <p>This — not the currently-open assignment — is what shift imports must use. A
     * supplier ID gets handed to a new captain when the old one leaves, and yesterday's
     * shifts still belong to whoever was holding it yesterday.
     */
    @Query("SELECT a FROM SupplierIdAssignment a WHERE a.supplierIdSlot.id = :slotId "
            + "AND a.validFrom <= :date AND (a.validTo IS NULL OR a.validTo >= :date)")
    Optional<SupplierIdAssignment> findActiveOn(@Param("slotId") UUID slotId,
                                                @Param("date") LocalDate date);

    /**
     * Counts assignments that would overlap a new open-ended period starting at {@code from}.
     * Mirrors the {@code no_overlapping_assignments} exclusion constraint (V4), whose ranges are
     * inclusive on both ends — so an assignment ending exactly on {@code from} still overlaps.
     */
    @Query("SELECT COUNT(a) FROM SupplierIdAssignment a WHERE a.supplierIdSlot.id = :slotId "
            + "AND (a.validTo IS NULL OR a.validTo >= :from)")
    long countOverlapping(@Param("slotId") UUID slotId, @Param("from") LocalDate from);
}
