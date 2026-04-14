---
plan: 5
phase: 1
wave: 2
depends_on:
  - plan-04
files_modified:
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/UserOrganization.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/OrganizationInvite.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/UserOrganizationRepository.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/OrganizationInviteRepository.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/models/User.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/services/UserDetailsImpl.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/services/UserDetailsServiceImpl.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java
  - Client/src/store/authStore.ts
  - Client/src/pages/SignupPage.tsx
  - Client/src/components/OrganizationSelector.tsx
  - Client/src/components/InviteSignupBanner.tsx
  - Client/src/components/InviteTokenDisplay.tsx
autonomous: true
requirements:
  - ORG-02
  - ORG-03
must_haves:
  truths:
    - "OWNER or ACCOUNTANT can create an invite token via POST /api/organizations/{id}/invites; token is returned in the response"
    - "A new user can sign up at POST /api/auth/signup?invite={token}; on success they are linked to the organization with the pre-assigned role"
    - "An expired or used invite token returns 400 with a clear error message"
    - "Authenticated user can get their organization list from GET /api/organizations/me/list"
    - "Authenticated user can switch active organization via POST /api/organizations/{id}/select; response contains a new JWT"
    - "User.organizationId field is removed; org context comes from UserOrganization membership + JWT claim"
    - "OrganizationSelector component renders in the frontend and calls the switch endpoint"
  artifacts:
    - path: "Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/UserOrganization.java"
      provides: "UserOrganization join table entity"
      contains: "UserOrganization"
    - path: "Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/OrganizationInvite.java"
      provides: "Invite token entity"
      contains: "OrganizationInvite"
    - path: "Client/src/components/OrganizationSelector.tsx"
      provides: "Org switching UI component"
      contains: "OrganizationSelector"
  key_links:
    - from: "POST /api/auth/signup?invite={token}"
      to: "OrganizationInviteRepository.findByToken()"
      via: "AuthController invite param"
      pattern: "findByToken"
    - from: "POST /api/organizations/{id}/select"
      to: "JwtUtils.generateJwtTokenForOrg()"
      via: "OrganizationController selectOrganization"
      pattern: "generateJwtTokenForOrg"
    - from: "OrganizationSelector"
      to: "POST /api/organizations/{id}/select"
      via: "api.post('/organizations/{id}/select')"
      pattern: "api.post.*select"
---

# Plan 5: Invite Flow + Multi-Org Membership

## Goal
Replace `User.organizationId` (single-org model) with `UserOrganization` join table (multi-org model per D-01); implement the invitation flow (D-07 to D-10); add org-switching endpoint (D-04); build the `OrganizationSelector` and `InviteSignupBanner` frontend components per UI spec.

## Context
This plan is the largest in Phase 1. It makes a structural change to the auth model: `User.organizationId` is removed and replaced by `UserOrganization` (userId, organizationId, role, joinedAt). The JWT retains an `organizationId` claim representing the currently active org. Switching org issues a new JWT. This plan depends on Plan 4 being complete (OrganizationController and Organization entity exist with new fields). (D-01 through D-10, D-13, D-06)

<threat_model>
## Threat Model (ASVS L1)

### Threats Addressed

- **[HIGH] T-05-01 — Elevation of Privilege: Invite token brute force** — UUIDs as tokens have 122 bits of entropy — effectively un-brute-forceable. Mitigation: use `UUID.randomUUID()` for token generation; no sequential IDs.

- **[HIGH] T-05-02 — Spoofing: User switches to an organization they are not a member of** — `POST /api/organizations/{id}/select` must validate membership. Mitigation: controller calls `userOrganizationRepository.findByUserIdAndOrganizationId(userId, orgId)` — returns 403 if absent (D-05).

- **[MED] T-05-03 — Tampering: Reusing an already-used invite token** — Token reuse allows unlimited signups with the same role to the same org. Mitigation: set `usedAt = Instant.now()` on token consumption; check `usedAt == null` on validation (D-10).

- **[MED] T-05-04 — Tampering: Token expiry bypass** — Tokens must expire after 7 days (D-10). Mitigation: check `expiresAt.isAfter(Instant.now())` in invite validation; return 400 with "This invite link has expired or has already been used."

- **[LOW] T-05-05 — Information Disclosure: Invite token in server logs** — Tokens appear in URL query params which may be logged. Mitigation: limit log level for `/api/auth/signup` route; note this in summary. Full mitigation (POST body token) is a later concern.

