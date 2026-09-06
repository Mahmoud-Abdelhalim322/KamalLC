package com.kamal.lc.auth.repository;

import com.kamal.lc.auth.entity.UserCaptainAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserCaptainAssignmentRepository extends JpaRepository<UserCaptainAssignment, UUID> {

    List<UserCaptainAssignment> findByUserId(UUID userId);

    @Query("SELECT ua.captain.id FROM UserCaptainAssignment ua WHERE ua.user.id = :userId")
    List<UUID> findCaptainIdsByUserId(@Param("userId") UUID userId);

    void deleteByUserId(UUID userId);

    void deleteByUserIdAndCaptainIdIn(UUID userId, List<UUID> captainIds);

    boolean existsByUserIdAndCaptainId(UUID userId, UUID captainId);

    @Query("SELECT COUNT(ua) FROM UserCaptainAssignment ua WHERE ua.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);
}
