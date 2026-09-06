package com.kamal.lc.captain.repository;

import com.kamal.lc.captain.entity.Captain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaptainRepository extends JpaRepository<Captain, UUID> {

    Optional<Captain> findByKamalCode(String kamalCode);

    Optional<Captain> findByIqamaNumber(String iqamaNumber);

    Optional<Captain> findByPhone(String phone);

    boolean existsByKamalCode(String kamalCode);

    /**
     * Name, Kamal code, or supplier ID.
     *
     * <p>The supplier ID is how the operations team refers to a captain all day — it is what
     * the supplier's files carry and what an import reports as unmatched — so searching by it
     * has to land on the captain holding it, including through an assignment that has since
     * been closed.
     */
    @Query("SELECT c FROM Captain c WHERE c.fullNameAr LIKE %:query% OR c.kamalCode LIKE %:query% "
            + "OR EXISTS (SELECT 1 FROM SupplierIdAssignment a WHERE a.captain = c "
            + "AND a.supplierIdSlot.externalId LIKE %:query%)")
    Page<Captain> search(String query, Pageable pageable);

    Page<Captain> findByCaptainType(String captainType, Pageable pageable);

    @Query("SELECT c FROM Captain c WHERE c.captainType = :type AND (c.fullNameAr LIKE %:query% "
            + "OR c.kamalCode LIKE %:query% "
            + "OR EXISTS (SELECT 1 FROM SupplierIdAssignment a WHERE a.captain = c "
            + "AND a.supplierIdSlot.externalId LIKE %:query%))")
    Page<Captain> searchByType(String query, String type, Pageable pageable);



    long countByStatus(String status);

    List<Captain> findByStatus(String status);

    List<Captain> findByTenantId(UUID tenantId);

    Page<Captain> findByIdIn(Collection<UUID> ids, Pageable pageable);

    Page<Captain> findByIdInAndCaptainType(Collection<UUID> ids, String captainType, Pageable pageable);

    @Query("SELECT c FROM Captain c WHERE c.id IN :ids AND (c.fullNameAr LIKE %:query% OR c.kamalCode LIKE %:query% "
            + "OR EXISTS (SELECT 1 FROM SupplierIdAssignment a WHERE a.captain = c AND a.supplierIdSlot.externalId LIKE %:query%))")
    Page<Captain> searchAndIdIn(@Param("query") String query, @Param("ids") Collection<UUID> ids, Pageable pageable);

    @Query("SELECT c FROM Captain c WHERE c.id IN :ids AND c.captainType = :type AND (c.fullNameAr LIKE %:query% "
            + "OR c.kamalCode LIKE %:query% "
            + "OR EXISTS (SELECT 1 FROM SupplierIdAssignment a WHERE a.captain = c AND a.supplierIdSlot.externalId LIKE %:query%))")
    Page<Captain> searchByTypeAndIdIn(@Param("query") String query, @Param("type") String type, @Param("ids") Collection<UUID> ids, Pageable pageable);

    /** All existing codes sharing a prefix — used to compute the next free sequence number. */
    @Query("SELECT c.kamalCode FROM Captain c WHERE c.kamalCode LIKE CONCAT(:prefix, '%')")
    List<String> findCodesStartingWith(@Param("prefix") String prefix);
}
