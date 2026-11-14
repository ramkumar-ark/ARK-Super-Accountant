package com.arktech.superaccountant.tds.repository;

import com.arktech.superaccountant.tds.models.TdsDeducteeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TdsDeducteeEntryRepository extends JpaRepository<TdsDeducteeEntry, UUID> {
    List<TdsDeducteeEntry> findByWorkflowId(UUID workflowId);
}
