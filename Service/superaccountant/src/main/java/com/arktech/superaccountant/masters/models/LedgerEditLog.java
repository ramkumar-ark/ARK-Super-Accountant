package com.arktech.superaccountant.masters.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_edit_log")
@Data
@NoArgsConstructor
public class LedgerEditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "upload_job_id", nullable = false)
    private UUID uploadJobId;

    @Column(name = "ledger_snapshot_id")
    private UUID ledgerSnapshotId;

    @Column(name = "ledger_name", nullable = false)
    private String ledgerName;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "edited_by", nullable = false)
    private String editedBy;

    @Column(name = "edited_at", nullable = false, updatable = false)
    private Instant editedAt = Instant.now();
}
