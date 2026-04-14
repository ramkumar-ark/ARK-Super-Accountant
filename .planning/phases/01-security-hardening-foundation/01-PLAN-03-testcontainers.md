---
plan: 3
phase: 1
wave: 1
depends_on: []
files_modified:
  - Service/superaccountant/pom.xml
  - Service/superaccountant/src/test/resources/fixtures/masters-minimal.json
  - Service/superaccountant/src/test/resources/fixtures/daybook-minimal.json
  - Service/superaccountant/src/test/resources/application-test.properties
  - Service/superaccountant/src/test/java/com/arktech/superaccountant/SuperaccountantApplicationTests.java
  - Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java
  - Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/OrganizationRepositoryIT.java
autonomous: true
requirements:
  - SEC-03
  - SEC-04
must_haves:
  truths:
    - "`./mvnw test` passes on a machine with no local PostgreSQL installed — tests use a containerized DB"
    - "No test file references an absolute local path like C:/Program Files/TallyPrime/"
    - "UserRepository integration test saves and retrieves a User via a Testcontainers PostgreSQL instance"
    - "OrganizationRepository integration test saves and retrieves an Organization via a Testcontainers PostgreSQL instance"
  artifacts:
    - path: "Service/superaccountant/src/test/resources/fixtures/masters-minimal.json"
      provides: "Portable masters fixture"
    - path: "Service/superaccountant/src/test/resources/application-test.properties"
      provides: "Test config using Testcontainers datasource"
    - path: "Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java"
      provides: "UserRepository integration test"
      contains: "@Testcontainers"
    - path: "Service/superaccountant/src/test/java/com/arktech/superaccountant/repository/OrganizationRepositoryIT.java"
      provides: "OrganizationRepository integration test"
      contains: "@Testcontainers"
  key_links:
    - from: "UserRepositoryIT"
      to: "Testcontainers PostgreSQL container"
      via: "@Container PostgreSQLContainer"
      pattern: "PostgreSQLContainer"
---

# Plan 3: Testcontainers — Portable CI Test Suite

## Goal
Add Testcontainers PostgreSQL for repository integration tests; create `src/test/resources/` with portable JSON fixtures and a test `application.properties`; remove hardcoded absolute file paths from existing test classes.

## Context
Two existing test classes (`TallyParserServiceTest`, `GstValidationServiceTest`) reference `new File("C:/Program Files/TallyPrime/DayBook.json")` — these fail on any machine other than the developer's Windows PC. SEC-03 requires fixture files in `src/test/resources/`. SEC-04 requires Testcontainers-based PostgreSQL repository tests. No Testcontainers dependency currently exists in `pom.xml`. The `SuperaccountantApplicationTests` smoke test currently requires a live local PostgreSQL connection and will fail in CI. (SEC-03, SEC-04)

<threat_model>
## Threat Model (ASVS L1)

### Threats Addressed

- **[LOW] T-03-01 — Information Disclosure: Hardcoded absolute paths in tests reveal developer machine layout** — `C:/Program Files/TallyPrime/` leaks filesystem structure. Mitigation: replace with classpath resource loading from `src/test/resources/fixtures/`.

- **[LOW] T-03-02 — Denial of Service: Tests fail in CI due to missing local DB** — Breaks CI pipeline preventing security fixes from being validated. Mitigation: Testcontainers spins up an isolated PostgreSQL container per test run.

### Residual Risks

- Docker must be available in CI environment for Testcontainers to work. This is standard in GitHub Actions, GitLab CI, and Jenkins with Docker-in-Docker. Documented assumption; no mitigation needed in code.
- `SuperaccountantApplicationTests` `contextLoads()` test will need `@SpringBootTest` with Testcontainers to avoid requiring local PostgreSQL. Addressed in task 1.
</threat_model>

## Tasks

<task id="1">
<title>Add Testcontainers dependency to pom.xml; create test resources directory with fixtures and test config</title>
<read_first>
- `Service/superaccountant/pom.xml` — add Testcontainers BOM and PostgreSQL module; check existing test dependencies
- `Service/superaccountant/src/test/java/com/arktech/superaccountant/SuperaccountantApplicationTests.java` — smoke test that currently requires local PostgreSQL via @SpringBootTest
- `Service/superaccountant/src/test/java/com/arktech/superaccountant/tally/TallyParserServiceTest.java` — references hardcoded C: path
- `Service/superaccountant/src/test/java/com/arktech/superaccountant/gst/GstValidationServiceTest.java` — references hardcoded C: path
</read_first>
<action>
**Step 1 — pom.xml:**

Add the Testcontainers BOM in `<dependencyManagement>`:
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.testcontainers</groupId>
      <artifactId>testcontainers-bom</artifactId>
      <version>1.20.4</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Add Testcontainers core and PostgreSQL module in `<dependencies>` (test scope):
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

Also add Spring Boot test support for Testcontainers (already available via `spring-boot-starter-data-rest-test` in the existing pom but verify):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

