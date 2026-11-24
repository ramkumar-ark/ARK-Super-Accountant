package com.arktech.superaccountant.masters.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_ledger_snapshots")
@Data
@NoArgsConstructor
public class UploadLedgerSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "upload_job_id", nullable = false)
    private UUID uploadJobId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "ledger_name", nullable = false)
    private String ledgerName;

    /** Tally's stable GUID for the ledger; null on snapshots uploaded before this column existed. */
    @Column(name = "tally_guid")
    private String tallyGuid;

    @Column(name = "parent_group")
    private String parentGroup;

    /** Classified ledger category; null on snapshots uploaded before this column existed. */
    @Enumerated(EnumType.STRING)
    private LedgerCategory category;

    @Column(name = "gst_applicable")
    private Boolean gstApplicable;

    @Column(name = "tds_applicable")
    private Boolean tdsApplicable;

    /** Raw Tally "type of duty/tax" (TDS / GST / Others); null when the export carries none. */
    @Column(name = "tally_tax_type")
    private String taxType;

    /** Raw Tally nature of payment (tdsratename): a specific value, "Any", or null when absent. */
    @Column(name = "tds_nature_of_payment")
    private String tdsNatureOfPayment;

    @Column(name = "gstin")
    private String gstin;

    @Column(name = "tds_section")
    private String tdsSection;

    @Column(name = "hsn_sac_code")
    private String hsnSacCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_applicability_type")
    private GstApplicabilityType gstApplicabilityType;

    /** Tally "GST registration type" (e.g. Regular, Composition, Unregistered/Consumer); null when not exported. */
    @Column(name = "gst_registration_type")
    private String gstRegistrationType;

    // Raw value from Tally, kept as-is (never truncated): a real PAN is 10 chars, but the
    // column matches its 255-char siblings so an over-length value cannot fail the batch insert.
    @Column(name = "pan", length = 255)
    private String pan;

    // Sign convention matches voucher entries: negative = credit balance, positive = debit balance
    @Column(name = "opening_balance", precision = 19, scale = 2)
    private java.math.BigDecimal openingBalance;

    /** The ledger's parent-group chain from the uploaded Tally data, lower-case, nearest group first, joined by '|'. */
    @Column(name = "group_chain", columnDefinition = "TEXT")
    private String groupChain;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
