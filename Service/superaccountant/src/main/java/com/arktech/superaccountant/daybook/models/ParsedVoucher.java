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

    // Tally master identity for re-upload matching (worker R)
    @Column(name = "tally_master_id", length = 64)
    private String tallyMasterId;

    @Column(name = "tally_alter_id", length = 64)
    private String tallyAlterId;

    // SHA-256 hex (64 chars) of the canonical voucher form (VoucherCanonicalizer)
    @Column(name = "content_hash", length = 64)
    private String contentHash;

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

    // Raw Tally GSTREGISTRATIONTYPE of the party, stored unnormalised
    @Column(name = "party_gst_registration_type")
    private String partyGstRegistrationType;

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

    // GST section — stored outside the content hash so preparation edits do not
    // invalidate tamper evidence. Null when Tally did not send the field.
    @Column(name = "place_of_supply")
    private String placeOfSupply;

    @Column(name = "consignee_state")
    private String consigneeState;

    @Column(name = "consignee_country")
    private String consigneeCountry;

    // From LedgerEntry gstovrdnnature (first non-blank across entries)
    @Column(name = "supply_nature")
    private String supplyNature;

    // From LedgerEntry gstovrdntypeofsupply (first non-blank across entries)
    @Column(name = "type_of_supply")
    private String typeOfSupply;

    // 0 = captured before this column existed; new parser code sets 1
    @Column(name = "capture_version", nullable = false, columnDefinition = "int default 0")
    private int captureVersion = 0;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "parsedVoucher")
    private List<ParsedVoucherEntry> entries;
}
