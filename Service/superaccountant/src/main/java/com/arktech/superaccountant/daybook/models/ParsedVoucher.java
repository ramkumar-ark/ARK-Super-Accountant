package com.arktech.superaccountant.daybook.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "parsed_vouchers")
@Data
@NoArgsConstructor
public class ParsedVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "upload_job_id", nullable = false)
    private UUID uploadJobId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    // Identity — nullable: some Tally voucher types lack GUIDs
    @Column(name = "voucher_guid")
    private String voucherGuid;

    @Column(name = "voucher_number")
    private String voucherNumber;

    @Column(name = "voucher_type_name", nullable = false)
    private String voucherTypeName;

    // Normalized from Tally YYYYMMDD string — nullable: opening balance entries may lack dates
    @Column(name = "voucher_date")
    private LocalDate voucherDate;

    // Party info
    @Column(name = "party_ledger_name")
    private String partyLedgerName;

    @Column(name = "party_gstin")
    private String partyGstin;

    @Column(name = "narration", columnDefinition = "TEXT")
    private String narration;

    // Aggregate amounts computed during persistence for summary queries
    // totalDebit = sum of positive entries (Tally convention: positive = debit side)
    @Column(name = "total_debit", precision = 19, scale = 2)
    private BigDecimal totalDebit;

    // totalCredit = sum of absolute values of negative entries (Tally convention: negative = credit side)
    @Column(name = "total_credit", precision = 19, scale = 2)
    private BigDecimal totalCredit;

    // Flags from Tally
    private boolean isInvoice;
    private boolean isReverseChargeApplicable;
    private boolean isCancelled;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parsedVoucher")
    private List<ParsedVoucherEntry> entries;
}
