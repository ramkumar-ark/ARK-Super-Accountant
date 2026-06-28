package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.LedgerEditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerEditLogRepository extends JpaRepository<LedgerEditLog, UUID> {
    List<LedgerEditLog> findByUploadJobId(UUID uploadJobId);
    List<LedgerEditLog> findByLedgerSnapshotId(UUID snapshotId);
}
