package com.kamal.lc.loan.repository;

import com.kamal.lc.loan.entity.LoanPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, UUID> {

    List<LoanPayment> findByLoanIdOrderByCreatedAtDesc(UUID loanId);

    List<LoanPayment> findByPayrollRunId(UUID payrollRunId);

    List<LoanPayment> findByCaptainAdjustmentId(UUID captainAdjustmentId);

    List<LoanPayment> findByStaffAdjustmentId(UUID staffAdjustmentId);

    void deleteByPayrollRunId(UUID payrollRunId);

    /** Finds only automatic (system-generated) payments for a payroll run — those NOT linked to a manual adjustment. */
    List<LoanPayment> findByPayrollRunIdAndCaptainAdjustmentIdIsNullAndStaffAdjustmentIdIsNull(UUID payrollRunId);

    /** Deletes only automatic (system-generated) payments for a payroll run. */
    void deleteByPayrollRunIdAndCaptainAdjustmentIdIsNullAndStaffAdjustmentIdIsNull(UUID payrollRunId);
}
