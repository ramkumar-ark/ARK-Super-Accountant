package com.arktech.superaccountant.statutory.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One app-administrator write to the global statutory configuration: who changed what and
 * when. Append-only; written in the same transaction as the change it records.
 */
@Entity
@Table(name = "statutory_audit_entries",
        indexes = @Index(name = "idx_statutory_audit_occurred_at", columnList = "occurred_at"))
@Data
@NoArgsConstructor
public class StatutoryAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 50)
    private String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutoryAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statute statute;

    @Column(name = "section_code", nullable = false, length = 20)
    private String sectionCode;

    /** The section written; kept as a plain id so a deleted section's history survives. */
    @Column(name = "section_id")
    private UUID sectionId;

    @Column(name = "rule_id")
    private UUID ruleId;

    /** Human-readable summary of the values written (and, for supersession, the new end date). */
    @Column(columnDefinition = "text")
    private String details;
}
