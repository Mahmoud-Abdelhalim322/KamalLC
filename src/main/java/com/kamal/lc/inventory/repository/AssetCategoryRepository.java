package com.kamal.lc.inventory.repository;

import com.kamal.lc.inventory.entity.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, UUID> {

    Optional<AssetCategory> findByName(String name);

    boolean existsByName(String name);
}
