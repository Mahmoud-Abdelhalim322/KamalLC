package com.kamal.lc.payroll.repository;

import com.kamal.lc.payroll.entity.StaffAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
