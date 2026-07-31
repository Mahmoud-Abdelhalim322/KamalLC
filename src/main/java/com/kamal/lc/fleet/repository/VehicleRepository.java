package com.kamal.lc.fleet.repository;

import com.kamal.lc.fleet.entity.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Optional<Vehicle> findByPlateNumber(String plateNumber);

    boolean existsByPlateNumber(String plateNumber);

    @Query("SELECT v FROM Vehicle v WHERE v.plateNumber LIKE %:q% OR v.brand LIKE %:q% OR v.model LIKE %:q%")
    Page<Vehicle> search(String q, Pageable pageable);

    @Query("SELECT v FROM Vehicle v WHERE v.status = :status AND (v.plateNumber LIKE %:q% OR v.brand LIKE %:q% OR v.model LIKE %:q%)")
    Page<Vehicle> searchByStatus(String q, String status, Pageable pageable);

    Page<Vehicle> findByStatus(String status, Pageable pageable);

    Page<Vehicle> findByOwnershipType(String ownershipType, Pageable pageable);

    long countByStatus(String status);

    long countByOwnershipType(String ownershipType);

    /** Vehicles whose rental contract ends within the alert window. */
    @Query("SELECT v FROM Vehicle v WHERE v.ownershipType = 'RENTED' AND v.rentalEndDate IS NOT NULL "
            + "AND v.rentalEndDate <= :alertDate AND v.status <> 'DECOMMISSIONED'")
    List<Vehicle> findExpiringRentals(LocalDate alertDate);

    /** Distinct rental agent names for the filter dropdown. */
    @Query("SELECT DISTINCT v.rentalAgentName FROM Vehicle v WHERE v.rentalAgentName IS NOT NULL ORDER BY v.rentalAgentName")
    List<String> findDistinctRentalAgents();

    Page<Vehicle> findByOwnershipTypeAndStatus(String ownershipType, String status, Pageable pageable);

    @Query("SELECT v FROM Vehicle v WHERE v.ownershipType = 'RENTED' AND v.rentalAgentName = :agent")
    Page<Vehicle> findByRentalAgent(String agent, Pageable pageable);

    @Query("SELECT v FROM Vehicle v WHERE v.ownershipType = 'RENTED' AND v.rentalAgentName = :agent AND v.status = :status")
    Page<Vehicle> findByRentalAgentAndStatus(String agent, String status, Pageable pageable);
}
