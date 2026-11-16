package com.arktech.superaccountant.gst.mapping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tax_ledger_mappings", uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "ledger_key"}))
public class TaxLedgerMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "ledger_name", nullable = false, length = 255)
    private String ledgerName;

    @Column(name = "ledger_key", nullable = false, length = 255)
    private String ledgerKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TaxLedgerRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "head", length = 10)
    private GstHead head;

    @Column(name = "saved_by", nullable = false, length = 255)
    private String savedBy;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;
}
