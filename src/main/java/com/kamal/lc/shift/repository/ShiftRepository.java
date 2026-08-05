package com.kamal.lc.shift.repository;

import com.kamal.lc.shift.entity.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID>, JpaSpecificationExecutor<Shift> {

    /**
     * Natural key from the supplier file — used to make re-imports idempotent. The date is
     * part of it because the supplier reuses a shift id across days: without it, a captain's
     * second shift on the same slot overwrites the first.
     */
    Optional<Shift> findByExternalShiftIdAndNinjaCaptainIdAndShiftDate(
            String externalShiftId, String ninjaCaptainId, LocalDate shiftDate);

    List<Shift> findByCaptainIdAndShiftDateBetweenOrderByShiftDateAsc(
            UUID captainId, LocalDate from, LocalDate to);

    /** Used by supplier deduction import to match by Ninja captain ID + date. */
    List<Shift> findByNinjaCaptainIdAndShiftDate(String ninjaCaptainId, LocalDate shiftDate);

    /** Match by captain entity ID + shift date. */
    List<Shift> findByCaptainIdAndShiftDate(UUID captainId, LocalDate shiftDate);

    List<Shift> findByShiftDateBetween(LocalDate from, LocalDate to);

    @Query("SELECT s FROM Shift s WHERE s.captain IS NULL ORDER BY s.shiftDate DESC")
    Page<Shift> findUnmatched(Pageable pageable);

    @Query("SELECT COUNT(s) FROM Shift s WHERE s.captain IS NULL")
    long countUnmatched();

    @Query("SELECT COUNT(s) FROM Shift s WHERE s.shiftDate BETWEEN :from AND :to")
    long countBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Distinct zones present in the data, for the shifts-page filter. */
    @Query("SELECT DISTINCT s.zoneName FROM Shift s WHERE s.zoneName IS NOT NULL ORDER BY s.zoneName")
    List<String> findDistinctZones();

    /**
     * Per-captain totals for a period — the input to a payroll run and to the team report.
     * Excluded shifts are filtered out here so they never reach either.
     */
    @Query("""
            SELECT s.captain.id                                        AS captainId,
                   SUM(CASE WHEN s.attended = true THEN 1 ELSE 0 END)   AS attendedShifts,
                   SUM(CASE WHEN s.attended = true THEN 0 ELSE 1 END)   AS absentShifts,
                   COALESCE(SUM(s.totalDelivered), 0)                   AS totalOrders,
                   COALESCE(SUM(s.workingHours), 0)                     AS totalHours,
                   AVG(s.busyUtilization)                               AS avgBusyUtilization,
                   AVG(s.hourlyUtr)                                     AS avgHourlyUtr,
                   AVG(s.deliveryOntime)                                AS avgDeliveryOntime,
                   COALESCE(SUM(s.lateDeduction), 0)                    AS lateDeductions,
                   COALESCE(SUM(s.supplierDeduction), 0)                 AS supplierDeductions,
                   COALESCE(SUM(s.dailyIncentive), 0)                   AS dailyIncentives,
                   COALESCE(SUM(s.fuelAllowance), 0)                    AS fuelAllowances,
                   COALESCE(SUM(s.carAllowance), 0)                     AS carAllowances,
                   COALESCE(SUM(s.supplierRevenue), 0)
                     + COALESCE(SUM(s.supplierExtraRevenue), 0)         AS supplierRevenueTotal
            FROM Shift s
            WHERE s.captain IS NOT NULL
              AND s.excluded = false
              AND s.shiftDate BETWEEN :from AND :to
            GROUP BY s.captain.id
            """)
    List<CaptainPeriodTotals> aggregateByCaptain(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT s.captain.id                                        AS captainId,
                   SUM(CASE WHEN s.attended = true THEN 1 ELSE 0 END)   AS attendedShifts,
                   SUM(CASE WHEN s.attended = true THEN 0 ELSE 1 END)   AS absentShifts,
                   COALESCE(SUM(s.totalDelivered), 0)                   AS totalOrders,
                   COALESCE(SUM(s.workingHours), 0)                     AS totalHours,
                   AVG(s.busyUtilization)                               AS avgBusyUtilization,
                   AVG(s.hourlyUtr)                                     AS avgHourlyUtr,
                   AVG(s.deliveryOntime)                                AS avgDeliveryOntime,
                   COALESCE(SUM(s.lateDeduction), 0)                    AS lateDeductions,
                   COALESCE(SUM(s.supplierDeduction), 0)                 AS supplierDeductions,
                   COALESCE(SUM(s.dailyIncentive), 0)                   AS dailyIncentives,
                   COALESCE(SUM(s.fuelAllowance), 0)                    AS fuelAllowances,
                   COALESCE(SUM(s.carAllowance), 0)                     AS carAllowances,
                   COALESCE(SUM(s.supplierRevenue), 0)
                     + COALESCE(SUM(s.supplierExtraRevenue), 0)         AS supplierRevenueTotal
            FROM Shift s
            WHERE s.captain.id = :captainId
              AND s.excluded = false
              AND s.shiftDate BETWEEN :from AND :to
            GROUP BY s.captain.id
            """)
    Optional<CaptainPeriodTotals> aggregateForCaptain(@Param("captainId") UUID captainId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    /** Shifts a recalculation must revisit. */
    @Query("SELECT s FROM Shift s WHERE s.shiftDate BETWEEN :from AND :to")
    List<Shift> findForRecalculation(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Backfills shifts that were imported before their supplier ID was linked. */
    @Query("SELECT s FROM Shift s WHERE s.captain IS NULL AND s.ninjaCaptainId = :externalId")
    List<Shift> findUnmatchedByExternalId(@Param("externalId") String externalId);

    /** Shifts with a non-zero supplier deduction for a captain in a period — used by the payslip PDF. */
    @Query("""
            SELECT s FROM Shift s
            WHERE s.captain.id = :captainId
              AND s.shiftDate BETWEEN :from AND :to
              AND s.supplierDeduction IS NOT NULL
              AND s.supplierDeduction <> 0
            ORDER BY s.shiftDate ASC
            """)
    List<Shift> findSupplierDeductionsByCaptain(@Param("captainId") UUID captainId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    /** Projection for {@link #aggregateByCaptain}. */
    interface CaptainPeriodTotals {
        UUID getCaptainId();
        long getAttendedShifts();
        long getAbsentShifts();
        long getTotalOrders();
        java.math.BigDecimal getTotalHours();
        java.math.BigDecimal getAvgBusyUtilization();
        java.math.BigDecimal getAvgHourlyUtr();
        java.math.BigDecimal getAvgDeliveryOntime();
        java.math.BigDecimal getLateDeductions();
        java.math.BigDecimal getSupplierDeductions();
        java.math.BigDecimal getDailyIncentives();
        java.math.BigDecimal getFuelAllowances();
        java.math.BigDecimal getCarAllowances();
        java.math.BigDecimal getSupplierRevenueTotal();
    }
}
