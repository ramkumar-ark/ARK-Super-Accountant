# Codebase Structure

**Analysis Date:** 2026-04-09

## Directory Layout

```
Super Accountant/                  # Monorepo root
├── Client/                        # React/TypeScript frontend (Vite)
│   ├── public/                    # Static assets served as-is
│   ├── src/
│   │   ├── assets/                # Images and SVGs (hero.png, react.svg, vite.svg)
│   │   ├── components/
│   │   │   └── landing/           # Landing page section components (co-located tests)
│   │   ├── pages/                 # Route-level page components
│   │   ├── test/                  # Global test setup only
│   │   ├── index.css              # Global CSS (Tailwind v4 entry)
│   │   └── main.tsx               # App entry point, router definition
│   ├── index.html                 # Vite HTML template
│   ├── vite.config.ts             # Vite + Vitest config, path alias @/
│   ├── tsconfig.json              # TypeScript root config
│   ├── tsconfig.app.json          # App TypeScript config
│   └── package.json               # Frontend dependencies
│
├── Service/superaccountant/       # Spring Boot backend
│   ├── src/main/java/com/arktech/superaccountant/
│   │   ├── SuperaccountantApplication.java   # Spring Boot entry point
│   │   ├── login/                 # Auth feature (JWT, users, roles)
│   │   │   ├── config/            # DataInitializer (seed data)
│   │   │   ├── controllers/       # AuthController
│   │   │   ├── models/            # User, Role, ERole
│   │   │   ├── payload/request/   # LoginRequest, SignupRequest DTOs
│   │   │   ├── payload/response/  # JwtResponse, MessageResponse DTOs
│   │   │   ├── repository/        # UserRepository, RoleRepository
│   │   │   └── security/          # WebSecurityConfig, JWT filter chain
│   │   │       ├── jwt/           # JwtUtils, AuthTokenFilter, AuthEntryPointJwt
│   │   │       └── services/      # UserDetailsImpl, UserDetailsServiceImpl
│   │   ├── masters/               # Tally masters upload & validation feature
│   │   │   ├── classifier/        # LedgerCategoryClassifier, ParsedLedger
│   │   │   ├── controllers/       # OrganizationController, PreconfiguredMastersController, UploadController
│   │   │   ├── models/            # Organization, UploadJob, ValidationFinding, PreconfiguredMaster, enums
│   │   │   ├── payload/request/   # Request DTOs (BulkImport, CreateOrg, Onboard, Resolve, etc.)
│   │   │   ├── payload/response/  # Response DTOs (BulkImport, Finding, UploadJob, Resolve, etc.)
│   │   │   ├── repository/        # JPA repositories for masters domain entities
│   │   │   ├── rules/             # ValidationRule, MismatchDetectionRule, ValidationContext
│   │   │   └── services/          # ValidationOrchestrator, MastersParseResult
│   │   ├── tally/                 # Tally XML import & parsing feature
│   │   │   ├── controllers/       # TallyImportController
│   │   │   ├── models/            # Tally domain models (Voucher, LedgerEntry, GstEntry, etc.)
│   │   │   ├── payload/response/  # TallyImportResponse DTO
│   │   │   └── services/          # TallyParserService
│   │   └── gst/                   # GST validation feature
│   │       ├── controllers/       # GstValidationController
│   │       ├── models/            # GstValidationResult, GstValidationError, GstErrorType
│   │       ├── payload/response/  # GstValidationResponse DTO
│   │       └── services/          # GstValidationService
│   ├── src/main/resources/
│   │   ├── application.properties # DB, JWT, and server configuration
│   │   ├── static/                # Static files served by Spring (empty)
│   │   └── templates/             # Thymeleaf templates (empty)
│   ├── src/test/java/com/arktech/superaccountant/
│   │   ├── SuperaccountantApplicationTests.java
│   │   ├── gst/                   # GstValidationServiceTest
│   │   ├── masters/
│   │   │   ├── classifier/        # LedgerCategoryClassifierTest
│   │   │   └── rules/             # MismatchDetectionRuleTest
│   │   └── tally/                 # TallyMastersParserTest, TallyParserServiceTest
│   └── pom.xml                    # Maven build descriptor
│
├── design-system/                 # Design system documentation
│   ├── MASTER.md                  # Design system master reference
│   └── pages/                     # Per-page design specs
├── docs/superpowers/              # Project planning documents
│   ├── plans/                     # Phase implementation plans
│   └── specs/                     # Feature specifications
├── .planning/codebase/            # GSD codebase analysis documents
├── .claude/                       # Claude Code skills and settings
├── CLAUDE.md                      # Project instructions for Claude
└── .gitignore
```

