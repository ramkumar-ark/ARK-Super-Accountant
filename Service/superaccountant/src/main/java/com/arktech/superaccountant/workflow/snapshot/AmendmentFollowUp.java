package com.arktech.superaccountant.workflow.snapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One follow-up per workflow affected by an accepted tamper change: the
 * amendment must be reflected in that workflow's return.
 */
@Entity
@Table(name = "amendment_follow_ups")
@Data
@NoArgsConstructor
public class AmendmentFollowUp {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "tamper_finding_id")
    private UUID tamperFindingId;

    private String voucherKey;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Convert(converter = AmendmentStatusConverter.class)
    @Column(nullable = false, length = 16)
    private AmendmentStatus status;

    @Convert(converter = AmendmentChangeKindConverter.class)
    @Column(length = 16)
    private AmendmentChangeKind change;

    @Column(name = "table_code", length = 16)
    private String tableCode;

    @Column(name = "original_workflow_id")
    private UUID originalWorkflowId;

    @Column(name = "original_doc_key", length = 128)
    private String originalDocKey;

    private String doneBy;

    private Instant doneAt;

    @Column(columnDefinition = "TEXT")
    private String doneNote;

    private Instant createdAt;
}
