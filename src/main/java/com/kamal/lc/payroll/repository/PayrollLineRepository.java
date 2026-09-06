package com.kamal.lc.payroll.repository;

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
}
