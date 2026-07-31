package com.kamal.lc.auth.repository;

import com.kamal.lc.auth.entity.JobTitle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobTitleRepository extends JpaRepository<JobTitle, UUID> {

    Optional<JobTitle> findByName(String name);

    boolean existsByName(String name);

    List<JobTitle> findByActiveTrue();
}
