package com.arktech.superaccountant.gst.models;

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
@Table(name = "gst_daybook_findings", indexes = {
        @Index(columnList = "workflow_id"),
        @Index(columnList = "upload_job_id")
})
@Data
@NoArgsConstructor
public class GstDaybookFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "upload_job_id", nullable = false)
    private UUID uploadJobId;

    @Convert(converter = GstFindingTypeConverter.class)
    @Column(name = "finding_type", length = 40, nullable = false)
    private GstFindingType findingType;

    @Enumerated(EnumType.STRING)
    private FindingSeverity severity;

    @Convert(converter = VoucherSideConverter.class)
    @Column(name = "voucher_side", length = 16)
    private VoucherSide voucherSide;

    @Column(name = "voucher_id")
    private UUID voucherId;

    @Column(name = "voucher_guid")
    private String voucherGuid;

    private LocalDate voucherDate;

    @Column(name = "voucher_number")
    private String voucherNumber;

    @Column(name = "voucher_type_name")
    private String voucherTypeName;

    @Column(name = "party_ledger_name")
    private String partyLedgerName;

    @Column(name = "ledger_name")
    private String ledgerName;

    @Column(name = "expected_value")
    private String expectedValue;

    @Column(name = "actual_value")
    private String actualValue;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "taxable_amount", precision = 19, scale = 2)
    private BigDecimal taxableAmount;

    @Column(name = "evidence_hash", length = 64)
    private String evidenceHash;

    @Column(name = "carried_from_finding_id")
    private UUID carriedFromFindingId;

    // ── Shared review columns (must match ValidationFinding exactly) ─────────

    @Enumerated(EnumType.STRING)
    @Column(name = "resolve_status", nullable = false)
    private ResolveStatus resolveStatus = ResolveStatus.OPEN;

    @Column(name = "resolve_note", columnDefinition = "TEXT")
    private String resolveNote;

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

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