**Step 2 — Create `src/test/resources/` directory structure:**

Create `src/test/resources/fixtures/masters-minimal.json` — a minimal valid Tally masters JSON that exercises the parser without requiring real data. Content:
```json
{
  "tallymessage": [
    { "group": { "name": "Purchase Accounts", "parent": "" } },
    { "ledger": { "name": "Test Cement Purchases", "guid": "guid-test-001", "parent": "Purchase Accounts" } },
    { "group": { "name": "Sales Accounts", "parent": "" } },
    { "ledger": { "name": "Test Sales Revenue", "guid": "guid-test-002", "parent": "Sales Accounts" } }
  ]
}
```

Create `src/test/resources/fixtures/daybook-minimal.json` — a minimal valid Tally day book JSON for GST validation tests:
```json
{
  "tallymessage": [
    {
      "vouchertypename": "Purchase",
      "vouchernumber": "PB/001",
      "date": "20250401",
      "partyledgername": "Test Supplier",
      "partygstin": "27AAAAA0000A1Z5",
      "narration": "Test purchase",
      "allledgerentries": [
        { "ledgername": "Test Cement Purchases", "amount": "-10000" },
        { "ledgername": "Input CGST @9%", "amount": "-900" },
        { "ledgername": "Input SGST @9%", "amount": "-900" }
      ],
      "ledgerentries": []
    }
  ]
}
```

Create `src/test/resources/application-test.properties`:
```properties
# Testcontainers datasource — URL/credentials injected by @DynamicPropertySource
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
JWT_SECRET=testcontainers-test-jwt-secret-min-32-chars
arktech.app.jwtExpirationMs=86400000
```

**Step 3 — Fix `TallyParserServiceTest.java`:**

Remove `testParseDayBookJson()` (the test that reads from `C:/Program Files/TallyPrime/DayBook.json`) — it is a manual exploratory test, not an automated test with assertions. The portable tests in `TallyMastersParserTest.java` already cover the parser. If preserving the method is desired, annotate it with `@Disabled("Manual test — requires local Tally installation")` rather than deleting.

**Step 4 — Fix `GstValidationServiceTest.java`:**

Remove or `@Disabled("Manual test — requires local Tally installation")` the `testValidateDayBookJson()` method that references `C:/Program Files/TallyPrime/DayBook.json`.

**Step 5 — Fix `SuperaccountantApplicationTests.java`:**

Update the smoke test to use Testcontainers so it does not require local PostgreSQL:
```java
@SpringBootTest
@Testcontainers
class SuperaccountantApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
    }
}
```
Add imports: `org.testcontainers.containers.PostgreSQLContainer`, `org.testcontainers.junit.jupiter.Container`, `org.testcontainers.junit.jupiter.Testcontainers`, `org.springframework.test.context.DynamicPropertyRegistry`, `org.springframework.test.context.DynamicPropertySource`.
Also add `@TestPropertySource(properties = "JWT_SECRET=testcontainers-test-jwt-secret-min-32-chars")` so the startup validation in Plan 1's `JwtUtils` passes.
</action>
<acceptance_criteria>
- `pom.xml` contains `<artifactId>postgresql</artifactId>` inside a `<groupId>org.testcontainers</groupId>` dependency block
- `pom.xml` contains `testcontainers-bom` in `<dependencyManagement>`
- `src/test/resources/fixtures/masters-minimal.json` exists and contains `"tallymessage"`
- `src/test/resources/fixtures/daybook-minimal.json` exists and contains `"tallymessage"`
- `src/test/resources/application-test.properties` exists and contains `spring.jpa.hibernate.ddl-auto=create-drop`
- `TallyParserServiceTest.java` does NOT contain `C:/Program Files`
- `GstValidationServiceTest.java` does NOT contain `C:/Program Files`
- `SuperaccountantApplicationTests.java` contains `PostgreSQLContainer`
</acceptance_criteria>
</task>

<task id="2">
<title>Write UserRepository and OrganizationRepository integration tests using Testcontainers</title>
<read_first>
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/models/User.java` — User entity fields (id: Long, username, email, password, role: ManyToOne Role, organizationId: UUID)
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/models/Organization.java` — Organization entity fields (id: UUID, name, createdAt)
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/repository/UserRepository.java` — repository interface to test
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/masters/repository/OrganizationRepository.java` — repository interface to test
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/models/ERole.java` — ERole enum values needed to seed a Role for the User test
- `Service/superaccountant/src/main/java/com/arktech/superaccountant/login/repository/RoleRepository.java` — needed to save a Role in UserRepositoryIT
</read_first>
<action>
**Create `src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java`:**

```java
package com.arktech.superaccountant.repository;

