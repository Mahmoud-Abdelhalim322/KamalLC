package com.kamal.lc.payroll.repository;

import com.kamal.lc.payroll.entity.StaffPayrollLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffPayrollLineRepository extends JpaRepository<StaffPayrollLine, UUID> {

    List<StaffPayrollLine> findByPayrollRunIdOrderByStaffCodeAsc(UUID runId);

    List<StaffPayrollLine> findByPayrollRunIdAndStaffTypeOrderByStaffCodeAsc(UUID runId, String staffType);

    Optional<StaffPayrollLine> findByPayrollRunIdAndStaffId(UUID runId, UUID staffId);

    void deleteByPayrollRunId(UUID runId);
}
