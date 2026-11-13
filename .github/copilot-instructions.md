# Travel Ideas AI
Stack: Java 25, Spring Boot 4.1.2, Maven, Spring MVC, JPA, H2, Validation, Lombok; React, Vite, JavaScript; LangChain4j + Ollama.

## Code Quality
- No overengineering, premature abstractions, or unnecessary patterns.
- Inspect existing code first; make minimal, focused changes.
- No unrequested features, dependencies, or unrelated refactoring.

## Backend
- Architecture: Controller -> Service -> Repository. Business logic in Services.
- Constructor injection; immutable DTOs as Java records by default; never expose JPA entities in REST.
- Jakarta Validation; centralized errors via @RestControllerAdvice.
- Lombok: @RequiredArgsConstructor for DI; @Slf4j only when logging. Never @Data on JPA entities (use @Getter and needed setters).
- Isolate LangChain4j; use structured output and validate responses. Follow existing config; no unapproved starters.

## Testing
- Test business logic and all scenarios.
- always add short concrete display names to tests; use @DisplayName for clarity.
- Use JUnit 5, Mockito, and AssertJ; avoid other testing frameworks.
- Test edge cases and all scenarios.

## Frontend
- Functional React components and hooks; API calls in src/services/.
- Handle loading, empty, and error states.

## Domain
- Calculate Smart Trip Match in Java: interests (50) + budget (30) + duration (20).
- AI output is untrusted; treat AI prices and duration as estimates.

## Workflow
- Run ./mvnw -q test after backend edits (use clean only if needed).
- Keep responses brief (max 3-5 lines): report changed files, test results, and blockers only.