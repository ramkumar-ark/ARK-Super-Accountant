# Testing

## Backend (Java)

### Framework
- JUnit 5 (`org.junit.jupiter`)
- No mocking framework — direct instantiation
- No `@SpringBootTest` except the smoke test (`SuperaccountantApplicationTests`)

### Patterns
- `@BeforeEach` for test setup
- `assertThrows` for error/exception paths
- `MockMultipartFile` for file-based tests
- Text blocks (Java 15+) for inline JSON fixtures
- Direct object instantiation (no Mockito)

### Running Tests
```bash
cd Service/superaccountant
./mvnw test
```

---

## Frontend (TypeScript / React)

### Framework & Libraries
- **Test runner:** Vitest 4
- **Component testing:** Testing Library React 16
- **DOM environment:** happy-dom
- **Matchers:** `@testing-library/jest-dom`

### File Organization
- Co-located `.test.tsx` files alongside component files
- Example: `HeroSection.tsx` → `HeroSection.test.tsx`

### Patterns
- `describe` / `it` block structure
- `render` + `screen` queries
- `fireEvent` for user interaction simulation
- Accessibility-first queries: `getByRole`, `getByText`, `getByLabelText`

### Running Tests
```bash
cd Client
npm test
# or
npm run test
```

### Coverage
- 75 tests at landing page phase completion
- Two exploratory/manual tests reference a local file path (`C:/Program Files/TallyPrime/DayBook.json`) — not suitable for CI

---

## Test Coverage Gaps
- Backend has minimal test coverage beyond smoke test
- No integration tests (no `@SpringBootTest` with test DB)
- No E2E tests
- No API contract tests
