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

    @Column(name = "parent_group")
    private String parentGroup;

    @Column(name = "gst_applicable")
    private Boolean gstApplicable;

    @Column(name = "tds_applicable")
    private Boolean tdsApplicable;

    @Column(name = "gstin")
    private String gstin;

    @Column(name = "tds_section")
    private String tdsSection;

    @Column(name = "hsn_sac_code")
    private String hsnSacCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_applicability_type")
    private GstApplicabilityType gstApplicabilityType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