### Residual Risks

- `User.organizationId` removal: Existing users in the database have `organization_id` set. Hibernate `ddl-auto: update` will NOT drop the column — it only adds columns. The old column remains in the DB but is no longer mapped in the entity. A manual SQL cleanup (`ALTER TABLE users DROP COLUMN organization_id`) is deferred — no data loss risk since the column is unused after this plan.
- `UserDetailsImpl.organizationId` now requires a DB lookup to resolve during JWT validation — the active org ID comes from JWT claim, not a DB join. This is a deliberate trade-off for stateless JWT architecture.
</threat_model>

## Tasks

<task id="1">
<title>Create UserOrganization and OrganizationInvite entities; update User entity to remove direct org reference</title>
<read_first>
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/models/User.java` — current entity; `organizationId` UUID field to be removed
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/services/UserDetailsImpl.java` — uses `user.getOrganizationId()` in `build()` — must be updated after User changes
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/services/UserDetailsServiceImpl.java` — builds UserDetailsImpl from User; check for organizationId usage
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java` — currently calls `userPrincipal.getOrganizationId()` in generateJwtToken; needs new method signature
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java` — currently does `user.setOrganizationId(orgId)` in createOrganization; replace with UserOrganization creation
</read_first>
<action>
**Create `UserOrganization.java`** in `com.arktech.superaccountant.masters.models`:

```java
package com.arktech.superaccountant.masters.models;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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
```

**Create `OrganizationInvite.java`** in `com.arktech.superaccountant.masters.models`:

```java
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
```

**Create `UserOrganizationRepository.java`** in `com.arktech.superaccountant.masters.repository`:

```java
package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.UserOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserOrganizationRepository extends JpaRepository<UserOrganization, Long> {
    List<UserOrganization> findByUserId(Long userId);
    Optional<UserOrganization> findByUserIdAndOrganizationId(Long userId, java.util.UUID organizationId);
}
```

**Create `OrganizationInviteRepository.java`** in `com.arktech.superaccountant.masters.repository`:

```java
package com.arktech.superaccountant.masters.repository;

import com.arktech.superaccountant.masters.models.OrganizationInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationInviteRepository extends JpaRepository<OrganizationInvite, UUID> {
    Optional<OrganizationInvite> findByToken(String token);
}
```

**Update `User.java` — remove `organizationId` field:**

Remove the field:
```java
// REMOVE these two lines:
@Column(name = "organization_id")
private UUID organizationId;
```

Remove `import java.util.UUID;` only if no other UUID usage remains in the file. Keep the rest of the class unchanged.

**Update `UserDetailsImpl.java`:**

The `organizationId` field is now populated from the JWT claim (not from User entity). Change `build(User user)` to accept an additional `UUID activeOrganizationId` parameter:

```java
public static UserDetailsImpl build(User user, UUID activeOrganizationId) {
    List<GrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority(user.getRole().getName().name()));

    return new UserDetailsImpl(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPassword(),
            activeOrganizationId,
            authorities);
}
```

Keep the existing 6-argument constructor. The `organizationId` getter remains unchanged.

**Update `UserDetailsServiceImpl.java`:**

Find the call to `UserDetailsImpl.build(user)` and update it to `UserDetailsImpl.build(user, null)` — `null` here means no active org context during the initial authentication flow. The active org ID is set from JWT claim in `AuthTokenFilter`.

**Update `AuthTokenFilter.java`:**

Read `AuthTokenFilter.java` before editing. In `doFilterInternal`, after `UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal()`, extract the `organizationId` claim from the JWT and build a new `UserDetailsImpl` with it:

```java
String orgIdClaim = jwtUtils.getOrganizationIdFromJwtToken(jwt);
UUID activeOrgId = orgIdClaim != null ? UUID.fromString(orgIdClaim) : null;
// Rebuild principal with active org context from JWT:
UserDetailsImpl principal = UserDetailsImpl.build(
    ((UserDetailsImpl) authentication.getPrincipal()).getUser(), activeOrgId);
