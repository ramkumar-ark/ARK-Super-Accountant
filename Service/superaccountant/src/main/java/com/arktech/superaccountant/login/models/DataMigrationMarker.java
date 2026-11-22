package com.arktech.superaccountant.login.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Records that a one-time boot migration has been applied. Needed when the legacy
 * data value can legitimately reappear after the fix (e.g. deductor type P, which
 * the old UI meant as Individual/HUF but the fixed UI offers as AOP) — without a
 * marker the migration would keep rewriting user selections on every restart.
 * The table is created by ddl-auto on boot; one row per completed migration.
 */
@Entity
@Table(name = "data_migration_markers")
@Data
@NoArgsConstructor
public class DataMigrationMarker {

    @Id
    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt = Instant.now();

    public DataMigrationMarker(String name) {
        this.name = name;
    }
}
