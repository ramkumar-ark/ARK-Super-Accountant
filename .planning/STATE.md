# Project State

**Last updated:** 2026-04-12
**Current phase:** Phase 2
**Status:** Phase 1 Complete

## Project Reference

See: .planning/PROJECT.md
**Core value:** A CA or business owner uploads Tally JSON and immediately gets actionable GST and TDS compliance reports — without manual re-entry or spreadsheet juggling.
**Milestone:** Milestone 1 — Compliance Workflow: GST & TDS from Tally

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | Security Hardening & Foundation | Complete ✓ |
| 2 | Role Restructuring & CA Multi-Client Switching | Not Started |
| 3 | Masters TDS & GST Mapping Extension | Not Started |
| 4 | Tally JSON Day Book Parser & Analysis Engine | Not Started |
| 5 | TDS Computation & Reports | Not Started |
| 6 | Pre-Reconciliation GST Validation | Not Started |
| 7 | GSTR-2B Reconciliation | Not Started |

## Current Position

**Phase:** 2
**Plan:** —
**Progress:** 1/7 phases complete

```
[█·········] 14%
```

## Performance Metrics

- Plans completed: 5
- Plans attempted: 5
- Phases completed: 1

## Accumulated Context

### Key Decisions
- Extend brownfield Spring Boot backend — auth, org management, and masters pipeline already built
- JSON parser runs alongside existing XML parser — day book JSON is primary input for analysis
- Masters upload pattern (upload → parse → validate → findings) reused for GSTR-2B and TDS workflows
- TDS and GSTR-2B reports before AI invoice processing — compliance workflow is the primary v1 success criterion
- CA multi-client via Organization model — organization already exists as the tenant boundary

### Phase 1 Decisions (locked)
- JWT_SECRET env var — startup fails hard if unset (no fallback)
- jjwt upgraded to 0.12.6
- UserOrganization join table replaces User.organizationId — all org context comes from JWT claim
- Org switch via POST /api/organizations/{id}/select — returns new JWT
- Invite tokens: UUID, 7-day expiry, single-use, returned in API response only (no email)
- GSTIN regex: [0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}
- PAN regex: [A-Z]{5}[0-9]{4}[A-Z]{1}
- Testcontainers via @SpringBootTest + @Transactional (Spring Boot 4 removed @DataJpaTest slice)

### Open Questions (resolve before implementation)
1. Tally Prime version(s) used by CA pilot users — day book JSON schema is version-sensitive
2. Real GSTR-2B JSON export needed before Phase 7 — cannot build parser on assumed field names
3. TDS section rates for FY 2025–26 — verify 194J(a)/(b) split and 194Q threshold against Finance Act
4. Cloud SaaS vs. self-hosted — affects CORS config and DPDP Act obligations
5. ROLE_MANAGER status — new role or synonym for OWNER (affects AUTH phase planning)

### Blockers
None.

## Session Continuity

Phase 1 completed 2026-04-12. All 5 plans executed and verified.

Next action: `/gsd-plan-phase 2`
