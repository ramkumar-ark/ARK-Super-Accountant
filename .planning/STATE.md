# Project State

**Last updated:** 2026-05-07
**Current phase:** Phase 3
**Status:** Phase 3 Complete — All 7 plans done (incl. 2 gap-closure plans)

## Project Reference

See: .planning/PROJECT.md
**Core value:** A CA or business owner uploads Tally JSON and immediately gets actionable GST and TDS compliance reports — without manual re-entry or spreadsheet juggling.
**Milestone:** Milestone 1 — Compliance Workflow: GST & TDS from Tally

## Phases

| # | Phase | Status |
|---|-------|--------|
| 1 | Security Hardening & Foundation | Complete ✓ |
| 2 | Role Restructuring & CA Multi-Client Switching | Not Started |
| 3 | Masters TDS & GST Mapping Extension | Complete ✓ (7/7 incl. gap closure) |
| 4 | Tally JSON Day Book Parser & Analysis Engine | Not Started |
| 5 | TDS Computation & Reports | Not Started |
| 6 | Pre-Reconciliation GST Validation | Not Started |
| 7 | GSTR-2B Reconciliation | Not Started |

## Current Position

**Phase:** 3 (complete)
**Plan:** 03-07 (complete)
**Progress:** 3/7 phases complete

```
[███·······] 43%
```

## Performance Metrics

- Plans completed: 12
- Plans attempted: 12
- Phases completed: 3

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
Phase 3 planned 2026-05-06. Plans 03-01 through 03-05 approved by plan checker (revision 2/3).
Phase 3 execution started 2026-05-06. Wave 1 complete — Plan 03-01 (backend model layer) done and merged.
Wave 2 complete — Plans 03-02 (validation rules TDD) and 03-03 (template seeding) done and merged.
Wave 3 complete — Plan 03-04 (frontend: AppShell, MastersPage, LedgerMappingPanel, two-step org setup, /masters route) done and merged.
Wave 4 complete — Plan 03-05 (integration tests: PreconfiguredMastersControllerIT, ValidationOrchestratorIT, fixture) done and merged.

Phase 3 complete 2026-05-07. Next: Phase 4 — Tally JSON Day Book Parser & Analysis Engine.
Gap closure 2026-05-07: Plans 03-06 (DataInitializer backfillFindingSeverities, JPQL severity migration) and 03-07 (ROLE_OPERATOR route guard fix) executed and verified. Both gaps from VERIFICATION.md closed.
