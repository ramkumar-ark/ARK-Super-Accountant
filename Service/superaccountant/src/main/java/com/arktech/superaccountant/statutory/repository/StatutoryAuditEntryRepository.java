package com.arktech.superaccountant.statutory.repository;

import com.arktech.superaccountant.statutory.models.StatutoryAuditEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StatutoryAuditEntryRepository extends JpaRepository<StatutoryAuditEntry, UUID> {

    List<StatutoryAuditEntry> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