```

This requires `UserDetailsImpl` to store a reference to the `User` object or `id`. Simpler approach: extract org ID from JWT in `AuthTokenFilter` and set it on the principal via a setter, or rebuild from scratch. Preferred: add `getOrganizationIdFromJwtToken(String token)` to `JwtUtils` that extracts the `organizationId` claim:

```java
public String getOrganizationIdFromJwtToken(String token) {
    Claims claims = Jwts.parser().verifyWith(key()).build()
            .parseSignedClaims(token).getPayload();
    return claims.get("organizationId", String.class);
}
```

Then in `AuthTokenFilter.doFilterInternal`, after setting the `SecurityContext`, look up the user again with the org ID from the JWT to rebuild a properly-scoped `UserDetailsImpl`. The simplest implementation: keep `UserDetailsImpl.organizationId` settable after construction (add a setter or change it to non-final), then call `userDetails.setOrganizationId(activeOrgId)` in `AuthTokenFilter`.

Add a public `setOrganizationId(UUID id)` method to `UserDetailsImpl` (Lombok `@Data` would generate it if organizationId is a field — it already is, so the setter may already exist).

**Update `JwtUtils.generateJwtToken(Authentication)` method:**

Add a new overload `generateJwtTokenForOrg(Authentication auth, UUID organizationId)` that puts a specific org ID in the JWT claim — used by the org-switch endpoint:

```java
public String generateJwtTokenForOrg(Authentication authentication, UUID organizationId) {
    UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
    return Jwts.builder()
        .subject(userPrincipal.getUsername())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
        .claim("organizationId", organizationId != null ? organizationId.toString() : null)
        .signWith(key())
        .compact();
}

public String generateJwtTokenForUser(String username, UUID organizationId) {
    return Jwts.builder()
        .subject(username)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
        .claim("organizationId", organizationId != null ? organizationId.toString() : null)
        .signWith(key())
        .compact();
}
```

`generateJwtTokenForUser(username, orgId)` is used by the org-switch endpoint where we don't have a Spring `Authentication` object — we have the user entity.

**Update `OrganizationController.createOrganization`:**

Replace the current `user.setOrganizationId(orgId)` + `userRepository.save(user)` block with a `UserOrganization` creation:

```java
@Autowired
private UserOrganizationRepository userOrganizationRepository;

// In createOrganization, after org is saved:
final UUID orgId = org.getId();
userRepository.findById(principal.getId()).ifPresent(user -> {
    UserOrganization membership = new UserOrganization(user, org, ERole.ROLE_OWNER);
    userOrganizationRepository.save(membership);
});
```

Add `import com.arktech.superaccountant.masters.models.UserOrganization;` and `import com.arktech.superaccountant.masters.repository.UserOrganizationRepository;`.
</action>
<acceptance_criteria>
- `UserOrganization.java` exists in `masters/models/` and contains `@Entity` and `@Table(name = "user_organizations")`
- `OrganizationInvite.java` exists in `masters/models/` and contains `isValid()` method
- `UserOrganizationRepository.java` exists and contains `findByUserId` and `findByUserIdAndOrganizationId`
- `OrganizationInviteRepository.java` exists and contains `findByToken`
- `User.java` does NOT contain `organization_id` column mapping
- `JwtUtils.java` contains `generateJwtTokenForUser`
- `JwtUtils.java` contains `getOrganizationIdFromJwtToken`
- Running `cd Service/superaccountant && ./mvnw compile -q` (with JWT_SECRET set) exits 0
</acceptance_criteria>
</task>

<task id="2">
<title>Implement invite endpoints, org-list endpoint, and org-switch endpoint; update AuthController for invite signup</title>
<read_first>
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java` — add three new endpoints: POST /invites, GET /me/list, POST /{id}/select
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/controllers/AuthController.java` — extend signup to accept ?invite=token query param
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/models/ERole.java` — ERole enum values available for invite role assignment
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java` — use generateJwtTokenForUser for org-switch
</read_first>
<action>
**OrganizationController.java — add three new endpoints:**

**1. `POST /api/organizations/{id}/invites` — create invite (D-07, D-08, D-11):**
```java
@Autowired
private OrganizationInviteRepository organizationInviteRepository;

