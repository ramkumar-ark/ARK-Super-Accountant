# Code Conventions

## Backend (Java / Spring Boot)

### Package Naming
- Feature-based package structure: `com.arktech.superaccountant.<feature>.<layer>`
- Example layers: `controllers`, `models`, `repository`, `security`, `payload/request`, `payload/response`

### Class & File Naming
- PascalCase for classes (e.g., `AuthController`, `JwtUtils`, `DataInitializer`)
- Interface suffix pattern: `*Repository` for repositories, `*Impl` for implementations
- DTO naming: `*Request` for inbound DTOs, `*Response` for outbound DTOs

### Lombok Usage
- `@Data` on entity/model classes
- `@NoArgsConstructor` on entities (required by JPA)
- `@Builder` where builder pattern is needed
- `@AllArgsConstructor` for DTOs

### Dependency Injection
- `@Autowired` field injection (not constructor injection)

### Controllers
- `@CrossOrigin(origins = "*")` on all controllers
- `ResponseEntity<?>` return type for HTTP responses
- `@RestController` + `@RequestMapping` class-level annotations

### Error Handling
- `ResponseEntity.badRequest().body(new MessageResponse(...))` pattern
- `ResponseEntity.ok(...)` for success responses

### Logging
- SLF4J (`@Slf4j` via Lombok or manual `Logger` declaration)
- Javadoc on public API methods

### Security
- JWT stateless sessions
- Role-based access via `ERole` enum

---

## Frontend (TypeScript / React)

### File & Component Naming
- `PascalCase.tsx` for component files (e.g., `HeroSection.tsx`, `NavBar.tsx`)
- `camelCase.ts` for utility/hook files

### Component Style
- Named `export function` components (not default arrow function exports)
- Functional components only (no class components)

### Styling
- Tailwind CSS only — no inline styles, no CSS modules
- Semantic HTML elements with ARIA accessibility attributes

### Imports
- `@/` path alias for project root imports

### Git Conventions
- Conventional Commits format: `feat:`, `fix:`, `docs:`, `chore:`, `refactor:`, `test:`
