package com.kamal.lc.captainapp.repository;

import com.kamal.lc.captainapp.entity.CaptainOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CaptainOtpRepository extends JpaRepository<CaptainOtp, UUID> {

    Optional<CaptainOtp> findFirstByPhoneKeyOrderByCreatedAtDesc(String phoneKey);

    long countByPhoneKeyAndCreatedAtAfter(String phoneKey, OffsetDateTime since);
}
