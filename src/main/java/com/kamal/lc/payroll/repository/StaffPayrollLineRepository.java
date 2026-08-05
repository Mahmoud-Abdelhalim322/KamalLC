package com.kamal.lc.payroll.repository;

import com.kamal.lc.payroll.entity.StaffPayrollLine;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffPayrollLineRepository extends JpaRepository<StaffPayrollLine, UUID> {

    List<StaffPayrollLine> findByPayrollRunIdOrderByStaffCodeAsc(UUID runId);

    List<StaffPayrollLine> findByPayrollRunIdAndStaffTypeOrderByStaffCodeAsc(UUID runId, String staffType);

    Optional<StaffPayrollLine> findByPayrollRunIdAndStaffId(UUID runId, UUID staffId);

    void deleteByPayrollRunId(UUID runId);

    @Modifying
    @Query("UPDATE StaffPayrollLine spl SET spl.jobTitle = :jobTitle WHERE spl.staff.id = :staffId AND spl.payrollRun.status = 'DRAFT'")
    void updateJobTitleForDraftRuns(@Param("staffId") UUID staffId, @Param("jobTitle") String jobTitle);
}
