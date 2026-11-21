package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tally_change_items")
@Data
@NoArgsConstructor
public class TallyChangeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Convert(converter = TallyChangeTypeConverter.class)
    @Column(name = "change_type", length = 30, nullable = false)
    private TallyChangeType changeType;

    @Convert(converter = TallyFieldConverter.class)
    @Column(name = "field", length = 40, nullable = false)
    private TallyField field;

    @Column(name = "ledger_key", nullable = false)
    private String ledgerKey;

    @Column(name = "ledger_name", nullable = false)
    private String ledgerName;

    @Column(name = "tally_guid")
    private String tallyGuid;

    @Column(name = "baseline_value", length = 255)
    private String baselineValue;

    @Column(name = "baseline_upload_job_id")
    private UUID baselineUploadJobId;

    @Column(name = "new_value", length = 255, nullable = false)
    private String newValue;

    @Convert(converter = TallyChangeSourceTypeConverter.class)
    @Column(name = "source_type", length = 30, nullable = false)
    private TallyChangeSourceType sourceType;

    @Column(name = "source_override_id")
    private UUID sourceOverrideId;

    @Column(name = "source_finding_id")
    private UUID sourceFindingId;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Convert(converter = TallyChangeStatusConverter.class)
    @Column(name = "status", length = 20, nullable = false)
    private TallyChangeStatus status;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "observed_value", length = 255)
    private String observedValue;

    @Convert(converter = TallyFailureReasonConverter.class)
    @Column(name = "failure_reason", length = 30)
    private TallyFailureReason failureReason;

    @Convert(converter = ExistenceOutcomeConverter.class)
    @Column(name = "last_check_outcome", length = 20)
    private ExistenceOutcome lastCheckOutcome;

    @Column(name = "last_check_job_id")
    private UUID lastCheckJobId;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "applied_upload_job_id")
    private UUID appliedUploadJobId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
