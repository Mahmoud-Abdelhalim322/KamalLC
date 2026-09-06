package com.kamal.lc.payroll.repository;

import com.kamal.lc.payroll.entity.StaffAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StaffAdjustmentRepository extends JpaRepository<StaffAdjustment, UUID> {

    @Query("SELECT a FROM StaffAdjustment a WHERE a.periodFrom >= :from AND a.periodTo <= :to "
            + "ORDER BY a.staff.id, a.adjustmentType")
    List<StaffAdjustment> findInPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT a FROM StaffAdjustment a WHERE a.staff.id = :staffId "
            + "AND a.periodFrom >= :from AND a.periodTo <= :to ORDER BY a.createdAt")
    List<StaffAdjustment> findForStaffInPeriod(@Param("staffId") UUID staffId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /** Deletes system-generated adjustments for a payroll run (e.g., automatic LOAN deductions). */
    void deleteByPayrollRunIdAndCreatedBy(UUID payrollRunId, String createdBy);

    @Modifying
    @Query("DELETE FROM StaffAdjustment sa WHERE sa.staff.id = :staffId AND (sa.tenant.id = :#{T(com.kamal.lc.common.security.TenantContext).getCurrentTenantId()} OR :#{T(com.kamal.lc.common.security.TenantContext).getCurrentTenantId()} IS NULL)")
    void deleteByStaffId(@Param("staffId") UUID staffId);
}
