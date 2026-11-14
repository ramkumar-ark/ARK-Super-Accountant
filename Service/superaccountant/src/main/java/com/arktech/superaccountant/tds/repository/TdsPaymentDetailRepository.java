package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.tds.models.TdsPaymentDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TdsPaymentDetailRepository extends JpaRepository<TdsPaymentDetail, UUID> {
    Optional<TdsPaymentDetail> findByWorkflowId(UUID workflowId);
}
