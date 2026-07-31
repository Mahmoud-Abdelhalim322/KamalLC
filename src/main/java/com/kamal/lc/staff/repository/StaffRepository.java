package com.kamal.lc.staff.repository;

import com.kamal.lc.staff.entity.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {

    Page<Staff> findByStaffType(String staffType, Pageable pageable);

    @Query("""
        SELECT s FROM Staff s
        WHERE (LOWER(s.fullNameAr) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(s.fullNameEn) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(s.staffCode) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(s.phone) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
    Page<Staff> search(String q, Pageable pageable);

    @Query("""
        SELECT s FROM Staff s
        WHERE s.staffType = :type
          AND (LOWER(s.fullNameAr) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(s.fullNameEn) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(s.staffCode) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(s.phone) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
    Page<Staff> searchByType(String q, String type, Pageable pageable);

    @Query("SELECT s.staffCode FROM Staff s WHERE s.staffCode LIKE CONCAT(:prefix, '%')")
    List<String> findCodesStartingWith(String prefix);

    boolean existsByStaffCode(String staffCode);

    List<Staff> findByStaffTypeAndStatus(String staffType, String status);

    List<Staff> findByStatus(String status);

    Page<Staff> findByIdIn(java.util.Collection<UUID> ids, Pageable pageable);

    Page<Staff> findByStaffTypeAndIdIn(String staffType, java.util.Collection<UUID> ids, Pageable pageable);
}
