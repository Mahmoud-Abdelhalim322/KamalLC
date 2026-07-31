package com.kamal.lc.payroll.repository;

import com.kamal.lc.payroll.entity.CaptainAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CaptainAdjustmentRepository extends JpaRepository<CaptainAdjustment, UUID> {

    /**
     * Adjustments that fall inside a payroll period. Matching on containment (rather than
     * overlap) keeps a single adjustment from being counted in two runs.
     */
    @Query("SELECT a FROM CaptainAdjustment a WHERE a.periodFrom >= :from AND a.periodTo <= :to "
            + "ORDER BY a.captain.id, a.adjustmentType")
    List<CaptainAdjustment> findInPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT a FROM CaptainAdjustment a WHERE a.captain.id = :captainId "
            + "AND a.periodFrom >= :from AND a.periodTo <= :to ORDER BY a.createdAt")
    List<CaptainAdjustment> findForCaptainInPeriod(@Param("captainId") UUID captainId,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);
}
