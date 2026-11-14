package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_events")
@Data
@NoArgsConstructor
public class WorkflowEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "actor")
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Convert(converter = WorkflowEventKindConverter.class)
    @Column(nullable = false, length = 32)
    private WorkflowEventKind kind;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;
}
