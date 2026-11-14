package com.arktech.superaccountant.workflow.analysis;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_analysis_runs")
@Data
@NoArgsConstructor
public class WorkflowAnalysisRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "analyzer_code", nullable = false, length = 64)
    private String analyzerCode;

    @Column(name = "upload_job_id")
    private UUID uploadJobId;

    @Column(name = "masters_job_id")
    private UUID mastersJobId;

    @Convert(converter = AnalysisRunStatusConverter.class)
    @Column(nullable = false, length = 16)
    private AnalysisRunStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
