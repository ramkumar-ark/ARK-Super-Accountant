package com.arktech.superaccountant.masters.models;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_invites")
@Data
@NoArgsConstructor
public class OrganizationInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private ERole role;

    @Column(name = "token", nullable = false, unique = true, length = 36)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public OrganizationInvite(Organization organization, ERole role, User createdBy) {
        this.organization = organization;
        this.role = role;
        this.createdBy = createdBy;
        this.token = UUID.randomUUID().toString();
        this.expiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60); // 7 days
    }

    public boolean isValid() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }
}
