package com.kamal.lc.loan.repository;

import com.kamal.lc.loan.entity.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    List<Loan> findByCaptainIdOrderByCreatedAtDesc(UUID captainId);

    List<Loan> findByStaffIdOrderByCreatedAtDesc(UUID staffId);

    List<Loan> findByCaptainIdAndStatus(UUID captainId, String status);

    List<Loan> findByStaffIdAndStatus(UUID staffId, String status);

    List<Loan> findByCaptainIdAndStatusOrderByCreatedAtAsc(UUID captainId, String status);

    List<Loan> findByStaffIdAndStatusOrderByCreatedAtAsc(UUID staffId, String status);

    @Query("SELECT COALESCE(SUM(l.remaining), 0) FROM Loan l WHERE l.captain.id = :captainId AND l.status = 'ACTIVE'")
    BigDecimal findActiveLoanBalanceByCaptainId(UUID captainId);

    @Query("SELECT COALESCE(SUM(l.remaining), 0) FROM Loan l WHERE l.staff.id = :staffId AND l.status = 'ACTIVE'")
    BigDecimal findActiveLoanBalanceByStaffId(UUID staffId);

    /** All active loans for payroll deduction processing. */
    List<Loan> findByStatusOrderByCreatedAtAsc(String status);

    List<Loan> findByMemberTypeAndStatusOrderByCreatedAtDesc(String memberType, String status);

    // ---- Aggregation projections for the members list ----

    interface MemberLoanSummary {
        UUID getMemberId();
        String getMemberCode();
        String getMemberName();
        String getMemberNameEn();
        String getMemberType();
        String getPhone();
        long getActiveLoanCount();
        BigDecimal getTotalRemaining();
        BigDecimal getTotalOriginal();
    }

    @Query("""
        SELECT c.id AS memberId, c.kamalCode AS memberCode,
               c.fullNameAr AS memberName, c.fullNameEn AS memberNameEn,
               'CAPTAIN' AS memberType, c.phone AS phone,
               COUNT(l.id) AS activeLoanCount,
               COALESCE(SUM(l.remaining), 0) AS totalRemaining,
               COALESCE(SUM(l.originalAmount), 0) AS totalOriginal
        FROM Captain c
        LEFT JOIN Loan l ON l.captain = c AND l.status = 'ACTIVE'
        WHERE c.status = 'ACTIVE'
        GROUP BY c.id, c.kamalCode, c.fullNameAr, c.fullNameEn, c.phone
        ORDER BY c.kamalCode
    """)
    List<MemberLoanSummary> findCaptainSummaries();

    @Query("""
        SELECT s.id AS memberId, s.staffCode AS memberCode,
               s.fullNameAr AS memberName, s.fullNameEn AS memberNameEn,
               s.staffType AS memberType, s.phone AS phone,
               COUNT(l.id) AS activeLoanCount,
               COALESCE(SUM(l.remaining), 0) AS totalRemaining,
               COALESCE(SUM(l.originalAmount), 0) AS totalOriginal
        FROM Staff s
        LEFT JOIN Loan l ON l.staff = s AND l.status = 'ACTIVE'
        WHERE s.status = 'ACTIVE' AND s.staffType = :staffType
        GROUP BY s.id, s.staffCode, s.fullNameAr, s.fullNameEn, s.staffType, s.phone
        ORDER BY s.staffCode
    """)
    List<MemberLoanSummary> findStaffSummaries(String staffType);
}
