package com.arktech.superaccountant.workflow.snapshot;

import com.arktech.superaccountant.masters.models.ResolveStatus;
import com.arktech.superaccountant.review.LifecycleFinding;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tamper_findings")
public class TamperFinding implements LifecycleFinding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizationId;

    private UUID tamperChangeId;

    private String findingKey;

    private String evidenceHash;

    private UUID carriedFromFindingId;

    @Convert(converter = TamperChangeKindConverter.class)
    private TamperChangeKind changeKind;

    private String voucherKey;

    /** Masters findings: the display ledger name; null on voucher findings. */
    private String ledgerName;

    /** Masters findings: the frozen attribute name; null on voucher findings. */
    private String attributeName;

    /** Masters findings: frozen value; null on voucher findings. */
    private String oldValue;

    /** Masters findings: uploaded value; null on voucher findings. */
    private String newValue;

    /** Masters findings: max periodEnd of the affected frozen rows; null on voucher findings. */
    private LocalDate frozenPeriodEnd;

    private String voucherTypeName;

    private String voucherNumber;

    private String partyLedgerName;

    private LocalDate oldDate;

    private LocalDate newDate;

    private LocalDate changeDate;

    private String oldHash;

    private String newHash;

    private UUID detectedInUploadJobId;

    private String affectedTypes;

    private String affectedWorkflowIds;

    private String message;

    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private ResolveStatus resolveStatus = ResolveStatus.OPEN;

    private String resolveNote;

    private String proposedValue;

    private String submittedBy;

    private Instant submittedAt;

    private String reviewedBy;

    private Instant reviewedAt;

    private String reviewNote;

    private String resolvedBy;

    private Instant resolvedAt;
}
