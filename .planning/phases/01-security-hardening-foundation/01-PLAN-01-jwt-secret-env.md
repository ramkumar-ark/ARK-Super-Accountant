---
plan: 1
phase: 1
wave: 1
depends_on: []
files_modified:
  - Service/superaccountant/pom.xml
  - Service/superaccountant/src/main/resources/application.properties
  - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java
autonomous: true
requirements:
  - SEC-01
must_haves:
  truths:
    - "Starting the application without JWT_SECRET env var fails immediately with a clear error"
    - "JWT_SECRET is never read from application.properties — no fallback default exists"
    - "JWT signing uses jjwt 0.12.x APIs (SdkException, not legacy MalformedJwtException)"
  artifacts:
    - path: "Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java"
      provides: "JWT generation with env-var secret + startup validation"
      contains: "@PostConstruct"
    - path: "Service/superaccountant/src/main/resources/application.properties"
      provides: "Config without hardcoded secret"
      does_not_contain: "jwtSecret=super"
  key_links:
    - from: "JwtUtils"
      to: "JWT_SECRET env var"
      via: "@Value(\"${JWT_SECRET}\")"
      pattern: "@Value.*JWT_SECRET"
---

# Plan 1: JWT Secret — Environment Variable + jjwt 0.12.x Upgrade

## Goal
Move the JWT signing secret from a hardcoded `application.properties` value to a mandatory `JWT_SECRET` environment variable, add `@PostConstruct` startup validation that throws `IllegalStateException` if the secret is absent, and upgrade jjwt from 0.11.5 to 0.12.x.

## Context
`application.properties` line 11 currently contains `arktech.app.jwtSecret=superAccountantSecretKeyReferenceForJwtTokenGenerationPurposeOnly` — a plaintext secret checked into source control. This is incompatible with storing GSTIN and PAN data (ORG-01) because a leaked secret allows forged JWTs. The fix: remove the hardcoded value, bind `@Value("${JWT_SECRET}")`, and crash at startup if missing. jjwt is upgraded to 0.12.x simultaneously because 0.12.x's `Jwts.parser()` builder API is cleaner and aligns with the secret-loading changes. (D-18, D-19)

<threat_model>
## Threat Model (ASVS L1)

### Threats Addressed

- **[HIGH] T-01-01 — Spoofing / Tampering: JWT secret in source control** — Anyone with repo access can forge valid JWTs. Mitigation in this plan: remove `arktech.app.jwtSecret` from `application.properties`; bind to `JWT_SECRET` env var only; fail startup if absent. Env vars are not committed to source control.

- **[MED] T-01-02 — Spoofing: Weak signing key** — A short or low-entropy secret produces weak HMAC signatures. Mitigation in this plan: add `@PostConstruct` validation — throw `IllegalStateException` if `JWT_SECRET` is blank or fewer than 32 characters.

- **[LOW] T-01-03 — Tampering: jjwt 0.11.5 known issues** — jjwt 0.11.5 has minor API inconsistencies; 0.12.x resolves them and removes deprecated methods. Mitigation: upgrade in `pom.xml`, update call sites.

### Residual Risks

- Key rotation (changing JWT_SECRET invalidates all issued tokens — all users logged out): deferred. Acceptable for v1 — no session revocation mechanism exists yet.
- Env var value is still visible in process list on some OSes: acceptable for v1; secrets manager integration is beyond Phase 1 scope.
</threat_model>

## Tasks

<task id="1">
<title>Remove hardcoded JWT secret from application.properties; bind JWT_SECRET env var in JwtUtils</title>
<read_first>
- `Service/superaccountant/src/main/resources/application.properties` — current config; line 11 has the hardcoded secret to remove
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java` — current JWT utils; `@Value("${arktech.app.jwtSecret}")` to be replaced; `key()` and validate methods use jjwt 0.11.5 API
</read_first>
<action>
**Step 1 — application.properties:**
Remove the line `arktech.app.jwtSecret=superAccountantSecretKeyReferenceForJwtTokenGenerationPurposeOnly` entirely. Keep `arktech.app.jwtExpirationMs=86400000`. Do not add any replacement value — the secret comes from the environment only.

**Step 2 — JwtUtils.java (after jjwt upgrade in task 2):**
Replace `@Value("${arktech.app.jwtSecret}")` with `@Value("${JWT_SECRET}")`. Add a `@PostConstruct` method named `validateJwtSecret()` that throws `IllegalStateException("JWT_SECRET environment variable must be set and be at least 32 characters")` if `jwtSecret` is null, blank (`.isBlank()`), or shorter than 32 characters.

Update `generateJwtToken()` to use jjwt 0.12.x API:
```java
return Jwts.builder()
    .subject(userPrincipal.getUsername())
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
    .signWith(key())
    .compact();
