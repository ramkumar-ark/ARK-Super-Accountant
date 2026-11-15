package com.arktech.superaccountant.workflow.repository;

import com.arktech.superaccountant.workflow.models.ResponsibilityStart;
import com.arktech.superaccountant.workflow.models.WorkflowType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResponsibilityStartRepository extends JpaRepository<ResponsibilityStart, UUID> {

    Optional<ResponsibilityStart> findByOrganizationIdAndType(UUID organizationId, WorkflowType type);

    List<ResponsibilityStart> findAllByOrganizationId(UUID organizationId);
}
