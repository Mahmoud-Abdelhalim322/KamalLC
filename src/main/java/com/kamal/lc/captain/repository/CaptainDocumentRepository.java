package com.kamal.lc.captain.repository;

import com.kamal.lc.captain.entity.CaptainDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CaptainDocumentRepository extends JpaRepository<CaptainDocument, UUID> {

    List<CaptainDocument> findByCaptainId(UUID captainId);
}
