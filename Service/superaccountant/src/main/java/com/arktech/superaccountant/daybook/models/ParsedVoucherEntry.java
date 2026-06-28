package com.arktech.superaccountant.daybook.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "parsed_voucher_entries")
@Data
@NoArgsConstructor
public class ParsedVoucherEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parsed_voucher_id", nullable = false)
    private ParsedVoucher parsedVoucher;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "ledger_name", nullable = false)
    private String ledgerName;

    // Preserved sign: negative = credit side per Tally convention, positive = debit side
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Derived: true when amount.compareTo(BigDecimal.ZERO) > 0 (Tally convention: positive = debit)
    @Column(name = "is_debit", nullable = false)
    private boolean isDebit;

    // Original string preserved for debugging
    @Column(name = "raw_amount")
    private String rawAmount;

    // GST classification field from LedgerEntry (for Phase 6 GST validation use)
    @Column(name = "gst_class")
    private String gstClass;

    @Column(name = "is_party_ledger")
    private boolean isPartyLedger;
}
