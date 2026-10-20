package com.kamal.lc.captainapp.repository;

import com.kamal.lc.captainapp.entity.CaptainDocumentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaptainDocumentRequestRepository extends JpaRepository<CaptainDocumentRequest, UUID> {

    List<CaptainDocumentRequest> findByCaptainIdOrderByCreatedAtDesc(UUID captainId);

    boolean existsByCaptainIdAndDocumentKindAndStatus(UUID captainId, String documentKind, String status);

    Page<CaptainDocumentRequest> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    Page<CaptainDocumentRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);
}
