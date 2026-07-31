package com.kamal.lc.custody.repository;

import com.kamal.lc.custody.entity.CustodyRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustodyRepository extends JpaRepository<CustodyRecord, UUID> {

    List<CustodyRecord> findByCaptainIdAndStatus(UUID captainId, String status);

    Page<CustodyRecord> findByCaptainId(UUID captainId, Pageable pageable);

    Page<CustodyRecord> findByStatus(String status, Pageable pageable);

    Page<CustodyRecord> findByAssetType(String assetType, Pageable pageable);

    Optional<CustodyRecord> findByReferenceIdAndStatus(UUID referenceId, String status);

    List<CustodyRecord> findByReferenceId(UUID referenceId);

    Page<CustodyRecord> findByReferenceId(UUID referenceId, Pageable pageable);

    long countByStatus(String status);

    long countByCaptainIdAndStatus(UUID captainId, String status);

    /**
     * Summary: how many active custody items each captain holds, grouped by captain.
     * Returns [captainId, captainCode, captainName, activeCount].
     */
    @Query("SELECT c.captain.id, c.captain.kamalCode, c.captain.fullNameAr, COUNT(c) "
            + "FROM CustodyRecord c WHERE c.status = 'ACTIVE' "
            + "GROUP BY c.captain.id, c.captain.kamalCode, c.captain.fullNameAr "
            + "ORDER BY c.captain.fullNameAr")
    List<Object[]> captainCustodySummary();
}
