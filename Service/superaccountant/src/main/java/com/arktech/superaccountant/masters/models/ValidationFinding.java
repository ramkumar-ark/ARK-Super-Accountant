package com.arktech.superaccountant.masters.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_findings")
@Data
@NoArgsConstructor
public class ValidationFinding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "upload_job_id", nullable = false)
    private UUID uploadJobId;

    @Column(name = "rule_code")
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    private LedgerCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "mismatch_type")
    private MismatchType mismatchType;

    @Column(name = "ledger_name")
    private String ledgerName;

    @Column(name = "expected_value")
    private String expectedValue;

    @Column(name = "actual_value")
    private String actualValue;

    @Enumerated(EnumType.STRING)
    private FindingSeverity severity;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "suggested_fix", columnDefinition = "TEXT")
    private String suggestedFix;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolve_status", nullable = false)
    private ResolveStatus resolveStatus = ResolveStatus.OPEN;

    @Column(name = "resolve_note", columnDefinition = "TEXT")
    private String resolveNote;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
