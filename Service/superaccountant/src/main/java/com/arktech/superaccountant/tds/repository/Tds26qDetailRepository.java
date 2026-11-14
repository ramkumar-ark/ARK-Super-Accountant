package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.tds.models.Tds26qDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface Tds26qDetailRepository extends JpaRepository<Tds26qDetail, UUID> {
    Optional<Tds26qDetail> findByWorkflowId(UUID workflowId);
}
