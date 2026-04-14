package com.arktech.superaccountant.masters.models;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "user_organizations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "organization_id"})
)
@Data
@NoArgsConstructor
public class UserOrganization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private ERole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    public UserOrganization(User user, Organization organization, ERole role) {
        this.user = user;
        this.organization = organization;
        this.role = role;
        this.joinedAt = Instant.now();
    }
}
