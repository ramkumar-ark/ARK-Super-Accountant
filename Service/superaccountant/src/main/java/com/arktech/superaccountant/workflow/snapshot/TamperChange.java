package com.arktech.superaccountant.workflow.snapshot;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
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
@Table(name = "tamper_changes")
public class TamperChange {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizationId;

    private UUID uploadJobId;

    private String voucherKey;

    /** Masters changes: the display ledger name; null on voucher changes. */
    private String ledgerName;

    /** Masters changes: the frozen attribute name; null on voucher changes. */
    private String attributeName;

    /** Masters changes: frozen value; null on voucher changes. */
    private String oldValue;

    /** Masters changes: uploaded value; null on voucher changes. */
    private String newValue;

    @Convert(converter = TamperChangeKindConverter.class)
    private TamperChangeKind changeKind;

    private String oldHash;

    private String newHash;

    private LocalDate oldDate;

    private LocalDate newDate;

    private String voucherTypeName;

    private String voucherNumber;

    private String partyLedgerName;

    private String touchedScopes;

    /** Retro flags: the TDS section whose annual threshold was crossed later. */
    private String retroSection;

    /** Retro flags: TDS shortfall now due on this earlier voucher. */
    private java.math.BigDecimal retroShortfall;

    private boolean blocking;

    private String blockingWorkflowIds;

    private Instant createdAt;
}
