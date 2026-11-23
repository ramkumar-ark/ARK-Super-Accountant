package com.arktech.superaccountant.tdsapplicability.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A prompt asking the user to confirm (or dismiss) whether TDS applies for one
 * financial year — raised from books turnover, a financial-year close, or the
 * one-time legacy note. Dismissed prompts are kept so the same prompt is never
 * raised twice.
 */
@Entity
@Table(name = "tds_applicability_prompts")
@Data
@NoArgsConstructor
public class TdsApplicabilityPrompt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 32, nullable = false)
    private PromptKind kind;

    @Column(name = "financial_year", nullable = false)
    private int financialYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PromptStatus status = PromptStatus.OPEN;

    // Snapshot of the turnover estimate at raise time, so the message is stable.
    @Column(name = "estimate_measured_fy")
    private Integer estimateMeasuredFy;

    @Column(name = "estimate_amount", precision = 18, scale = 2)
    private BigDecimal estimateAmount;

    @Column(name = "estimate_source", length = 16)
    private String estimateSource;

    @Column(name = "estimate_complete")
    private Boolean estimateComplete;

    @Column(name = "dismiss_reason", columnDefinition = "TEXT")
    private String dismissReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;
}
