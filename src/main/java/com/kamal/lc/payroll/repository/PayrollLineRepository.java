package com.kamal.lc.payroll.repository;

import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.kamal.lc.payroll.entity.PayrollLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayrollLineRepository extends JpaRepository<PayrollLine, UUID> {

    List<PayrollLine> findByPayrollRunIdOrderByCaptainCodeAsc(UUID payrollRunId);

    Optional<PayrollLine> findByPayrollRunIdAndCaptainId(UUID payrollRunId, UUID captainId);

    boolean existsByCaptainId(UUID captainId);

    void deleteByPayrollRunId(UUID payrollRunId);

    /** A captain's own payslips: lines of runs in the given statuses, newest period first. */
    @Query("SELECT l FROM PayrollLine l JOIN FETCH l.payrollRun r "
            + "WHERE l.captain.id = :captainId AND r.status IN :statuses ORDER BY r.periodFrom DESC")
    List<PayrollLine> findForCaptainInRunStatuses(@Param("captainId") UUID captainId,
                                                 @Param("statuses") Collection<String> statuses);
}
