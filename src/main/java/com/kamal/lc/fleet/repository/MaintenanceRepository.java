package com.kamal.lc.fleet.repository;

import com.kamal.lc.fleet.entity.VehicleMaintenance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceRepository extends JpaRepository<VehicleMaintenance, UUID> {

    Page<VehicleMaintenance> findByStatus(String status, Pageable pageable);

    Page<VehicleMaintenance> findByVehicleId(UUID vehicleId, Pageable pageable);

    Page<VehicleMaintenance> findByStatusAndVehicleId(String status, UUID vehicleId, Pageable pageable);

    List<VehicleMaintenance> findByVehicleIdOrderByOpenedAtDesc(UUID vehicleId);

    long countByStatus(String status);

    boolean existsByVehicleIdAndStatus(UUID vehicleId, String status);
}
