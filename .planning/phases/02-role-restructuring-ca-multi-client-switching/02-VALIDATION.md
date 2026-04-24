---
phase: 2
slug: role-restructuring-ca-multi-client-switching
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-14
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest + Testing Library (frontend) |
| **Config file** | `Service/superaccountant/src/test/resources/` (fixtures); `Client/vite.config.ts` (Vitest) |
| **Quick run command** | `./mvnw test -pl Service/superaccountant` |
| **Full suite command** | `./mvnw test` + `cd Client && npm run test:run` |
| **Estimated runtime** | ~60 seconds (backend with Testcontainers) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw test -pl Service/superaccountant -Dtest={TaskTest}`
- **After every plan wave:** Run `./mvnw test` + `cd Client && npm run test:run`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 1 | AUTH-01 | — | ERole has exactly 4 values; CASHIER absent | unit | `./mvnw test -Dtest=ERoleTest` | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 1 | AUTH-01 | — | Signup with "cashier" returns 400 | integration | `./mvnw test -Dtest=AuthControllerIT` | ❌ W0 | ⬜ pending |
| 2-01-03 | 01 | 1 | AUTH-01 | — | DataInitializer upserts all 4 roles; no exception on populated DB | integration | `./mvnw test -Dtest=DataInitializerIT` | ❌ W0 | ⬜ pending |
| 2-02-01 | 02 | 2 | AUTH-02 | — | OWNER GET /api/v1/uploads returns 200; POST /api/v1/uploads returns 403 | integration | `./mvnw test -Dtest=UploadControllerIT` | ❌ W0 | ⬜ pending |
| 2-02-02 | 02 | 2 | AUTH-03 | — | ACCOUNTANT resolve endpoint 200; OPERATOR approve endpoint 403 | integration | `./mvnw test -Dtest=UploadControllerIT` | ❌ W0 | ⬜ pending |
| 2-02-03 | 02 | 2 | AUTH-04 | — | AUDITOR_CA POST /api/v1/uploads returns 403 | integration | `./mvnw test -Dtest=UploadControllerIT` | ❌ W0 | ⬜ pending |
| 2-02-04 | 02 | 2 | AUTH-05 | — | POST /api/organizations/{id}/select returns JWT with correct orgId claim | integration | `./mvnw test -Dtest=OrganizationControllerIT` | ❌ W0 | ⬜ pending |
| 2-03-01 | 03 | 2 | AUTH-06 | — | AUDITOR_CA sees org selector in UI; org switch updates Zustand store role | unit | `cd Client && npm run test:run -- OrganizationSelector` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `Service/superaccountant/src/test/java/.../ERoleTest.java` — enum value count assertions for AUTH-01
- [ ] `Service/superaccountant/src/test/java/.../AuthControllerIT.java` — signup/role guard integration tests
- [ ] `Service/superaccountant/src/test/java/.../UploadControllerIT.java` — role-based access control tests for AUTH-02/03/04
- [ ] `Service/superaccountant/src/test/java/.../OrganizationControllerIT.java` — org-switch JWT tests for AUTH-05
- [ ] `Service/superaccountant/src/test/java/.../DataInitializerIT.java` — upsert idempotency for AUTH-01
- [ ] `Client/src/components/OrganizationSelector.test.tsx` — Zustand role update on org switch for AUTH-06

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| CA user switches org in UI without re-login | AUTH-06 | E2E browser flow | Log in as AUDITOR_CA, open org selector, select different org, verify all API calls use new org scope |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
