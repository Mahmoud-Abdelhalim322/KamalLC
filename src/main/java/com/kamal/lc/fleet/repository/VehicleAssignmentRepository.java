package com.kamal.lc.fleet.repository;

import com.kamal.lc.fleet.entity.VehicleAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleAssignmentRepository extends JpaRepository<VehicleAssignment, UUID> {

    /** The active assignment for a vehicle — at most one at a time. */
    Optional<VehicleAssignment> findByVehicleIdAndStatus(UUID vehicleId, String status);

    Page<VehicleAssignment> findByStatus(String status, Pageable pageable);

    Page<VehicleAssignment> findByCaptainId(UUID captainId, Pageable pageable);

    List<VehicleAssignment> findByVehicleIdOrderByAssignedAtDesc(UUID vehicleId);

    /** Assignments whose expected return is past and are still ACTIVE — candidates for LATE. */
    @Query("SELECT a FROM VehicleAssignment a WHERE a.status = 'ACTIVE' AND a.expectedReturnAt < CURRENT_TIMESTAMP")
    List<VehicleAssignment> findOverdueAssignments();

    long countByStatus(String status);

    /** Count late assignments this month. */
    @Query("SELECT COUNT(a) FROM VehicleAssignment a WHERE a.status = 'LATE' OR "
            + "(a.status = 'RETURNED' AND a.actualReturnAt > a.expectedReturnAt)")
    long countLateReturns();

    boolean existsByVehicleId(UUID vehicleId);
}
