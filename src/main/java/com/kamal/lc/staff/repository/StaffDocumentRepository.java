package com.kamal.lc.staff.repository;

import com.kamal.lc.staff.entity.StaffDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StaffDocumentRepository extends JpaRepository<StaffDocument, UUID> {
    List<StaffDocument> findByStaffId(UUID staffId);

    void deleteByStaffId(UUID staffId);
}
