package com.arktech.superaccountant.tallywriteback.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tally_export_batches")
@Data
@NoArgsConstructor
public class TallyExportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "masters_job_id")
    private UUID mastersJobId;

    @Column(name = "masters_uploaded_at")
    private Instant mastersUploadedAt;

    @Column(name = "stale_confirmed")
    private Boolean staleConfirmed;

    @Column(name = "item_count")
    private Integer itemCount;

    @Column(name = "file_sha256", length = 64)
    private String fileSha256;
}
