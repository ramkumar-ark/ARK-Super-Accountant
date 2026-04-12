# Technology Stack

**Analysis Date:** 2026-04-09

---

## Languages

**Primary:**
- Java 25 — Spring Boot backend (`Service/superaccountant/`)
- TypeScript 5.9.3 — React frontend (`Client/`)

**Secondary:**
- CSS (via Tailwind) — styling in `Client/src/index.css` and component files

---

## Runtime

**Backend:**
- JVM (Java 25)
- Spring Boot embedded server (Tomcat via `spring-boot-starter-webmvc`)

**Frontend:**
- Node.js (version unspecified, no `.nvmrc`)
- Browser target: ES2023, DOM

**Package Manager:**
- Backend: Maven via `./mvnw` wrapper (`Service/superaccountant/.mvn/wrapper/`)
- Frontend: npm (`Client/package-lock.json` present)

---

## Frameworks

**Backend Core:**
- Spring Boot 4.0.2 (parent POM: `org.springframework.boot:spring-boot-starter-parent:4.0.2`)
- Spring Web MVC (`spring-boot-starter-webmvc`) — REST controllers
- Spring Security (`spring-boot-starter-security`) — auth filter chain, JWT
- Spring Data JPA (`spring-boot-starter-data-jpa`) — ORM repositories
- Spring Validation (`spring-boot-starter-validation`) — Jakarta Bean Validation on DTOs

**Frontend Core:**
- React 19.2.4 — UI rendering
- TanStack Router 1.168.10 — client-side routing (`@tanstack/react-router`)
- TanStack Query 5.96.1 — async data fetching and caching (`@tanstack/react-query`)
- Tailwind CSS 4.2.2 — utility-first CSS (via `@tailwindcss/vite` Vite plugin)

**Build / Dev:**
- Vite 8.0.1 — frontend dev server and bundler
- `@vitejs/plugin-react` 6.0.1 — React fast refresh
- Spring Boot Maven Plugin — backend packaging and `spring-boot:run`
- Maven Compiler Plugin — Lombok annotation processing

---

## Key Dependencies

### Backend (production)

| Artifact | Version | Purpose |
|---|---|---|
| `spring-boot-starter-webmvc` | 4.0.2 (managed) | REST API |
| `spring-boot-starter-security` | 4.0.2 (managed) | Security filter chain |
| `spring-boot-starter-data-jpa` | 4.0.2 (managed) | JPA/Hibernate ORM |
| `spring-boot-starter-validation` | 4.0.2 (managed) | Bean Validation (Jakarta) |
| `org.postgresql:postgresql` | managed (runtime) | PostgreSQL JDBC driver |
| `io.jsonwebtoken:jjwt-api` | 0.11.5 | JWT creation/parsing API |
| `io.jsonwebtoken:jjwt-impl` | 0.11.5 (runtime) | JWT implementation |
| `io.jsonwebtoken:jjwt-jackson` | 0.11.5 (runtime) | JWT Jackson serializer |
| `com.fasterxml.jackson.core:jackson-databind` | managed | JSON serialization |
| `org.projectlombok:lombok` | managed (optional) | Code generation (`@Data`, `@Builder`, etc.) |

### Backend (dev/runtime only)

| Artifact | Scope | Purpose |
|---|---|---|
| `spring-boot-devtools` | runtime, optional | Hot reload in dev |

### Backend (test)

| Artifact | Purpose |
|---|---|
| `spring-boot-starter-data-rest-test` | REST layer testing |
| `spring-boot-starter-security-test` | Security test utilities |
| `spring-boot-starter-webmvc-test` | MockMvc test support |

### Frontend (production dependencies)

| Package | Version | Purpose |
|---|---|---|
| `react` | 19.2.4 | UI framework |
| `react-dom` | 19.2.4 | DOM renderer |
| `@tanstack/react-router` | 1.168.10 | Client-side routing |
| `@tanstack/react-query` | 5.96.1 | Server state management |
| `@tanstack/router-devtools` | 1.166.11 | Router devtools |
| `axios` | 1.14.0 | HTTP client for API calls |
| `tailwindcss` | 4.2.2 | Utility CSS framework |
| `@tailwindcss/vite` | 4.2.2 | Tailwind Vite integration |
| `lucide-react` | 1.7.0 | Icon set |
| `zod` | 4.3.6 | Schema validation |
| `zustand` | 5.0.12 | Client state management |

### Frontend (devDependencies)

| Package | Version | Purpose |
|---|---|---|
| `vite` | 8.0.1 | Build tool and dev server |
| `@vitejs/plugin-react` | 6.0.1 | React Vite plugin |
| `typescript` | 5.9.3 | Type checking |
| `vitest` | 4.1.2 | Test runner |
| `@testing-library/react` | 16.3.2 | React component testing |
| `@testing-library/jest-dom` | 6.9.1 | DOM matchers |
| `happy-dom` | 20.8.9 | DOM environment for tests |
| `eslint` | 9.39.4 | Linting |
| `typescript-eslint` | 8.57.0 | TypeScript ESLint rules |
| `eslint-plugin-react-hooks` | 7.0.1 | React hooks lint rules |
| `eslint-plugin-react-refresh` | 0.5.2 | Fast refresh lint rules |
| `@types/react` | 19.2.14 | React type definitions |
| `@types/react-dom` | 19.2.3 | React DOM type definitions |
| `@types/node` | 24.12.0 | Node type definitions |
| `globals` | 17.4.0 | ESLint global definitions |

---

## Configuration

**Backend:**
- Config file: `Service/superaccountant/src/main/resources/application.properties`
- Hibernate DDL: `ddl-auto=update` (schema auto-managed, no migrations)
- File upload: max 50MB per file and request

**Frontend:**
- Build config: `Client/vite.config.ts`
- TypeScript config: `Client/tsconfig.json` + `Client/tsconfig.app.json` + `Client/tsconfig.node.json`
- Path alias: `@/` maps to `Client/src/`
- Dev proxy: `/api` requests forwarded to `http://localhost:8080`
- Test environment: `happy-dom` via Vitest
- Test setup file: `Client/src/test/setup.ts`
- TypeScript strict mode enabled (`strict: true`, `noUnusedLocals`, `noUnusedParameters`)
- Compile target: ES2023

---

## Build Commands

**Backend:**
```bash
cd Service/superaccountant
./mvnw spring-boot:run      # Run application
./mvnw test                 # Run tests
./mvnw clean package        # Build JAR
```

**Frontend:**
```bash
cd Client
npm run dev                 # Dev server (Vite)
npm run build               # Production build (tsc + vite)
npm run test                # Vitest watch mode
npm run test:run            # Vitest single run
npm run lint                # ESLint
npm run preview             # Preview production build
```

---

## Platform Requirements

**Development:**
- Java 25 (backend)
- Node.js with npm (frontend)
- PostgreSQL 5432 running locally, database `superaccountant`

**Production:**
- Spring Boot fat JAR (embedded Tomcat)
- Static frontend build output served separately or via Spring's static resources

---

*Stack analysis: 2026-04-09*
