package com.arktech.superaccountant.filedreturns;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "filed_return_snapshots")
@Data
@NoArgsConstructor
public class FiledReturnSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Convert(converter = FiledFormTypeConverter.class)
    @Column(name = "form_type", nullable = false, length = 16)
    private FiledFormType formType;

    @Column(name = "filing_period", nullable = false, length = 7)
    private String filingPeriod;

    @Column(name = "cover_start", nullable = false)
    private LocalDate coverStart;

    @Column(name = "cover_end", nullable = false)
    private LocalDate coverEnd;

    @Column(name = "baseline_workflow_id", nullable = false)
    private UUID baselineWorkflowId;

    @Column(name = "gstin", nullable = false, length = 15)
    private String gstin;

    @Convert(converter = FiledSourceKindConverter.class)
    @Column(name = "source_kind", nullable = false, length = 16)
    private FiledSourceKind sourceKind;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "parser_version")
    private String parserVersion;

    @Column(name = "imported_by", nullable = false)
    private String importedBy;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt = Instant.now();

    @Column(name = "superseded_at")
    private Instant supersededAt;
}
