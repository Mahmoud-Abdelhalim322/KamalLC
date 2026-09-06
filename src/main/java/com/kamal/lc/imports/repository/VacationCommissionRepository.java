package com.kamal.lc.imports.repository;

import com.kamal.lc.imports.entity.VacationCommission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VacationCommissionRepository extends JpaRepository<VacationCommission, UUID>, JpaSpecificationExecutor<VacationCommission> {

    @Query("""
            SELECT v.captain.id AS captainId,
                   COALESCE(SUM(v.amount), 0) AS totalVacationCommission
            FROM VacationCommission v
            WHERE v.captain IS NOT NULL
              AND v.vacationDate BETWEEN :from AND :to
            GROUP BY v.captain.id
            """)
    List<CaptainVacationCommissionTotals> aggregateByCaptain(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(v.amount), 0)
            FROM VacationCommission v
            WHERE v.captain.id = :captainId
              AND v.vacationDate BETWEEN :from AND :to
            """)
    BigDecimal sumByCaptainAndDateBetween(@Param("captainId") UUID captainId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<VacationCommission> findByVacationDateBetween(LocalDate from, LocalDate to);

    interface CaptainVacationCommissionTotals {
        UUID getCaptainId();
        BigDecimal getTotalVacationCommission();
    }
}
