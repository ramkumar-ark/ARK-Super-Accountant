package com.arktech.superaccountant.filedreturns.recon;

import com.arktech.superaccountant.filedreturns.FiledFormType;
import com.arktech.superaccountant.filedreturns.FiledFormTypeConverter;
import com.arktech.superaccountant.masters.models.FindingSeverity;
import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.LifecycleFinding;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "filed_return_discrepancies", indexes = {
        @Index(columnList = "workflow_id"),
        @Index(columnList = "workflow_id, superseded_at"),
        @Index(columnList = "organization_id")
})
@Data
@NoArgsConstructor
public class FiledReturnDiscrepancy implements LifecycleFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Convert(converter = FiledFormTypeConverter.class)
    @Column(name = "form_type", length = 16, nullable = false)
    private FiledFormType formType;

    @Column(name = "table_code", length = 16, nullable = false)
    private String tableCode;

    @Convert(converter = DiscrepancyKindConverter.class)
    @Column(name = "kind", length = 24, nullable = false)
    private DiscrepancyKind kind;

    @Convert(converter = DiscrepancyGrainConverter.class)
    @Column(name = "grain", length = 12, nullable = false)
    private DiscrepancyGrain grain;

    @Column(name = "material", nullable = false)
    private boolean material;

    @Column(name = "fuzzy_match")
    private Boolean fuzzyMatch;

    @Column(name = "doc_number")
    private String docNumber;

    private LocalDate docDate;

    @Column(name = "counterparty_gstin", length = 15)
    private String counterpartyGstin;

    @Column(name = "voucher_id")
    private UUID voucherId;

    @Column(name = "voucher_number")
    private String voucherNumber;

    @Column(name = "books_date")
    private LocalDate booksDate;

    @Column(name = "books_gstin", length = 15)
    private String booksGstin;

    @Column(name = "filed_taxable", precision = 19, scale = 2)
    private BigDecimal filedTaxable;

    @Column(name = "filed_igst", precision = 19, scale = 2)
    private BigDecimal filedIgst;

    @Column(name = "filed_cgst", precision = 19, scale = 2)
    private BigDecimal filedCgst;

    @Column(name = "filed_sgst", precision = 19, scale = 2)
    private BigDecimal filedSgst;

    @Column(name = "filed_cess", precision = 19, scale = 2)
    private BigDecimal filedCess;

    @Column(name = "books_taxable", precision = 19, scale = 2)
    private BigDecimal booksTaxable;

    @Column(name = "books_igst", precision = 19, scale = 2)
    private BigDecimal booksIgst;

    @Column(name = "books_cgst", precision = 19, scale = 2)
    private BigDecimal booksCgst;

    @Column(name = "books_sgst", precision = 19, scale = 2)
    private BigDecimal booksSgst;

    @Column(name = "books_cess", precision = 19, scale = 2)
    private BigDecimal booksCess;

    @Column(name = "amended_in_period", length = 7)
    private String amendedInPeriod;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    private FindingSeverity severity;

    @Column(name = "finding_key", length = 512, nullable = false)
    private String findingKey;

    @Column(name = "evidence_hash", length = 64)
    private String evidenceHash;

    @Column(name = "carried_from_finding_id")
    private UUID carriedFromFindingId;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    // ── Shared review columns (must match GstDaybookFinding exactly) ─────────

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

    @Override
    public String getFindingKey() {
        return findingKey;
    }
}
