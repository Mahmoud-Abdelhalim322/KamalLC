package com.kamal.lc.auth.repository;

import com.kamal.lc.auth.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for application users.
 */
@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE AppUser u SET u.staff = null WHERE u.staff.id = :staffId AND (u.tenant.id = :#{T(com.kamal.lc.common.security.TenantContext).getCurrentTenantId()} OR :#{T(com.kamal.lc.common.security.TenantContext).getCurrentTenantId()} IS NULL)")
    void unlinkStaff(@Param("staffId") UUID staffId);

    java.util.List<AppUser> findByTenantId(UUID tenantId);
}
