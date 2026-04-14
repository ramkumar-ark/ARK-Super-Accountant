# Super Accountant

Accounting/business management application with role-based access control. Full-stack monorepo with a Spring Boot API backend and a React + Vite frontend.

## Tech Stack

### Backend (Service/)

- **Language:** Java 25
- **Framework:** Spring Boot 4.0.2
- **Build:** Maven (use `./mvnw` wrapper, not system Maven)
- **Database:** PostgreSQL 5432, database name `superaccountant`
- **ORM:** Spring Data JPA with Hibernate (ddl-auto: update)
- **Auth:** JWT (jjwt 0.11.5) + Spring Security, stateless sessions
- **Other:** Lombok, Jakarta Validation, Jackson, DevTools

### Frontend (Client/)

- **Language:** TypeScript ~5.9.3
- **UI Library:** React 19
- **Bundler:** Vite 8 (`@vitejs/plugin-react`)
- **Styling:** Tailwind CSS 4 (via `@tailwindcss/vite` plugin)
- **Routing:** TanStack Router (`@tanstack/react-router`)
- **Data Fetching:** TanStack React Query
- **HTTP Client:** Axios (base URL `/api`, proxied to backend via Vite dev server)
- **State Management:** Zustand
- **Validation:** Zod
- **Icons:** Lucide React
- **Testing:** Vitest + Testing Library + happy-dom
- **Linting:** ESLint 9 with `typescript-eslint`, `react-hooks`, `react-refresh`

## Project Structure

```
Service/superaccountant/          # Spring Boot backend
  src/main/java/com/arktech/superaccountant/
    SuperaccountantApplication.java        # Entry point
    login/
      controllers/AuthController.java      # /api/auth/signin, /api/auth/signup
      models/                              # User, Role, ERole (JPA entities)
      payload/request/                     # LoginRequest, SignupRequest (DTOs)
      payload/response/                    # JwtResponse, MessageResponse (DTOs)
      repository/                          # UserRepository, RoleRepository
      security/
        WebSecurityConfig.java             # Security filter chain config
        jwt/                               # JwtUtils, AuthTokenFilter, AuthEntryPointJwt
        services/                          # UserDetailsImpl, UserDetailsServiceImpl
      config/DataInitializer.java          # Seed data
    gst/                                   # GST module
  src/main/resources/
    application.properties                 # DB + JWT config

Client/                             # React + Vite frontend
  index.html                               # SPA entry HTML
  vite.config.ts                           # Vite config (plugins, proxy, test)
  tsconfig.json                            # TypeScript project references
  eslint.config.js                         # ESLint flat config
  package.json                             # Dependencies & scripts
  public/                                  # Static assets (favicon, etc.)
  .vite/                                   # Vite dep pre-bundling cache (TRACKED)
  src/
    main.tsx                               # App entry — router setup, renders <App/>
    index.css                              # Global styles (Tailwind)
    components/                            # Reusable UI components
      landing/                             # Landing page sections
      OrganizationSelector.tsx
      InviteSignupBanner.tsx
      InviteTokenDisplay.tsx
    pages/                                 # Route-level page components
      LandingPage.tsx                      # / — public landing
      LoginPage.tsx                        # /login
      SignupPage.tsx                       # /signup
      DashboardPage.tsx                    # /dashboard (auth-guarded)
      OrganizationSetupPage.tsx            # /organization/setup (auth-guarded)
    lib/
      api.ts                               # Axios instance & API helpers
    store/
      authStore.ts                         # Zustand auth state (token, user, isAuthenticated)
    test/
      setup.ts                             # Vitest test setup
```

## Roles

Four user roles defined in `ERole`: `ROLE_CASHIER`, `ROLE_ACCOUNTANT`, `ROLE_DATA_ENTRY_OPERATOR`, `ROLE_OWNER`. Default role on signup is `ROLE_CASHIER`.

## API Endpoints

- `POST /api/auth/signin` - Login, returns JWT
- `POST /api/auth/signup` - Register new user

Public endpoints: `/api/auth/**`, `/api/test/**`, `/error`. All others require authentication.

## Build & Run

### Backend

```bash
cd Service/superaccountant
./mvnw spring-boot:run        # Run the application
./mvnw test                   # Run tests
./mvnw clean package          # Build JAR
```

### Frontend

```bash
cd Client
npm install                   # Install dependencies
npm run dev                   # Start Vite dev server (port 5173, proxies /api → localhost:8080)
npm run build                 # Type-check + production build → dist/
npm run preview               # Preview production build
npm run lint                  # ESLint
npm run test                  # Vitest (watch mode)
npm run test:run              # Vitest (single run)
```

## Conventions

### Backend

- Package base: `com.arktech.superaccountant`
- Feature-based package organization (e.g., `login/` contains controllers, models, repos, security)
- Uses `@Autowired` field injection
- Lombok `@Data` / `@NoArgsConstructor` on entities
- Request/response DTOs in `payload/request/` and `payload/response/`
- `@CrossOrigin(origins = "*")` on controllers
- Each user has a single `Role` (ManyToOne relationship)

### Frontend

- Path alias: `@` → `src/` (configured in `vite.config.ts` and `tsconfig.app.json`)
- TanStack Router with manual route definitions in `main.tsx` (not file-based)
- Auth guards via `beforeLoad` + `redirect` on protected routes
- Zustand store for auth state; token persisted in store
- Axios instance in `lib/api.ts` for all API calls
- Tailwind CSS 4 for styling (no `tailwind.config` file — uses Vite plugin)
- Vitest with happy-dom environment + Testing Library for component tests
- ESLint flat config (ESLint 9+) in `eslint.config.js`

## Database

PostgreSQL must be running locally on port 5432. Schema is auto-managed by Hibernate (`ddl-auto: update`). Credentials are in `application.properties`.

## .gitignore Rules

> **CRITICAL: Do NOT remove `.vite/*` or `.vscode/*` entries from any `.gitignore` file (root or `Client/.gitignore`).** These directories contain Vite's dependency pre-bundling cache and editor-specific configuration that should not be tracked in version control. They must stay ignored.

## gstack

Use the `/browse` skill from gstack for all web browsing. Never use `mcp__claude-in-chrome__*` tools directly.
