# Super Accountant

Accounting/business management application with role-based access control.

## Tech Stack

- **Language:** Java 25
- **Framework:** Spring Boot 4.0.2
- **Build:** Maven (use `./mvnw` wrapper, not system Maven)
- **Database:** PostgreSQL 5432, database name `superaccountant`
- **ORM:** Spring Data JPA with Hibernate (ddl-auto: update)
- **Auth:** JWT (jjwt 0.11.5) + Spring Security, stateless sessions
- **Other:** Lombok, Jakarta Validation, Jackson, DevTools

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
  src/main/resources/
    application.properties                 # DB + JWT config
```

## Roles

Four user roles defined in `ERole`: `ROLE_CASHIER`, `ROLE_ACCOUNTANT`, `ROLE_DATA_ENTRY_OPERATOR`, `ROLE_OWNER`. Default role on signup is `ROLE_CASHIER`.

## API Endpoints

- `POST /api/auth/signin` - Login, returns JWT
- `POST /api/auth/signup` - Register new user

Public endpoints: `/api/auth/**`, `/api/test/**`, `/error`. All others require authentication.

## Build & Run

```bash
cd Service/superaccountant
./mvnw spring-boot:run        # Run the application
./mvnw test                   # Run tests
./mvnw clean package          # Build JAR
```

## Conventions

- Package base: `com.arktech.superaccountant`
- Feature-based package organization (e.g., `login/` contains controllers, models, repos, security)
- Uses `@Autowired` field injection
- Lombok `@Data` / `@NoArgsConstructor` on entities
- Request/response DTOs in `payload/request/` and `payload/response/`
- `@CrossOrigin(origins = "*")` on controllers
- Each user has a single `Role` (ManyToOne relationship)

## Database

PostgreSQL must be running locally on port 5432. Schema is auto-managed by Hibernate (`ddl-auto: update`). Credentials are in `application.properties`.
