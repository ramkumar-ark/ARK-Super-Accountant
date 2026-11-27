# Super Accountant

A multi-tenant accounting and business management platform built for Indian businesses. It validates Tally Masters exports, enforces GST/PAN/ledger standards, and gives teams a structured workflow for detecting and resolving accounting mismatches.

## What it does

- **Tally Masters validation** — Upload a JSON export from Tally ERP and automatically detect ledger mismatches (wrong parent group, missing GST/TDS flags, misclassified categories).
- **Organization management** — Create organizations with GSTIN, PAN, and financial year settings. Invite team members by role.
- **Role-based access** — Four roles (Owner, Accountant, Operator, CA Auditor) with different permissions across upload, resolve, and admin actions.
- **Mismatch resolution workflow** — Operators propose a corrected master value for a flagged finding; an Accountant approves (applying the override to the master and unlocking the gate) or rejects it with a note. Accountants' own proposals are approved in one step. Findings can be filtered by Tally top-level group and, for mismatch findings, by mismatch reason, and can be submitted or approved in bulk (including TDS section and "GST not applicable" overrides). Per-organization validation settings, such as whether expense ledgers need an HSN/SAC code, tune which findings are raised. Results export to CSV.
- **Tally PAN write-back** — Accepted PAN corrections (including an explicit "No PAN" choice that clears the PAN) become change items on the Masters page. Accountants download a Tally JSON import file and import it manually in TallyPrime with Alt+O; the app never pushes to Tally. Ledgers missing from the latest masters upload are blocked, and an upload older than 24 hours needs a ticked confirmation. See `docs/adr/0007-tally-writeback-change-items.md`.
- **Discard started workflows** — An Owner or Accountant can discard a workflow still at the Started stage that has no uploaded data and no findings; later stages or existing data refuse the discard. Discarded workflows drop out of lists and no longer lock the responsibility start date for their type.
- **Compliance settings** — A `/settings/compliance` page (visible to all members; edits are Accountant-only) holds the filer profile (including HSN summary digits, 4 or 6), per-type responsibility start dates, GST tax-ledger mapping (IGST/CGST/SGST per output, input, portal cash/liability/credit and GST TDS), company-wide baseline confirmation, and financial-year close. A `/settings/baseline-periods` page imports filed returns for baseline periods (GSTR-1/IFF JSON; GSTR-3B PDF review or manual entry) and shows differences from the books.
- **GSTR-1 Prepare** — In a GSTR-1 workflow's Prepare stage, the accountant reviews the return tables built from the day book (B2B, B2CL, B2CS, exports, nil/exempt, credit/debit notes, advances, HSN, documents issued) and fills in attributes Tally does not carry (place of supply, HSN, export type and similar) per voucher or as a per-ledger default. Fill-ins are stored with the workflow, never written to the day book, and the prepared return is validated (errors block Finalize; warnings are advisory) and frozen at Finalize. A validation panel shows the results, and the accountant can download the portal GSTR-1 JSON (errors block the download) to upload manually; each download is logged with its hash. After uploading to the portal, the accountant records the upload (the uploaded file hash and an optional note) and the filing channel used. An IFF workflow prepares its own return with only B2B, B2BA, CDNR and CDNRA (the ₹50 lakh monthly cap warning applies to IFF only); the quarterly GSTR-1 leaves out documents already reported in a finalized IFF, and later corrections to them go into its amendment tables. See `GLOSSARY.md` and `docs/adr/`.
- **TDS applicability** — Registration captures the company's TAN and entity type. Companies, firms, trusts and similar entities always deduct TDS; individuals/HUFs depend on the preceding year's turnover, so for a non-deductor year the TDS findings, workflows and ledger mapping are hidden, and PAN-only obligations (194M, 194-IB) show as advisories. If the books exceed the limit, or a financial year closes, the app prompts the user to confirm TDS applies from 1 April. See `docs/adr/0008-tds-applicability-per-financial-year.md`.
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

### App administrator

Statutory configuration (TDS sections and GST parameters, served under
`/api/statutory/**` and the `/settings/statutory` screen) is global: it applies to every
company, so no company role can change it. The capability is the `users.app_admin` flag,
which is deliberately not settable through signup, invites or any API. Grant or revoke it
directly on the app database (takes effect on the user's next request):

```sql
UPDATE users SET app_admin = true  WHERE username = '<name>';  -- grant
UPDATE users SET app_admin = false WHERE username = '<name>';  -- revoke
```

## Tech stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4, Java 25, Spring Security, jjwt |
| Database | PostgreSQL 15, Spring Data JPA / Hibernate |
| Frontend | React 19, TypeScript, Vite 8, TanStack Router, Zustand, Axios |
| Styling | Tailwind CSS 4 |
| Testing | Vitest + Testing Library (frontend), Maven Surefire + Failsafe with Testcontainers (backend) |
