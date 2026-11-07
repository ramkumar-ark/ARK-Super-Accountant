package com.arktech.superaccountant.masters.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic per-organization key/value setting. One row per (organization, key); the value is text so any
 * setting type fits. Plain varchar/text columns (no @Enumerated) keep ddl-auto=update additive.
 */
@Entity
@Table(name = "organization_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_org_setting_key", columnNames = {"organization_id", "setting_key"}))
@Data
@NoArgsConstructor
public class OrganizationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