@PreAuthorize("hasRole('OWNER') or hasRole('ACCOUNTANT')")
@PostMapping("/{id}/invites")
public ResponseEntity<?> createInvite(
        @PathVariable UUID id,
        @RequestParam String role,
        @AuthenticationPrincipal UserDetailsImpl principal) {

    // Verify caller is a member of this org
    Optional<UserOrganization> membership = userOrganizationRepository
            .findByUserIdAndOrganizationId(principal.getId(), id);
    if (membership.isEmpty()) {
        return ResponseEntity.status(403).body(new MessageResponse("Access denied to this organization"));
    }

    Optional<Organization> orgOpt = organizationRepository.findById(id);
    if (orgOpt.isEmpty()) {
        return ResponseEntity.notFound().build();
    }

    ERole inviteRole;
    try {
        inviteRole = ERole.valueOf("ROLE_" + role.toUpperCase());
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new MessageResponse("Invalid role: " + role));
    }

    User callerUser = userRepository.findById(principal.getId())
            .orElseThrow(() -> new RuntimeException("User not found"));

    OrganizationInvite invite = new OrganizationInvite(orgOpt.get(), inviteRole, callerUser);
    organizationInviteRepository.save(invite);

    return ResponseEntity.ok(Map.of(
        "token", invite.getToken(),
        "expiresAt", invite.getExpiresAt(),
        "role", role,
        "organizationId", id
    ));
}
```

**2. `GET /api/organizations/me/list` — list user's orgs (D-06, D-12):**
```java
@PreAuthorize("isAuthenticated()")
@GetMapping("/me/list")
public ResponseEntity<?> getMyOrganizations(@AuthenticationPrincipal UserDetailsImpl principal) {
    List<UserOrganization> memberships = userOrganizationRepository.findByUserId(principal.getId());

    List<Map<String, Object>> result = memberships.stream().map(m -> {
        Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("organizationId", m.getOrganization().getId());
        entry.put("organizationName", m.getOrganization().getName());
        entry.put("role", m.getRole().name());
        entry.put("isActive", m.getOrganization().getId().equals(principal.getOrganizationId()));
        return entry;
    }).collect(java.util.stream.Collectors.toList());

    return ResponseEntity.ok(result);
}
```

**3. `POST /api/organizations/{id}/select` — switch org, return new JWT (D-04, D-05, D-13):**
```java
@PreAuthorize("isAuthenticated()")
@PostMapping("/{id}/select")
public ResponseEntity<?> selectOrganization(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetailsImpl principal) {

    Optional<UserOrganization> membership = userOrganizationRepository
            .findByUserIdAndOrganizationId(principal.getId(), id);
    if (membership.isEmpty()) {
        return ResponseEntity.status(403)
                .body(new MessageResponse("Could not switch to that organization. You may no longer be a member. Refresh and try again."));
    }

    String newJwt = jwtUtils.generateJwtTokenForUser(principal.getUsername(), id);

    return ResponseEntity.ok(Map.of(
        "token", newJwt,
        "organizationId", id,
        "organizationName", membership.get().getOrganization().getName(),
        "role", membership.get().getRole().name()
    ));
}
```

Add `@Autowired private JwtUtils jwtUtils;` to `OrganizationController`.

**Also add `GET /api/auth/invite/{token}` endpoint for pre-validating invite before signup form renders:**

Add to `AuthController.java`:
```java
@GetMapping("/invite/{token}")
public ResponseEntity<?> validateInviteToken(@PathVariable String token) {
    Optional<OrganizationInvite> inviteOpt = organizationInviteRepository.findByToken(token);
    if (inviteOpt.isEmpty()) {
        return ResponseEntity.badRequest().body(new MessageResponse("This invite link is invalid. Check the link and try again."));
    }
    OrganizationInvite invite = inviteOpt.get();
    if (!invite.isValid()) {
        return ResponseEntity.badRequest().body(new MessageResponse("This invite link has expired or has already been used. Ask your organization admin to send a new one."));
    }
    return ResponseEntity.ok(Map.of(
        "organizationName", invite.getOrganization().getName(),
        "role", invite.getRole().name().replace("ROLE_", "")
    ));
}
```

Add `@Autowired private OrganizationInviteRepository organizationInviteRepository;` to `AuthController`.

**Extend `AuthController.registerUser` to accept `?invite=token` (D-09):**

Change the method signature to:
```java
@PostMapping("/signup")
public ResponseEntity<?> registerUser(
        @Valid @RequestBody SignupRequest signUpRequest,
        @RequestParam(required = false) String invite) {
```

After the existing duplicate-check block and before the user save, add invite validation:
```java
OrganizationInvite resolvedInvite = null;
if (invite != null && !invite.isBlank()) {
    Optional<OrganizationInvite> inviteOpt = organizationInviteRepository.findByToken(invite);
    if (inviteOpt.isEmpty()) {
        return ResponseEntity.badRequest().body(new MessageResponse("This invite link is invalid. Check the link and try again."));
    }
    resolvedInvite = inviteOpt.get();
    if (!resolvedInvite.isValid()) {
        return ResponseEntity.badRequest().body(new MessageResponse("This invite link has expired or has already been used. Ask your organization admin to send a new one."));
    }
    // Override role from invite
    userRole = roleRepository.findByName(resolvedInvite.getRole())
            .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
}
```

After `userRepository.save(user)`, if `resolvedInvite` is not null:
```java
if (resolvedInvite != null) {
    UserOrganization membership = new UserOrganization(user, resolvedInvite.getOrganization(), resolvedInvite.getRole());
    userOrganizationRepository.save(membership);
    resolvedInvite.setUsedAt(Instant.now());
    organizationInviteRepository.save(resolvedInvite);
}
```

Add `@Autowired private UserOrganizationRepository userOrganizationRepository;` to `AuthController`.
Add imports: `com.arktech.superaccountant.masters.models.OrganizationInvite`, `com.arktech.superaccountant.masters.models.UserOrganization`, `com.arktech.superaccountant.masters.repository.OrganizationInviteRepository`, `com.arktech.superaccountant.masters.repository.UserOrganizationRepository`, `java.time.Instant`.
</action>
<acceptance_criteria>
- `OrganizationController.java` contains `@PostMapping("/{id}/invites")`
- `OrganizationController.java` contains `@GetMapping("/me/list")`
- `OrganizationController.java` contains `@PostMapping("/{id}/select")`
- `AuthController.java` contains `@GetMapping("/invite/{token}")`
- `AuthController.java` contains `@RequestParam(required = false) String invite` in registerUser signature
- `AuthController.java` contains `resolvedInvite.setUsedAt`
- Running `cd Service/superaccountant && ./mvnw compile -q` (with JWT_SECRET set) exits 0
</acceptance_criteria>
</task>

<task id="3">
<title>Build OrganizationSelector, InviteSignupBanner, and InviteTokenDisplay frontend components; update authStore and SignupPage</title>
<read_first>
- `Client/src/store/authStore.ts` — current auth store shape; needs organizations list and org switching support
- `Client/src/pages/SignupPage.tsx` — needs InviteSignupBanner above the form when ?invite param present
- `.planning/phases/01-security-hardening-foundation/01-UI-SPEC.md` — OrganizationSelector, InviteSignupBanner, InviteTokenDisplay component specs; interaction contracts; copywriting contract
- `Client/src/pages/OrganizationSetupPage.tsx` (from Plan 4) — reference for page-level layout patterns
</read_first>
<action>
**Update `authStore.ts`:**

Extend `AuthUser` interface and state to support org context:
```typescript
interface OrgMembership {
  organizationId: string
  organizationName: string
  role: string
  isActive: boolean
}

interface AuthUser {
  id: number
  username: string
  email: string
  role: string
  organizationId?: string
  organizationName?: string
}

interface AuthState {
  token: string | null
  user: AuthUser | null
  organizations: OrgMembership[]
  isAuthenticated: boolean
  login: (token: string, user: AuthUser) => void
  logout: () => void
  setOrganizations: (orgs: OrgMembership[]) => void
  switchOrganization: (token: string, org: { organizationId: string; organizationName: string; role: string }) => void
}
```

Add `organizations: []` to initial state. Add `setOrganizations` action: `setOrganizations: (orgs) => set({ organizations: orgs })`. Add `switchOrganization` action: updates `token` and sets the selected org as active in the `organizations` list + updates `user.organizationId` and `user.organizationName`.

**Create `Client/src/components/InviteSignupBanner.tsx`:**

Per UI spec:
```tsx
export function InviteSignupBanner({ orgName, role, error }: {
  orgName?: string
  role?: string
  error?: string
}) {
  if (error) {
    return (
      <div role="alert" className="mb-4 border-l-[3px] border-[var(--color-danger)] bg-[var(--color-danger-bg)] px-4 py-3 rounded-[var(--radius-md)]">
        <p className="text-sm text-[var(--color-danger)]">{error}</p>
      </div>
    )
  }
  return (
    <div role="status" className="mb-4 border-l-[3px] border-[var(--color-primary)] bg-[var(--color-surface)] px-4 py-3 rounded-[var(--radius-md)] shadow-sm">
      <p className="text-sm text-[var(--color-text-primary)]">
        You've been invited to join <strong>{orgName}</strong> as <strong>{role}</strong>
      </p>
    </div>
  )
}
```

**Create `Client/src/components/InviteTokenDisplay.tsx`:**

Per UI spec — shows token in read-only input with Copy button (Lucide `Copy` icon, 14px, always visible with label):
```tsx
import { Copy, CheckCircle } from 'lucide-react'
import { useState } from 'react'

export function InviteTokenDisplay({ token, expiresAt }: { token: string; expiresAt: string }) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(token)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const expiryDate = new Date(expiresAt).toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric' })

  return (
    <div className="mt-4 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-[var(--radius-md)] p-4">
      <p className="text-sm text-[var(--color-text-primary)] mb-2 font-medium">Invite Link Generated</p>
      <div className="flex items-center gap-2">
        <input
          type="text"
          readOnly
          value={token}
          className="flex-1 h-11 px-3 font-mono text-sm bg-[var(--color-surface-raised)] border border-[var(--color-border)] rounded-[var(--radius-md)] text-[var(--color-text-primary)]"
        />
        <button
          onClick={handleCopy}
          aria-label="Copy invite link to clipboard"
          className="flex items-center gap-1.5 h-11 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] transition-colors"
        >
          {copied ? <CheckCircle size={14} className="text-[var(--color-success)]" /> : <Copy size={14} />}
          <span>Copy</span>
        </button>
      </div>
      <p className="mt-2 text-xs text-[var(--color-text-muted)]">Expires in 7 days ({expiryDate})</p>
      <p className="mt-1 text-xs text-[var(--color-warning)]">Share this link privately. It can only be used once.</p>
    </div>
  )
}
```

**Create `Client/src/components/OrganizationSelector.tsx`:**

Per UI spec — renders in topbar when user belongs to 2+ orgs. Dropdown with org list, switch button, loading state per row:
```tsx
import { ChevronDown, X } from 'lucide-react'
import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'

