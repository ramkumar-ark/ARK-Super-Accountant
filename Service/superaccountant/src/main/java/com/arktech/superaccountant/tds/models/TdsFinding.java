package com.arktech.superaccountant.tds.models;

import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tds_findings", indexes = @Index(name = "idx_tds_findings_workflow", columnList = "workflow_id"))
@Data
@NoArgsConstructor
public class TdsFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "upload_job_id", nullable = false)
    private UUID uploadJobId;

    @Convert(converter = TdsFindingTypeConverter.class)
    @Column(name = "finding_type", nullable = false, length = 40)
    private TdsFindingType findingType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FindingSeverity severity;

    @Column(name = "party_ledger_name")
    private String partyLedgerName;

    @Column(name = "voucher_id")
    private UUID voucherId;

    @Column(name = "voucher_date")
    private LocalDate voucherDate;

    @Column(name = "voucher_number")
    private String voucherNumber;

    // JSON array of contributing ParsedVoucher UUIDs (retro threshold findings)
    @Column(name = "related_voucher_ids", columnDefinition = "TEXT")
    private String relatedVoucherIds;

    @Column(length = 10)
    private String section;

    @Column(name = "expected_amount", precision = 19, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "actual_amount", precision = 19, scale = 2)
    private BigDecimal actualAmount;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "suggested_fix", columnDefinition = "TEXT")
    private String suggestedFix;

    /** SHA-256 hex of the evidence bundle that produced this finding. */
    @Column(name = "evidence_hash", length = 64)
    private String evidenceHash;

    /** Finding in a superseded workflow this row was carried over from. */
    @Column(name = "carried_from_finding_id")
    private UUID carriedFromFindingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolve_status", nullable = false)
    private ResolveStatus resolveStatus = ResolveStatus.OPEN;

    @Column(name = "resolve_note", columnDefinition = "TEXT")
    private String resolveNote;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Corrected value proposed by the submitter (typed-input findings only). */
    @Column(name = "proposed_value", columnDefinition = "TEXT")
    private String proposedValue;

    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
