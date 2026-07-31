package com.kamal.lc.imports.repository;

import com.kamal.lc.imports.entity.ImportBatch;
import com.kamal.lc.imports.entity.ImportBatchType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    /**
     * Has this exact file already been imported? Only a COMMITTED batch counts — a file
     * left sitting in preview was never applied, so re-uploading it must be allowed.
     */
    boolean existsByFileHashAndBatchTypeAndStatus(String fileHash, ImportBatchType batchType, String status);

    /**
     * The pending preview for a file, if the user uploaded it before without committing.
     * Deliberately "findFirst": nothing stops the same file being previewed twice, and a
     * single-result lookup here used to blow up the whole upload with a 500.
     */
    Optional<ImportBatch> findFirstByFileHashAndBatchTypeAndStatusOrderByUploadedAtDesc(
            String fileHash, ImportBatchType batchType, String status);

    Page<ImportBatch> findAllByOrderByUploadedAtDesc(Pageable pageable);

    /** History for one import screen — captain files and shift reports never mix. */
    Page<ImportBatch> findAllByBatchTypeOrderByUploadedAtDesc(ImportBatchType batchType, Pageable pageable);

    /** Most recent committed import. */
    Optional<ImportBatch> findFirstByStatusOrderByUploadedAtDesc(String status);
}
