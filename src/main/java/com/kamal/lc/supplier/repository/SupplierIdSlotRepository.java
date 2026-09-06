package com.kamal.lc.supplier.repository;

import com.kamal.lc.supplier.entity.SupplierIdSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierIdSlotRepository extends JpaRepository<SupplierIdSlot, UUID> {

    Optional<SupplierIdSlot> findByExternalIdAndSupplierId(String externalId, UUID supplierId);

    boolean existsByExternalIdAndSupplierId(String externalId, UUID supplierId);

    Page<SupplierIdSlot> findBySupplierId(UUID supplierId, Pageable pageable);

    List<SupplierIdSlot> findBySupplierId(UUID supplierId);

    @Query("SELECT COUNT(s) FROM SupplierIdSlot s WHERE NOT EXISTS " +
           "(SELECT a FROM SupplierIdAssignment a WHERE a.supplierIdSlot = s AND a.validTo IS NULL)")
    long countUnassigned();

    long countBySupplierId(UUID supplierId);

    Page<SupplierIdSlot> findByExternalIdContainingIgnoreCase(String externalId, Pageable pageable);

    Page<SupplierIdSlot> findBySupplierIdAndExternalIdContainingIgnoreCase(
            UUID supplierId, String externalId, Pageable pageable);

    @Query("SELECT DISTINCT s FROM SupplierIdSlot s " +
           "LEFT JOIN SupplierIdAssignment a ON a.supplierIdSlot = s AND a.validTo IS NULL " +
           "LEFT JOIN a.captain c " +
           "WHERE (:supplierId IS NULL OR s.supplier.id = :supplierId) " +
           "AND (:q IS NULL OR :q = '' OR LOWER(s.externalId) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(c.fullNameAr) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(c.kamalCode) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<SupplierIdSlot> searchSlots(@Param("supplierId") UUID supplierId,
                                     @Param("q") String q,
                                     Pageable pageable);
}
