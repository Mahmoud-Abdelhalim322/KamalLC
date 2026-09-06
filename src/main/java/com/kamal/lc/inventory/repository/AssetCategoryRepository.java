package com.kamal.lc.inventory.repository;

import com.kamal.lc.inventory.entity.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, UUID> {

    Optional<AssetCategory> findByName(String name);

    boolean existsByName(String name);

    @Query(value = "SELECT COUNT(*) > 0 FROM asset_category WHERE name = :name AND tenant_id = :tenantId", nativeQuery = true)
    boolean existsByNameAndTenantIdRaw(@Param("name") String name, @Param("tenantId") UUID tenantId);
}
