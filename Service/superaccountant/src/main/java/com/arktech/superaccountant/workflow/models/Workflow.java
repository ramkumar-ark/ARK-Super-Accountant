package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "workflows")
@Data
@NoArgsConstructor
public class Workflow {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Convert(converter = WorkflowTypeConverter.class)
    @Column(nullable = false, length = 32)
    private WorkflowType type;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "period_key", nullable = false, length = 16)
    private String periodKey;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Column(name = "revision", nullable = false)
    private int revision = 0;

    @Column(name = "supersedes_workflow_id")
    private UUID supersedesWorkflowId;

    @Convert(converter = WorkflowStageConverter.class)
    @Column(nullable = false, length = 32)
    private WorkflowStage stage;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "upload_job_id")
    private UUID uploadJobId;

    @Column(name = "masters_job_id")
    private UUID mastersJobId;

    @Column(name = "baseline_confirmed_at")
    private Instant baselineConfirmedAt;

    @Column(name = "baseline_confirmed_by")
    private String baselineConfirmedBy;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "finalized_by")
    private String finalizedBy;

    @Column(name = "frozen_through")
    private LocalDate frozenThrough;

    @Column(name = "filed_at")
    private Instant filedAt;

    @Column(name = "filed_by")
    private String filedBy;

    @Column(name = "filing_ref")
    private String filingRef;

    @Column(name = "filing_ref_date")
    private LocalDate filingRefDate;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
