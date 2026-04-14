---
phase: 1
plan: 1
subsystem: backend-security
tags: [jwt, security, env-var, jjwt-upgrade]
dependency_graph:
  requires: []
  provides: [SEC-01]
  affects: [JwtUtils, application.properties, pom.xml]
tech_stack:
  added: [jjwt 0.12.6]
  patterns: [env-var secret binding, @PostConstruct startup validation]
key_files:
  created: []
  modified:
    - Service/superaccountant/src/main/resources/application.properties
    - Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java
    - Service/superaccountant/pom.xml
decisions:
  - "JWT_SECRET env var with 32-char minimum enforced at startup via @PostConstruct IllegalStateException"
  - "jjwt upgraded to 0.12.6; single JwtException catch replaces individual 0.11.5 exception types"
metrics:
  duration_minutes: 25
  completed: "2026-04-12T13:30:00Z"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 3
---

# Phase 1 Plan 1: JWT Secret — Environment Variable + jjwt 0.12.x Upgrade Summary

**One-liner:** JWT signing secret moved from hardcoded application.properties to mandatory JWT_SECRET env var with @PostConstruct startup validation and jjwt upgraded from 0.11.5 to 0.12.6.

## What Was Built

The JWT secret was hardcoded in source control (`application.properties` line 11). This plan removes it entirely and wires the secret from a `JWT_SECRET` environment variable. The application now refuses to start if `JWT_SECRET` is absent or shorter than 32 characters. jjwt was simultaneously upgraded from 0.11.5 to 0.12.6, updating all call sites to the new fluent API.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 2 | Upgrade jjwt from 0.11.5 to 0.12.6 in pom.xml | b9b9f5e |
| 1 | Remove hardcoded JWT secret; bind JWT_SECRET env var; migrate JwtUtils to 0.12.x API | 22764e4 |

## Key Changes

### application.properties
- Removed: `arktech.app.jwtSecret=superAccountantSecretKeyReferenceForJwtTokenGenerationPurposeOnly`
- Added comment noting JWT_SECRET must be set as env var (min 32 chars)
- `arktech.app.jwtExpirationMs=86400000` unchanged

### JwtUtils.java
- `@Value("${arktech.app.jwtSecret}")` replaced with `@Value("${JWT_SECRET}")`
- Added `@PostConstruct validateJwtSecret()`: throws `IllegalStateException` if JWT_SECRET is null, blank, or < 32 chars
- `generateJwtToken()`: `.setSubject()` → `.subject()`, `.setIssuedAt()` → `.issuedAt()`, `.setExpiration()` → `.expiration()`, removed `SignatureAlgorithm.HS256` parameter
- `getUserNameFromJwtToken()`: `parserBuilder().setSigningKey().parseClaimsJws().getBody()` → `parser().verifyWith().parseSignedClaims().getPayload()`
- `validateJwtToken()`: replaced individual exception catches (MalformedJwtException, ExpiredJwtException, UnsupportedJwtException) with single `catch (JwtException e)`
- `key()` return type changed from `Key` to `SecretKey`
- Added imports: `jakarta.annotation.PostConstruct`, `javax.crypto.SecretKey`
- Removed import: `io.jsonwebtoken.SignatureAlgorithm`

### pom.xml
- All three jjwt artifacts (jjwt-api, jjwt-impl, jjwt-jackson) upgraded from 0.11.5 to 0.12.6

## Decisions Made

1. **@PostConstruct over EnvironmentPostProcessor:** Simpler approach using @PostConstruct on JwtUtils. Spring Boot fails fast before accepting any HTTP traffic if the bean fails initialization.
2. **Single JwtException catch:** jjwt 0.12.x consolidates the exception hierarchy under JwtException; individual catches were replaced with one catch that covers all JWT parsing failures.

## Verification Results

- `./mvnw compile -q` with JWT_SECRET set: PASS
- `application.properties` contains no `jwtSecret=`: PASS
- `@Value("${JWT_SECRET}")` binding present: PASS
- `@PostConstruct` present: PASS
- `IllegalStateException` present: PASS
- `SignatureAlgorithm` removed: PASS
- `parserBuilder()` removed: PASS
- `SecretKey` type used: PASS
- `setSubject` removed: PASS
- jjwt-api/jjwt-impl/jjwt-jackson all at 0.12.6: PASS

## Deviations from Plan

### Worktree Initialization Issue (non-functional)

During worktree setup, `git reset --soft` to rebase onto the correct base commit left staged deletions of planning files (.planning/ROADMAP.md and all phase 1 PLAN files). These were accidentally committed as deletions in the first commit (b9b9f5e) and then restored in a follow-up commit (c427c3e). This does not affect the functional outcome of the plan.

Otherwise, plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — this plan exclusively removes a threat (T-01-01: JWT secret in source control) and does not introduce new network endpoints, auth paths, or schema changes.

## Self-Check: PASSED

- `Service/superaccountant/pom.xml` contains `0.12.6` for all three jjwt artifacts: FOUND
- `Service/superaccountant/src/main/resources/application.properties` does not contain `jwtSecret=`: CONFIRMED
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/security/jwt/JwtUtils.java` contains `@PostConstruct`, `JWT_SECRET`, `IllegalStateException`, `SecretKey`: CONFIRMED
- Commits b9b9f5e and 22764e4 exist in git log: CONFIRMED
