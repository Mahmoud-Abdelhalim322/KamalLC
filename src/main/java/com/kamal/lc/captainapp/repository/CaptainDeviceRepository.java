package com.kamal.lc.captainapp.repository;

import com.kamal.lc.captainapp.entity.CaptainDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptainDeviceRepository extends JpaRepository<CaptainDevice, UUID> {

    Optional<CaptainDevice> findByPushToken(String pushToken);

    List<CaptainDevice> findByCaptainId(UUID captainId);
}
