# Phase 2 Permission Matrix

Source of truth for @PreAuthorize expressions in Super Accountant.
Every backend guard must reference this table. Do not diverge without updating this document in the same commit.

## Roles

- ROLE_OWNER — administrative; cannot do data entry or resolve mismatches
- ROLE_ACCOUNTANT — operational; can do everything including approve AI entries (future)
- ROLE_OPERATOR — operational; reconciliation + resolve, cannot approve AI entries
- ROLE_AUDITOR_CA — read-only cross-org access (CA use case)

## Endpoint Matrix

| Endpoint | Method | OWNER | ACCOUNTANT | OPERATOR | AUDITOR_CA | Guard Expression |
|----------|--------|-------|------------|----------|------------|------------------|
| /api/organizations | POST | YES | YES | NO | NO | `hasRole('OWNER') or hasRole('ACCOUNTANT')` |
| /api/organizations/{id} | GET | YES | YES | YES | YES | `isAuthenticated()` |
| /api/organizations/{id}/invites | POST | YES | YES | NO | NO | `hasRole('OWNER') or hasRole('ACCOUNTANT')` |
| /api/organizations/me/list | GET | YES | YES | YES | YES | `isAuthenticated()` |
| /api/organizations/{id}/select | POST | YES | YES | YES | YES | `isAuthenticated()` |
| /api/v1/uploads | POST | NO | YES | YES | NO | `hasRole('ACCOUNTANT') or hasRole('OPERATOR')` |
| /api/v1/uploads | GET | YES | YES | YES | YES | `isAuthenticated()` |
| /api/v1/uploads/{id}/mismatches | GET | YES | YES | YES | YES | `isAuthenticated()` |
| /api/v1/uploads/{id}/mismatches/export | GET | YES | YES | YES | YES | `isAuthenticated()` |
| /api/v1/uploads/{jobId}/mismatches/{findingId}/resolve | PATCH | NO | YES | YES | NO | `hasRole('ACCOUNTANT') or hasRole('OPERATOR')` |
| /api/v1/validation-rules | GET | YES | YES | YES | YES | `isAuthenticated()` |
| /api/v1/preconfigured-masters | GET | YES | YES | YES | YES | `isAuthenticated()` |
| /api/v1/preconfigured-masters | POST | NO | YES | YES | NO | `hasRole('ACCOUNTANT') or hasRole('OPERATOR')` |
| /api/v1/preconfigured-masters/{id} | PUT | NO | YES | YES | NO | `hasRole('ACCOUNTANT') or hasRole('OPERATOR')` |
| /api/v1/preconfigured-masters/{id} | DELETE | YES | YES | NO | NO | `hasRole('OWNER') or hasRole('ACCOUNTANT')` |
| /api/v1/preconfigured-masters/bulk | POST | NO | YES | YES | NO | `hasRole('ACCOUNTANT') or hasRole('OPERATOR')` |
| /api/v1/preconfigured-masters/onboard | POST | YES | YES | NO | NO | `hasRole('OWNER') or hasRole('ACCOUNTANT')` |
| /api/gst/validate | POST | NO | YES | YES | NO | `hasRole('ACCOUNTANT') or hasRole('OPERATOR')` |
| /api/tally/import | POST | NO | YES | YES | NO | `hasRole('ACCOUNTANT') or hasRole('OPERATOR')` |

## Derivation Rules

- Read (GET, list, export): `isAuthenticated()` — all four roles can read once authenticated. Guarding this tighter (enumerating all 4 roles) is equivalent to `isAuthenticated()` since every authenticated user has exactly one of the 4 roles.
- Data entry (upload, create master, bulk, validate, import): `hasRole('ACCOUNTANT') or hasRole('OPERATOR')`
- Resolve mismatches: `hasRole('ACCOUNTANT') or hasRole('OPERATOR')`
- Admin (invite, create org, delete master, onboard): `hasRole('OWNER') or hasRole('ACCOUNTANT')`
- AI approve endpoints (future, not in Phase 2): `hasRole('ACCOUNTANT')` only

## Permission Reduction from Phase 1

OWNER previously allowed on `POST /api/v1/uploads`, resolve, bulk, create/update master, `/api/gst/validate`, `/api/tally/import`. Under the new matrix, OWNER is removed from all data-entry guards. Existing seed/demo OWNER accounts must delegate data entry to an ACCOUNTANT or OPERATOR.