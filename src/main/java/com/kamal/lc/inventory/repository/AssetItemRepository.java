package com.kamal.lc.inventory.repository;

import com.kamal.lc.inventory.entity.AssetItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AssetItemRepository extends JpaRepository<AssetItem, UUID> {

    Page<AssetItem> findByCategoryId(UUID categoryId, Pageable pageable);

    Page<AssetItem> findByCategoryIdAndStatus(UUID categoryId, String status, Pageable pageable);

    @Query("SELECT i FROM AssetItem i WHERE i.category.id = :categoryId AND "
            + "(i.serialCode LIKE %:q% OR EXISTS (SELECT 1 FROM AssetItemField f WHERE f.item = i AND f.fieldValue LIKE %:q%))")
    Page<AssetItem> searchInCategory(UUID categoryId, String q, Pageable pageable);

    long countByCategoryId(UUID categoryId);

    long countByCategoryIdAndStatus(UUID categoryId, String status);

    /** Find the highest serial code number for a given prefix to generate the next one. */
    @Query("SELECT i.serialCode FROM AssetItem i WHERE i.serialCode LIKE CONCAT(:prefix, '%') ORDER BY i.serialCode DESC")
    List<String> findSerialCodesStartingWith(String prefix);
}
