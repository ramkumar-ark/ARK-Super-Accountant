package com.arktech.superaccountant.masters.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "preconfigured_masters")
@Data
@NoArgsConstructor
public class PreconfiguredMaster {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @NotBlank
    @Column(name = "ledger_name", nullable = false)
    private String ledgerName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerCategory category;

    @Column(name = "expected_parent_group")
    private String expectedParentGroup;

    @Column(name = "expected_gst_applicable")
    private Boolean expectedGstApplicable;

    @Column(name = "expected_tds_applicable")
    private Boolean expectedTdsApplicable;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_template", nullable = false)
    private boolean template = false;

    @Column(name = "template_slug")
    private String templateSlug;

    @Column(name = "tds_section")
    private String tdsSection;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_applicability_type")
    private GstApplicabilityType gstApplicabilityType;

    @Column(name = "hsn_sac_code")
    private String hsnSacCode;

    @Column(name = "gstin")
    private String gstin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
