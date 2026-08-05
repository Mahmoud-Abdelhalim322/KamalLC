package com.kamal.lc.supplier.repository;

import com.kamal.lc.supplier.entity.SupplierAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SupplierAdjustmentRepository extends JpaRepository<SupplierAdjustment, UUID> {

    List<SupplierAdjustment> findBySupplierIdAndAdjustmentDateBetweenOrderByAdjustmentDateDesc(
            UUID supplierId, LocalDate from, LocalDate to);

    List<SupplierAdjustment> findByAdjustmentDateBetweenOrderByAdjustmentDateDesc(
            LocalDate from, LocalDate to);

    @Query("SELECT a FROM SupplierAdjustment a WHERE a.supplier.id = :supplierId ORDER BY a.adjustmentDate DESC")
    List<SupplierAdjustment> findBySupplierId(@Param("supplierId") UUID supplierId);
}
