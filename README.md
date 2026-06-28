# Super Accountant

A multi-tenant accounting and business management platform built for Indian businesses. It validates Tally Masters exports, enforces GST/PAN/ledger standards, and gives teams a structured workflow for detecting and resolving accounting mismatches.

## What it does

- **Tally Masters validation** — Upload a JSON export from Tally ERP and automatically detect ledger mismatches (wrong parent group, missing GST/TDS flags, misclassified categories).
- **Organization management** — Create organizations with GSTIN, PAN, and financial year settings. Invite team members by role.
- **Role-based access** — Four roles (Owner, Accountant, Operator, CA Auditor) with different permissions across upload, resolve, and admin actions.
- **Mismatch resolution workflow** — Review flagged findings, mark them as accepted or overridden, and export the results to CSV.
- **Pre-configured masters** — Define expected ledger standards per organization; use a built-in Construction/Works Contractor template or build from scratch.

## Architecture

Full-stack monorepo with a Spring Boot API backend and a React + Vite frontend running in the same repository.

```
Super Accountant/
├── Service/superaccountant/   # Spring Boot 4 backend (Java 25)
└── Client/                    # React 19 + Vite 8 frontend (TypeScript)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for a detailed breakdown.

## Quick start

**Prerequisites:** Java 25, Node.js 18+, PostgreSQL 15+ running on port 5432.

```bash
# 1. Create the database
psql -U postgres -c "CREATE DATABASE superaccountant;"

# 2. Set required environment variable
export JWT_SECRET="<your-base64-encoded-secret-min-32-chars>"

# 3. Start the backend
cd Service/superaccountant
./mvnw spring-boot:run

# 4. Start the frontend (separate terminal)
cd Client
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). See [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) for a full walkthrough.

## Roles

| Role | API key | Default capabilities |
|------|---------|----------------------|
| Owner | `owner` | Create organizations, invite members, manage masters |
| Accountant | `accountant` | Upload files, manage masters, invite members |
| Operator | `operator` | Upload files, manage masters |
| CA Auditor | `auditor_ca` | Read-only access; can switch between client organizations |

## Tech stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 4, Java 25, Spring Security, jjwt |
| Database | PostgreSQL 15, Spring Data JPA / Hibernate |
| Frontend | React 19, TypeScript, Vite 8, TanStack Router, Zustand, Axios |
| Styling | Tailwind CSS 4 |
| Testing | Vitest + Testing Library (frontend), Maven Surefire (backend) |

## Documentation

- [ARCHITECTURE](docs/ARCHITECTURE.md) — System design, data model, security model
- [GETTING-STARTED](docs/GETTING-STARTED.md) — First-time setup and onboarding walkthrough
- [DEVELOPMENT](docs/DEVELOPMENT.md) — Development workflow, conventions, debugging
- [TESTING](docs/TESTING.md) — Test strategy and how to run tests
- [CONFIGURATION](docs/CONFIGURATION.md) — All configuration variables and their purpose
- [API](docs/API.md) — Full REST API reference
