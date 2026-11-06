package com.arktech.superaccountant.masters.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit row for a write to {@link PreconfiguredMaster}. All columns are plain
 * varchar/text (no @Enumerated) so ddl-auto=update never leaves stale CHECK constraints.
 */
@Entity
@Table(name = "master_edit_log")
@Data
@NoArgsConstructor
public class MasterEditLog {

    public static final String SOURCE_FINDING_OVERRIDE = "FINDING_OVERRIDE";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "master_id")
    private UUID masterId;

    @Column(name = "ledger_name", nullable = false)
    private String ledgerName;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /** The approving accountant. */
    @Column(name = "edited_by", nullable = false)
    private String editedBy;

    /** The user who proposed the value (equals editedBy for one-step accountant overrides). */
    @Column(name = "submitted_by")
    private String submittedBy;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "finding_id")
    private UUID findingId;

    @Column(name = "edited_at", nullable = false, updatable = false)
    private Instant editedAt = Instant.now();
}
