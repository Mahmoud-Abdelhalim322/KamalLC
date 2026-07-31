package com.kamal.lc.imports.repository;

import com.kamal.lc.imports.entity.ImportRowIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportRowIssueRepository extends JpaRepository<ImportRowIssue, UUID> {

    List<ImportRowIssue> findByImportBatchIdOrderByRowNumber(UUID batchId);

    /** Clears a preview's findings before it is re-run, so they are not counted twice. */
    void deleteByImportBatchId(UUID batchId);
}
