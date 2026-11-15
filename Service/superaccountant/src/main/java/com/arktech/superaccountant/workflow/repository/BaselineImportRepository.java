package com.arktech.superaccountant.workflow.repository;

import com.arktech.superaccountant.workflow.models.BaselineImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface BaselineImportRepository extends JpaRepository<BaselineImport, UUID> {

    long countByWorkflowIdIn(Collection<UUID> workflowIds);
}
