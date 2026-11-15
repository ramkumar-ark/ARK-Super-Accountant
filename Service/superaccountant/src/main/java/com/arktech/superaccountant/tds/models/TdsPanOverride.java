package com.arktech.superaccountant.tds.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tds_pan_overrides",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "party_key"}))
@Data
@NoArgsConstructor
public class TdsPanOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "party_ledger_name")
    private String partyLedgerName;

    /** Normalised party key: TdsEngine.key(partyLedgerName). Unique per organization. */
    @Column(name = "party_key", nullable = false)
    private String partyKey;

    @Column(name = "pan", length = 10, nullable = false)
    private String pan;

    @Column(name = "source_finding_id")
    private UUID sourceFindingId;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
