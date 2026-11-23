package com.arktech.superaccountant.filedreturns;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiledReturnSnapshotRepository extends JpaRepository<FiledReturnSnapshot, UUID> {

    Optional<FiledReturnSnapshot> findByOrganizationIdAndFormTypeAndFilingPeriodAndSupersededAtIsNull(
            UUID organizationId, FiledFormType formType, String filingPeriod);

    Optional<FiledReturnSnapshot> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<FiledReturnSnapshot> findAllByOrganizationId(UUID organizationId);

    List<FiledReturnSnapshot> findAllByOrganizationIdAndFormTypeAndSupersededAtIsNullAndCoverStartBetween(
            UUID organizationId, FiledFormType formType, java.time.LocalDate coverStartFrom,
            java.time.LocalDate coverStartTo);

    List<FiledReturnSnapshot> findAllByOrganizationIdAndBaselineWorkflowIdOrderByFilingPeriodAscImportedAtAsc(
            UUID organizationId, UUID baselineWorkflowId);

    boolean existsByBaselineWorkflowId(UUID baselineWorkflowId);
}