import com.arktech.superaccountant.login.models.ERole;
import com.arktech.superaccountant.login.models.Role;
import com.arktech.superaccountant.login.models.User;
import com.arktech.superaccountant.login.repository.RoleRepository;
import com.arktech.superaccountant.login.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "JWT_SECRET=testcontainers-test-jwt-secret-min-32-chars")
class UserRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void saveAndFindByUsername() {
        Role role = new Role(ERole.ROLE_OWNER);
        roleRepository.save(role);

        User user = new User("testuser", "test@example.com", "hashedpassword");
        user.setRole(role);
        userRepository.save(user);

        Optional<User> found = userRepository.findByUsername("testuser");
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
        assertEquals(ERole.ROLE_OWNER, found.get().getRole().getName());
    }

    @Test
    void existsByUsername_trueForExistingUser() {
        Role role = new Role(ERole.ROLE_CASHIER);
        roleRepository.save(role);

        User user = new User("existinguser", "existing@example.com", "hashedpassword");
        user.setRole(role);
        userRepository.save(user);

        assertTrue(userRepository.existsByUsername("existinguser"));
        assertFalse(userRepository.existsByUsername("nonexistent"));
    }

    @Test
    void existsByEmail_trueForExistingEmail() {
        Role role = new Role(ERole.ROLE_CASHIER);
        roleRepository.save(role);

        User user = new User("emailuser", "unique@example.com", "hashedpassword");
        user.setRole(role);
        userRepository.save(user);

        assertTrue(userRepository.existsByEmail("unique@example.com"));
        assertFalse(userRepository.existsByEmail("nothere@example.com"));
    }
}
```

**Create `src/test/java/com/arktech/superaccountant/repository/OrganizationRepositoryIT.java`:**

```java
package com.arktech.superaccountant.repository;

import com.arktech.superaccountant.masters.models.Organization;
import com.arktech.superaccountant.masters.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "JWT_SECRET=testcontainers-test-jwt-secret-min-32-chars")
class OrganizationRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void saveAndFindById() {
        Organization org = new Organization("Acme Construction Pvt Ltd");
        Organization saved = organizationRepository.save(org);

        assertNotNull(saved.getId());

        Optional<Organization> found = organizationRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Acme Construction Pvt Ltd", found.get().getName());
        assertNotNull(found.get().getCreatedAt());
    }

    @Test
    void deleteOrganization_removedFromDatabase() {
        Organization org = new Organization("Temporary Org");
        Organization saved = organizationRepository.save(org);

        organizationRepository.deleteById(saved.getId());

        assertFalse(organizationRepository.findById(saved.getId()).isPresent());
    }
}
```

**Note on Role constructor:** Read `Role.java` before writing — if `Role` uses `@NoArgsConstructor` only (no `Role(ERole)` constructor), use `Role role = new Role(); role.setName(ERole.ROLE_OWNER);` instead of `new Role(ERole.ROLE_OWNER)`.
</action>
<acceptance_criteria>
- `src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java` exists and contains `@Testcontainers`
- `src/test/java/com/arktech/superaccountant/repository/OrganizationRepositoryIT.java` exists and contains `@Testcontainers`
- Both IT files contain `PostgreSQLContainer`
- Both IT files contain `@DynamicPropertySource`
- Both IT files contain `@DataJpaTest`
- Running `cd Service/superaccountant && ./mvnw test -pl . -Dtest="UserRepositoryIT,OrganizationRepositoryIT" -q` (requires Docker) exits 0
- Running `cd Service/superaccountant && ./mvnw test -q` (with JWT_SECRET set, requires Docker) exits 0
</acceptance_criteria>
</task>

## Verification

```bash
cd "Service/superaccountant"
export JWT_SECRET="testcontainers-test-jwt-secret-min-32-chars"

# Fixtures exist
ls src/test/resources/fixtures/masters-minimal.json && echo "PASS" || echo "FAIL"
ls src/test/resources/fixtures/daybook-minimal.json && echo "PASS" || echo "FAIL"

# No absolute paths in tests
grep -r "C:/Program Files" src/test/ && echo "FAIL: absolute paths remain" || echo "PASS: no absolute paths"

# Testcontainers in pom
grep "testcontainers" pom.xml && echo "PASS" || echo "FAIL"

# IT test files exist
ls src/test/java/com/arktech/superaccountant/repository/UserRepositoryIT.java && echo "PASS" || echo "FAIL"
ls src/test/java/com/arktech/superaccountant/repository/OrganizationRepositoryIT.java && echo "PASS" || echo "FAIL"

# Run all tests (requires Docker)
./mvnw test -q
```

## must_haves
- `src/test/resources/fixtures/` directory exists with portable JSON fixtures
- No test file contains an absolute filesystem path
- `pom.xml` has Testcontainers BOM + PostgreSQL module in test scope
- `UserRepositoryIT` and `OrganizationRepositoryIT` pass against a Testcontainers PostgreSQL instance
- `./mvnw test` passes on a clean machine with Docker (no local PostgreSQL needed)

<output>
After completion, create `.planning/phases/01-security-hardening-foundation/01-03-SUMMARY.md`
</output>
