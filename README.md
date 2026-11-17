# Super Accountant

A multi-tenant accounting and business management platform built for Indian businesses. It validates Tally Masters exports, enforces GST/PAN/ledger standards, and gives teams a structured workflow for detecting and resolving accounting mismatches.

## What it does

- **Tally Masters validation** — Upload a JSON export from Tally ERP and automatically detect ledger mismatches (wrong parent group, missing GST/TDS flags, misclassified categories).
- **Organization management** — Create organizations with GSTIN, PAN, and financial year settings. Invite team members by role.
- **Role-based access** — Four roles (Owner, Accountant, Operator, CA Auditor) with different permissions across upload, resolve, and admin actions.
- **Mismatch resolution workflow** — Operators propose a corrected master value for a flagged finding; an Accountant approves (applying the override to the master and unlocking the gate) or rejects it with a note. Accountants' own proposals are approved in one step. Findings can be filtered by mismatch reason and submitted or approved in bulk (including TDS section and "GST not applicable" overrides). Per-organization validation settings, such as whether expense ledgers need an HSN/SAC code, tune which findings are raised. Results export to CSV.
- **Compliance settings** — A `/settings/compliance` page (visible to all members; edits are Accountant-only) holds the filer profile, per-type responsibility start dates, GST tax-ledger mapping (IGST/CGST/SGST per output, input, portal cash/liability/credit and GST TDS), company-wide baseline confirmation, and financial-year close. A `/settings/baseline-periods` page imports filed returns for baseline periods (GSTR-1/IFF JSON; GSTR-3B PDF review or manual entry) and shows differences from the books.
- **Pre-configured masters** — Define expected ledger standards per organization; use a built-in Construction/Works Contractor template or build from scratch.

## Architecture

Full-stack monorepo with a Spring Boot API backend and a React + Vite frontend running in the same repository.

```
Super Accountant/
├── Service/superaccountant/   # Spring Boot 4 backend (Java 25)
└── Client/                    # React 19 + Vite 8 frontend (TypeScript)
```

## Quick start

**Prerequisites:** Java 25, Node.js 18+, PostgreSQL 15+ running on port 5432.

```bash
# 1. Create the database
psql -U postgres -c "CREATE DATABASE superaccountant;"

# 2. Set required environment variables
export DB_PASSWORD="<your-postgres-password>"
export JWT_SECRET="<your-base64-encoded-secret-min-32-chars>"

# 3. Start the backend
cd Service/superaccountant
./mvnw spring-boot:run

# 4. Start the frontend (separate terminal)
cd Client
npm install
npm run dev
```

The backend requires `DB_PASSWORD` and refuses to start without it. `DB_URL` and `DB_USERNAME` default to
`jdbc:postgresql://localhost:5432/superaccountant` and `postgres`; override them for a non-local database.

Open [http://localhost:5173](http://localhost:5173).

## Roles

| Role | API key | Default capabilities |
|------|---------|----------------------|
| Owner | `owner` | Create organizations, invite members |
| Accountant | `accountant` | Upload files, manage masters, invite members, approve/reject finding overrides |
| Operator | `operator` | Upload files, manage masters, submit finding overrides for review |
| CA Auditor | `auditor_ca` | Read-only access; can switch between client organizations |

## Tech stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4, Java 25, Spring Security, jjwt |
| Database | PostgreSQL 15, Spring Data JPA / Hibernate |
| Frontend | React 19, TypeScript, Vite 8, TanStack Router, Zustand, Axios |
| Styling | Tailwind CSS 4 |
| Testing | Vitest + Testing Library (frontend), Maven Surefire + Failsafe with Testcontainers (backend) |