## Directory Purposes

**`Client/src/components/landing/`:**
- Purpose: Presentational section components for the marketing landing page
- Contains: One `.tsx` file and one `.test.tsx` file per section component (co-located)
- Key files: `Navbar.tsx`, `HeroSection.tsx`, `ProblemSection.tsx`, `FeaturesSection.tsx`, `AudienceSection.tsx`, `HowItWorksSection.tsx`, `CtaStripSection.tsx`, `LandingFooter.tsx`

**`Client/src/pages/`:**
- Purpose: Route-level page components that compose section components
- Key files: `LandingPage.tsx`, `LandingPage.test.tsx`

**`Client/src/test/`:**
- Purpose: Global test configuration only — not test cases
- Key files: `setup.ts` (Vitest global setup)

**`Service/.../login/`:**
- Purpose: Complete auth feature — JWT authentication, user management, Spring Security config
- Key files: `controllers/AuthController.java`, `security/WebSecurityConfig.java`, `security/jwt/JwtUtils.java`, `config/DataInitializer.java`

**`Service/.../masters/`:**
- Purpose: Tally masters upload, validation orchestration, organization management
- Key files: `services/ValidationOrchestrator.java`, `classifier/LedgerCategoryClassifier.java`, `rules/ValidationRule.java`, `controllers/UploadController.java`

**`Service/.../tally/`:**
- Purpose: Tally XML parsing — converts Tally export format into domain objects
- Key files: `services/TallyParserService.java`, `controllers/TallyImportController.java`

**`Service/.../gst/`:**
- Purpose: GST compliance validation — validates parsed Tally data against GST rules
- Key files: `services/GstValidationService.java`, `controllers/GstValidationController.java`

## Key File Locations

**Entry Points:**
- `Client/src/main.tsx` — Frontend app bootstrap, TanStack Router setup
- `Service/.../SuperaccountantApplication.java` — Spring Boot main class

**Configuration:**
- `Client/vite.config.ts` — Vite build, Vitest test runner, `@/` path alias, `/api` proxy to `:8080`
- `Client/tsconfig.app.json` — TypeScript compiler config
- `Service/.../resources/application.properties` — DB connection, JWT secret, server port
- `Client/eslint.config.js` — ESLint rules

**Core Logic:**
- `masters/services/ValidationOrchestrator.java` — Central validation pipeline for uploaded masters
- `masters/classifier/LedgerCategoryClassifier.java` — Classifies ledger entries by category
- `masters/rules/ValidationRule.java` — Base interface for validation rules
- `masters/rules/MismatchDetectionRule.java` — Concrete rule: detects category mismatches
- `tally/services/TallyParserService.java` — Parses Tally XML export into Java models
- `gst/services/GstValidationService.java` — Validates GST data extracted from Tally

**Security:**
- `login/security/WebSecurityConfig.java` — Filter chain, public vs. protected routes
- `login/security/jwt/AuthTokenFilter.java` — Per-request JWT extraction and validation

## Naming Conventions

**Frontend Files:**
- React components: `PascalCase.tsx`
- Test files: `PascalCase.test.tsx` co-located next to the component file
- Config files: `camelCase` or `kebab-case` per tool convention

**Backend Files:**
- Controllers: `[Feature]Controller.java`
- Services: `[Feature]Service.java`
- Repositories: `[Entity]Repository.java`
- Request DTOs: `[Action][Entity]Request.java`
- Response DTOs: `[Entity]Response.java`
- Models/Enums: `PascalCase.java`

**Backend Packages:**
- Feature-based: `com.arktech.superaccountant.[feature].[layer]`
- Layers: `controllers`, `models`, `payload/request`, `payload/response`, `repository`, `services`, `rules`, `classifier`

## Where to Add New Code

**New Frontend Page:**
- `Client/src/pages/[PageName].tsx` + co-located test
- Register route in `Client/src/main.tsx`

**New Frontend Component:**
- `Client/src/components/[feature]/[ComponentName].tsx` + co-located test

**New Backend Feature:**
- Create `Service/...//[featurename]/` with sub-packages
- Register public paths in `login/security/WebSecurityConfig.java`
- Add tests in `Service/.../test/java/.../[featurename]/`

**New Validation Rule:**
- Implement `ValidationRule` interface in `masters/rules/`
- Register with `masters/services/ValidationOrchestrator.java`

**Shared Utilities:**
- No shared utility package exists yet — add a `common/` package under `com.arktech.superaccountant`

---

*Structure analysis: 2026-04-09*
