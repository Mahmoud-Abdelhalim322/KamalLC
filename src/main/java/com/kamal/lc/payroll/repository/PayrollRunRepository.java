package com.kamal.lc.payroll.repository;

import com.kamal.lc.payroll.entity.PayrollRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID> {

    Page<PayrollRun> findAllByOrderByPeriodFromDesc(Pageable pageable);

    Optional<PayrollRun> findByPeriodFromAndPeriodTo(LocalDate periodFrom, LocalDate periodTo);

    /**
     * Approved or paid runs whose period overlaps [from, to]. Shift writes touching such a
     * period must be refused rather than silently restating a paid month.
     */
    @Query("SELECT r FROM PayrollRun r WHERE r.status IN ('APPROVED', 'PAID') "
            + "AND r.periodFrom <= :to AND r.periodTo >= :from")
    List<PayrollRun> findLockedOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
