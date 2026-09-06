package com.kamal.lc.imports.repository;

import com.kamal.lc.imports.entity.ImportBatch;
import com.kamal.lc.imports.entity.ImportBatchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    /**
     * Has this exact file already been imported? Only a COMMITTED batch counts.
     */
    boolean existsByFileHashAndBatchTypeAndStatus(String fileHash, ImportBatchType batchType, String status);

    @Query("SELECT COUNT(b) > 0 FROM ImportBatch b WHERE b.fileHash = :fileHash AND b.batchType = :batchType AND b.status = :status AND b.tenant.id = :tenantId")
    boolean existsByFileHashAndBatchTypeAndStatusAndTenantId(
            @Param("fileHash") String fileHash,
            @Param("batchType") ImportBatchType batchType,
            @Param("status") String status,
            @Param("tenantId") UUID tenantId);

    /**
     * The pending preview for a file, if the user uploaded it before without committing.
     */
    Optional<ImportBatch> findFirstByFileHashAndBatchTypeAndStatusOrderByUploadedAtDesc(
            String fileHash, ImportBatchType batchType, String status);

    @Query("SELECT b FROM ImportBatch b WHERE b.fileHash = :fileHash AND b.batchType = :batchType AND b.status = :status AND b.tenant.id = :tenantId ORDER BY b.uploadedAt DESC")
    Optional<ImportBatch> findFirstByFileHashAndBatchTypeAndStatusAndTenantIdOrderByUploadedAtDesc(
            @Param("fileHash") String fileHash,
            @Param("batchType") ImportBatchType batchType,
            @Param("status") String status,
            @Param("tenantId") UUID tenantId);

    Page<ImportBatch> findAllByOrderByUploadedAtDesc(Pageable pageable);

    @Query("SELECT b FROM ImportBatch b WHERE b.tenant.id = :tenantId ORDER BY b.uploadedAt DESC")
    Page<ImportBatch> findAllByTenantIdOrderByUploadedAtDesc(@Param("tenantId") UUID tenantId, Pageable pageable);

    /** History for one import screen — captain files and shift reports never mix. */
    Page<ImportBatch> findAllByBatchTypeOrderByUploadedAtDesc(ImportBatchType batchType, Pageable pageable);

    @Query("SELECT b FROM ImportBatch b WHERE b.batchType = :batchType AND b.tenant.id = :tenantId ORDER BY b.uploadedAt DESC")
    Page<ImportBatch> findAllByBatchTypeAndTenantIdOrderByUploadedAtDesc(
            @Param("batchType") ImportBatchType batchType,
            @Param("tenantId") UUID tenantId,
            Pageable pageable);

    /** Most recent committed import. */
    Optional<ImportBatch> findFirstByStatusOrderByUploadedAtDesc(String status);
}
