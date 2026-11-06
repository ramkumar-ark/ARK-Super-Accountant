package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.MasterEditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MasterEditLogRepository extends JpaRepository<MasterEditLog, UUID> {
    List<MasterEditLog> findByFindingId(UUID findingId);
    List<MasterEditLog> findByMasterId(UUID masterId);
}
