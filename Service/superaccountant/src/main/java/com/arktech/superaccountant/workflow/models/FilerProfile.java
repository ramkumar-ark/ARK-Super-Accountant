package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "filer_profiles")
@Data
@NoArgsConstructor
public class FilerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Convert(converter = FilerFrequencyConverter.class)
    @Column(nullable = false, length = 16)
    private FilerFrequency frequency;

    @Column(name = "iff_opt_in", nullable = false)
    private boolean iffOptIn = false;

    @Column(name = "einvoice_enabled", nullable = false)
    private boolean einvoiceEnabled = false;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