export function OrganizationSelector() {
  const { user, organizations, switchOrganization } = useAuthStore()
  const [open, setOpen] = useState(false)
  const [switchingId, setSwitchingId] = useState<string | null>(null)
  const [rowError, setRowError] = useState<string | null>(null)

  if (organizations.length < 2) return null

  async function handleSwitch(orgId: string, orgName: string) {
    setSwitchingId(orgId)
    setRowError(null)
    try {
      const res = await api.post(`/organizations/${orgId}/select`, {})
      switchOrganization(res.data.token, {
        organizationId: orgId,
        organizationName: orgName,
        role: res.data.role,
      })
      setOpen(false)
      // Toast: "Switched to [Org Name]"
      // Use window.alert as placeholder if no toast library
    } catch {
      setRowError(orgId)
    } finally {
      setSwitchingId(null)
    }
  }

  const activeOrg = organizations.find(o => o.isActive)

  return (
    <div className="relative">
      <button
        onClick={() => setOpen(prev => !prev)}
        className="flex items-center gap-1.5 h-9 px-3 rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-raised)] text-sm text-[var(--color-text-primary)] transition-colors"
      >
        <span>{activeOrg?.organizationName ?? 'Select Organization'}</span>
        <ChevronDown size={18} />
      </button>

      {open && (
        <div
          role="listbox"
          className="absolute right-0 mt-1 w-72 max-h-80 overflow-y-auto bg-[var(--color-surface)] rounded-[var(--radius-md)] shadow-[var(--shadow-lg)] border border-[var(--color-border)] z-50"
        >
          {organizations.map(org => (
            <div
              key={org.organizationId}
              role="option"
              aria-selected={org.isActive}
              className={`flex items-center justify-between px-4 py-3 ${org.isActive ? 'bg-[var(--color-surface-raised)]' : 'hover:bg-[var(--color-surface-raised)] cursor-pointer'}`}
            >
              <div>
                <p className="text-sm text-[var(--color-text-primary)]">{org.organizationName}</p>
                <p className="text-xs text-[var(--color-text-muted)]">{org.role.replace('ROLE_', '')}</p>
              </div>
              <div className="flex items-center gap-2">
                {org.isActive && (
                  <span className="w-2 h-2 rounded-full bg-[var(--color-primary)]" title="Active" />
                )}
                {!org.isActive && (
                  switchingId === org.organizationId ? (
                    <span className="text-xs text-[var(--color-text-muted)]">Switching…</span>
                  ) : rowError === org.organizationId ? (
                    <span className="flex items-center gap-1 text-xs text-[var(--color-danger)]">
                      <X size={14} />
                      <span>Could not switch to that organization. You may no longer be a member. Refresh and try again.</span>
                    </span>
                  ) : (
                    <button
                      onClick={() => handleSwitch(org.organizationId, org.organizationName)}
                      aria-label={`Switch to ${org.organizationName}`}
                      className="text-xs text-[var(--color-primary)] font-medium hover:underline"
                    >
                      Switch to {org.organizationName}
                    </button>
                  )
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
```

**Update `SignupPage.tsx` to show InviteSignupBanner:**

At the top of `SignupPage`, read the `?invite` query param using TanStack Router's `useSearch()` hook (or `window.location.search`):
```tsx
const search = new URLSearchParams(window.location.search)
const inviteToken = search.get('invite')
```

Add state for invite validation:
```tsx
const [inviteContext, setInviteContext] = useState<{ orgName: string; role: string } | null>(null)
const [inviteError, setInviteError] = useState('')
```

Add a `useEffect` that runs when `inviteToken` is present: call `api.get('/auth/invite/' + inviteToken)` — on success set `inviteContext`; on error set `inviteError`.

Render `<InviteSignupBanner>` above the white card when `inviteToken` is present. If `inviteError` is set, disable the form (add `disabled` to all fields and the submit button).

When submitting with an invite, append `?invite=${inviteToken}` to the POST URL: `api.post('/auth/signup?invite=' + inviteToken, form)`.

After successful invite signup, redirect to `/dashboard` (not `/login`): the user is already linked to an org.
</action>
<acceptance_criteria>
- `Client/src/components/OrganizationSelector.tsx` exists and contains `role="listbox"`
- `Client/src/components/OrganizationSelector.tsx` contains `aria-label={\`Switch to`
- `Client/src/components/InviteSignupBanner.tsx` exists and contains `role="alert"` and `role="status"`
- `Client/src/components/InviteTokenDisplay.tsx` exists and contains `aria-label="Copy invite link to clipboard"`
- `Client/src/store/authStore.ts` contains `organizations`
- `Client/src/store/authStore.ts` contains `switchOrganization`
- `Client/src/pages/SignupPage.tsx` contains `InviteSignupBanner`
- Running `cd Client && npm run build` exits 0 (TypeScript clean)
</acceptance_criteria>
</task>

## Verification

```bash
# Backend
cd "Service/superaccountant"
export JWT_SECRET="a-32-char-or-longer-dummy-secret-value"
./mvnw compile -q

# New entities exist
ls src/main/java/com/arktech/superaccountant/masters/models/UserOrganization.java && echo "PASS" || echo "FAIL"
ls src/main/java/com/arktech/superaccountant/masters/models/OrganizationInvite.java && echo "PASS" || echo "FAIL"

# User no longer has organizationId column mapping
grep "organization_id" src/main/java/com/arktech/superaccountant/login/models/User.java && echo "FAIL: still present" || echo "PASS"

# Invite endpoints in OrganizationController
grep '"/\{id\}/invites"' src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java && echo "PASS" || echo "FAIL"
grep '"/me/list"' src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java && echo "PASS" || echo "FAIL"
grep '"/\{id\}/select"' src/main/java/com/arktech/superaccountant/masters/controllers/OrganizationController.java && echo "PASS" || echo "FAIL"

# Frontend
cd "../../Client"
npm run build
grep "OrganizationSelector" src/components/OrganizationSelector.tsx && echo "PASS" || echo "FAIL"
grep "switchOrganization" src/store/authStore.ts && echo "PASS" || echo "FAIL"
```

## must_haves
- `user_organizations` and `organization_invites` tables auto-created by Hibernate on next startup
- `POST /api/organizations/{id}/invites` returns `{token, expiresAt}` for OWNER/ACCOUNTANT; 403 for others
- `POST /api/auth/signup?invite={token}` links new user to org, marks token as used
- `GET /api/organizations/me/list` returns org list for authenticated user
- `POST /api/organizations/{id}/select` returns new JWT with updated `organizationId` claim; 403 if not a member
- `OrganizationSelector` renders in the frontend with org switching wired to the API
- Frontend builds without TypeScript errors

<output>
After completion, create `.planning/phases/01-security-hardening-foundation/01-05-SUMMARY.md`
</output>
