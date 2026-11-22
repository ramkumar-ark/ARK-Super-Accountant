package com.arktech.superaccountant.tds.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "debtor_tans",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "ledger_key"}),
        indexes = {
                @Index(name = "idx_debtor_tans_org_key", columnList = "organization_id, ledger_key"),
                @Index(name = "idx_debtor_tans_org_guid", columnList = "organization_id, tally_guid")
        })
@Data
@NoArgsConstructor
public class DebtorTan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "ledger_name", nullable = false)
    private String ledgerName;

    /** Normalised ledger key: TdsEngine.key(ledgerName). Unique per organization. */
    @Column(name = "ledger_key", nullable = false)
    private String ledgerKey;

    /** Tally GUID of the customer ledger, so the TAN survives a ledger rename. */
    @Column(name = "tally_guid")
    private String tallyGuid;

    @Column(name = "tan", length = 10, nullable = false)
    private String tan;

    @Column(name = "source_finding_id")
    private UUID sourceFindingId;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
