package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.UploadLedgerSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadLedgerSnapshotRepository extends JpaRepository<UploadLedgerSnapshot, UUID> {

    List<UploadLedgerSnapshot> findByUploadJobId(UUID uploadJobId);

    @Modifying
    @Transactional
    void deleteByUploadJobId(UUID uploadJobId);

    Optional<UploadLedgerSnapshot> findByUploadJobIdAndId(UUID uploadJobId, UUID snapshotId);

    @Query("SELECT s.ledgerName FROM UploadLedgerSnapshot s WHERE s.uploadJobId = :jobId")
    List<String> findLedgerNamesByUploadJobId(@Param("jobId") UUID jobId);
}
