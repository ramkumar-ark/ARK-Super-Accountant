package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.workflow.models.WorkflowType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One frozen masters attribute a finalized workflow relied on (addendum item 1).
 * One live row per (organization, ledger, attribute, workflow type, validFrom);
 * differences are detected by MastersTamperComparisonService, never rewritten here.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "frozen_master_attributes")
public class FrozenMasterAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    private String ledgerName;
    private String ledgerKey;
    private String attribute;
    private String value;

    @Column(name = "workflow_type")
    private WorkflowType workflowType;

    /** Latest frozen period end for this type; null on valid-from rows not yet used. */
    private LocalDate periodEnd;

    private UUID frozenByWorkflowId;

    /** Null = from the beginning. */
    private LocalDate validFrom;

    private Instant frozenAt;
    private Instant releasedAt;
}
