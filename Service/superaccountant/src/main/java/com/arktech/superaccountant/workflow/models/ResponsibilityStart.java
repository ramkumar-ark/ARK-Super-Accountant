package com.arktech.superaccountant.workflow.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "responsibility_starts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "type"}))
@Data
@NoArgsConstructor
public class ResponsibilityStart {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Convert(converter = WorkflowTypeConverter.class)
    @Column(nullable = false, length = 32)
    private WorkflowType type;

    @Column(name = "first_period_start", nullable = false)
    private LocalDate firstPeriodStart;

    @Column(name = "set_by", nullable = false)
    private String setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt = Instant.now();
}