```
(Note: `.setSubject()` → `.subject()`, `.setIssuedAt()` → `.issuedAt()`, `.setExpiration()` → `.expiration()`, `SignatureAlgorithm.HS256` parameter removed — key type drives algorithm in 0.12.x.)

Update `generateJwtToken()` to add `organizationId` claim using 0.12.x API — `.claim("organizationId", ...)` is unchanged.

Update `getUserNameFromJwtToken()`:
```java
return Jwts.parser().verifyWith((SecretKey) key()).build()
    .parseSignedClaims(token).getPayload().getSubject();
```

Update `validateJwtToken()` — catch `io.jsonwebtoken.security.SecurityException` and `io.jsonwebtoken.JwtException` (the 0.12.x hierarchy) instead of `MalformedJwtException`, `ExpiredJwtException`, `UnsupportedJwtException` individually. All of these are subclasses of `JwtException` in 0.12.x; a single `catch (JwtException e)` is sufficient.

Update `key()` return type to `SecretKey` (not `Key`):
```java
private SecretKey key() {
    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
}
```

Add imports: `import javax.crypto.SecretKey;`, `import jakarta.annotation.PostConstruct;`.
Remove import: `import io.jsonwebtoken.security.Keys;` — already present; keep it.
Remove import: `import io.jsonwebtoken.SignatureAlgorithm;`.

**Step 3 — AuthTokenFilter.java** (read before editing):
In `doFilterInternal`, `jwtUtils.getUserNameFromJwtToken(jwt)` remains the same signature — no change needed. Verify any `parseClaimsJws` direct calls have been migrated if they exist.
</action>
<acceptance_criteria>
- `application.properties` does NOT contain the string `jwtSecret=`
- `JwtUtils.java` contains `@Value("${JWT_SECRET}")`
- `JwtUtils.java` contains `@PostConstruct`
- `JwtUtils.java` contains `IllegalStateException`
- `JwtUtils.java` does NOT contain `SignatureAlgorithm`
- `JwtUtils.java` does NOT contain `parserBuilder()` (0.11.5 method — replaced by `parser()` in 0.12.x)
- `JwtUtils.java` contains `SecretKey`
- `JwtUtils.java` does NOT contain `setSubject` (replaced by `subject()` in 0.12.x)
</acceptance_criteria>
</task>

<task id="2">
<title>Upgrade jjwt from 0.11.5 to 0.12.x in pom.xml</title>
<read_first>
- `Service/superaccountant/pom.xml` — three jjwt dependencies at version 0.11.5 to be upgraded
</read_first>
<action>
In `pom.xml`, change the version of all three jjwt artifacts from `0.11.5` to `0.12.6`:

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

After editing, run `./mvnw dependency:resolve -pl Service/superaccountant` (from the repo root) to confirm the artifact resolves from Maven Central. If 0.12.6 is unavailable, fall back to 0.12.5 — both are on Maven Central.
</action>
<acceptance_criteria>
- `pom.xml` contains `<version>0.12.6</version>` (or `0.12.5`) for `jjwt-api`, `jjwt-impl`, `jjwt-jackson` — all three must be the same version
- `pom.xml` does NOT contain `<version>0.11.5</version>`
- Running `cd "Service/superaccountant" && ./mvnw compile -q` (with `JWT_SECRET` env var set to any 32+ char value) exits 0
</acceptance_criteria>
</task>

## Verification

```bash
# Must be run from Service/superaccountant/
# Set a dummy secret for compilation test
export JWT_SECRET="a-32-char-or-longer-dummy-secret-value"
cd "Service/superaccountant"

# Compile passes
./mvnw compile -q

# application.properties has no hardcoded secret
grep -c "jwtSecret=" src/main/resources/application.properties && echo "FAIL: secret still present" || echo "PASS: secret removed"

# JwtUtils has env var binding
grep "@Value.*JWT_SECRET" src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java && echo "PASS" || echo "FAIL"

# JwtUtils has PostConstruct validation
grep "@PostConstruct" src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java && echo "PASS" || echo "FAIL"

# jjwt 0.12.x in pom
grep "0.12" pom.xml | grep jjwt && echo "PASS" || echo "FAIL"

# Startup fails without JWT_SECRET (unset it)
unset JWT_SECRET
./mvnw spring-boot:run 2>&1 | grep -i "JWT_SECRET\|IllegalStateException\|startup failed" | head -5
```

## must_haves
- Application refuses to start unless `JWT_SECRET` env var is set to a value of 32+ characters
- `application.properties` contains no JWT secret value — the property `arktech.app.jwtSecret` is absent
- All three jjwt artifacts in `pom.xml` are at version 0.12.x
- `JwtUtils` compiles cleanly using jjwt 0.12.x API (no deprecated methods)

<output>
After completion, create `.planning/phases/01-security-hardening-foundation/01-01-SUMMARY.md`
</output>
