package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "baseline_imports")
@Data
@NoArgsConstructor
public class BaselineImport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @Column(name = "filed_return_ref")
    private String filedReturnRef;

    @Column(name = "discrepancy_count")
    private Integer discrepancyCount;
}
