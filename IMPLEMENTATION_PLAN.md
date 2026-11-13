# Travel Ideas AI — Implementation Plan & Technical Specification

This document is the source of truth for implementing the Travel Ideas AI MVP. Follow the phases in order. Do not implement future phases early, add unrequested features, or change the architecture without first reporting the reason and receiving approval.

## 0. Project Goal, Technology and Scope

Build a small full-stack application that recommends three travel destinations based on a user's departure city, interests, budget, currency, trip duration and preferred transport.

The backend uses Java and Spring Boot. Persistence uses Spring Data JPA with an H2 in-memory database. AI generation uses LangChain4j with a locally running Ollama model. The frontend uses React and Vite.

**Dependency and version rule:** use the versions already defined in the existing `pom.xml` and frontend package files. Do not upgrade versions, add dependencies, or replace the existing project setup unless explicitly approved. Configure LangChain4j and Ollama manually; do not add an unapproved Spring Boot AI starter.

### MVP scope

- Generate exactly three travel recommendations.
- Calculate Smart Trip Match scores in Java, never in the AI model.
- Display the recommendations and the score breakdown in the frontend.
- Save, list, edit and delete saved ideas.
- Use estimated total trip costs in the currency selected by the user. These are estimates, not live prices or confirmed quotes.

### Out of scope

- Authentication and user accounts.
- Booking, ticket purchases or reservations.
- Live prices, live transport schedules, route building or maps.
- Currency conversion.
- Saving raw AI prompts or generation history.
- Redis, Kafka, Docker, RAG, vector databases or AI agents.
- Additional database entities unless explicitly approved.

## 1. Architecture and Scope Boundaries

The backend has two strictly separated flows.

### 1.1 Recommendation flow — stateless, no database access

`React (TravelForm)` → `POST /api/recommendations` → `RecommendationController` → `TravelRecommendationService` → `TravelRecommendationAssistant` (LangChain4j + Ollama) → `MatchScoreService` (Java scoring) → `RecommendationBatchResponse`.

Rules:

- `TravelRecommendationService` must not depend on `SavedIdeaRepository`, `SavedIdeaService` or any JPA repository.
- `TravelRecommendationAssistant` is a concrete Spring service class for the single Ollama implementation. Do not introduce an interface plus a one-implementation class unless a real second implementation becomes necessary.
- `MatchScoreService` is a plain Java service with no Spring, database, HTTP or AI dependencies.
- Generating recommendations must not write anything to the database.

### 1.2 Saved ideas flow — CRUD, no AI/scoring dependency

`React (RecommendationCard / SavedIdeas)` → `POST | GET | PATCH | DELETE /api/ideas` → `SavedIdeaController` → `SavedIdeaService` → `SavedIdeaRepository` → H2 Database.

Rules:

- `SavedIdeaService` depends only on `SavedIdeaRepository` (and ordinary mapping code if needed).
- `SavedIdeaService` must not depend on `TravelRecommendationAssistant`, `TravelRecommendationService` or `MatchScoreService`.
- `SavedIdeaRepository` is the only persistence abstraction required for this MVP.
- `SavedIdea` is the only JPA entity in this MVP.

### 1.3 Suggested backend package structure

Use the existing root package from the project. Keep the structure simple:

```text
backend/src/main/java/<existing-root-package>/
    controller/
    service/
    repository/
    entity/
    dto/
    model/
    config/
    exception/
```

Do not create extra layers, mappers, factories or interfaces without a concrete need.

## 2. Data Models

Use Java `record` for request/response/AI DTOs. Use a normal Java class for the JPA entity.

### 2.1 Enums (`model`)

- `Interest`: `NATURE`, `HISTORY`, `FOOD`, `CULTURE`, `BEACH`, `ADVENTURE`, `ARCHITECTURE`, `SHOPPING`.
- `TransportMode`: `TRAIN`, `BUS`, `CAR`, `PLANE`.
- `CurrencyCode`: `PLN`, `EUR`, `USD`.

Persist `CurrencyCode` as a string. Do not perform currency conversion. The AI estimate must use the currency provided in the request.

### 2.2 Request DTOs (`dto`)

All request DTOs are Java records and use Jakarta Validation. Validate at the HTTP boundary with `@Valid`.

#### `RecommendationRequest`

| Field | Java type | Validation / rule |
|---|---|---|
| `departureCity` | `String` | Required, non-blank, 2–100 characters after trimming whitespace. |
| `interests` | `List<Interest>` | Required, 1–8 elements, no null elements and no duplicates. |
| `budget` | `BigDecimal` | Required, strictly greater than zero, at most 10 integer digits and 2 fractional digits. |
| `currency` | `CurrencyCode` | Required. |
| `durationDays` | `Integer` | Required, from 1 to 30 inclusive. |
| `preferredTransport` | `TransportMode` | Required. |

Important validation details:

- `@NotBlank` and `@Size` alone do not guarantee that the trimmed city name has a length of at least two. Enforce the trimmed-length rule explicitly at the validation boundary.
- `@NotEmpty` and `@Size` do not detect duplicate list elements. Enforce uniqueness so duplicate interests produce `400 Bad Request`. Use a small custom Jakarta Validation constraint if appropriate; do not add a third-party library just for this check.
- Normalize `departureCity` with `trim()` before including it in the AI prompt.
- `MatchScoreService` must still use unique sets defensively, even though the incoming request is validated.

#### `SaveIdeaRequest`

| Field | Java type | Validation / rule |
|---|---|---|
| `title` | `String` | Required, non-blank, maximum 100 characters. |
| `destination` | `String` | Required, non-blank, maximum 100 characters. |
| `country` | `String` | Required, non-blank, maximum 100 characters. |
| `description` | `String` | Required, non-blank, maximum 1000 characters. |
| `whyRecommended` | `String` | Required, non-blank, maximum 1000 characters. |
| `estimatedTotalCost` | `BigDecimal` | Required, strictly greater than zero, at most 10 integer digits and 2 fractional digits. |
| `currency` | `CurrencyCode` | Required. |
| `suggestedDurationDays` | `Integer` | Required, from 1 to 30 inclusive. |
| `matchScore` | `Integer` | Required, from 0 to 100 inclusive. |
| `personalNote` | `String` | Optional / nullable, maximum 500 characters. |

The frontend must set `title` to the recommendation's `destination` by default. The user may change the title before saving. The backend still requires a non-blank title.

MVP limitation: the frontend sends the selected recommendation and score back when saving. The backend validates the request fields, but without authentication or server-side generation storage it cannot prove that a client-supplied score has not been modified. This is an accepted demo limitation; do not add generation-history persistence to solve it.

#### `UpdateSavedIdeaRequest`

| Field | Java type | Validation / rule |
|---|---|---|
| `title` | `String` | Required, non-blank, maximum 100 characters. |
| `personalNote` | `String` | Optional / nullable, maximum 500 characters. |

PATCH semantics must be explicit and consistent:

- `title` is always required. A missing or null title is a validation error (`400`).
- `personalNote` is optional. If it is omitted or explicitly set to `null`, clear the existing note by setting it to `null`.
- If `personalNote` contains a string, replace the existing note with that string.
- PATCH updates only `title` and `personalNote`. It must not modify destination, country, description, cost, currency, duration, score or creation time.

A normal record plus Jackson deserialization is sufficient for this behavior because omitted and explicit-null `personalNote` intentionally have the same meaning. Do not add custom partial-update machinery.

### 2.3 AI DTOs (`dto`)

These records describe the data expected from Ollama. They contain no score fields. The AI must never calculate or return Smart Trip Match scores.

#### `AiTravelRecommendation`

Represents one destination suggested by the AI.

| Field | Java type | Validation after AI response |
|---|---|---|
| `destination` | `String` | Not null/blank after trimming; maximum 100 characters. |
| `country` | `String` | Not null/blank after trimming; maximum 100 characters. |
| `description` | `String` | Not null/blank after trimming; maximum 1000 characters. |
| `whyRecommended` | `String` | Not null/blank after trimming; maximum 1000 characters. |
| `interestTags` | `List<Interest>` | Not null, non-empty, no null elements and no duplicates. Values must map to the `Interest` enum. |
| `estimatedTotalCost` | `BigDecimal` | Not null, strictly greater than zero, maximum 10 integer digits and 2 fractional digits. |
| `suggestedDurationDays` | `Integer` | Not null, from 1 to 30 inclusive. |

The estimate is the approximate **total cost for one person for the entire trip**, not a per-day cost. It must be expressed in the currency from `RecommendationRequest`. The prompt must explicitly state this. Do not claim that the price is live, verified or guaranteed.

#### `AiRecommendationBatch`

Contains one field:

- `recommendations`: `List<AiTravelRecommendation>`.

Validation after AI response:

- The batch and list must not be null.
- The list must contain exactly three elements.
- No element may be null.
- All three destinations must be unique by the pair `(destination, country)`, case-insensitively.
- For uniqueness comparison, trim both strings and normalize case using `Locale.ROOT`. Compare the normalized destination and normalized country together. Do not compare destination alone.
- Every recommendation must pass all `AiTravelRecommendation` field checks above.

If any check fails, throw `InvalidAiResponseException`. Never silently remove an item, invent a replacement, use random defaults or return fewer than three recommendations.

### 2.4 Response DTOs (`dto`)

#### `MatchScoreResponse`

- `int interestsScore` — 0–50.
- `int budgetScore` — either 0 or 30.
- `int durationScore` — either 0 or 20.
- `int totalScore` — 0–100.

#### `RecommendationResponse`

Fields:

- `String destination`
- `String country`
- `String description`
- `String whyRecommended`
- `List<Interest> matchingInterests` — only the intersection of user interests and AI `interestTags`.
- `BigDecimal estimatedTotalCost`
- `CurrencyCode currency` — copied from the user's request; the AI does not choose the currency.
- `Integer suggestedDurationDays`
- `MatchScoreResponse matchScore`

Do not expose `AiTravelRecommendation` directly from the controller. Map it to this response DTO after validation and scoring.

#### `RecommendationBatchResponse`

- `List<RecommendationResponse> recommendations` — exactly three elements.

#### `SavedIdeaResponse`

Fields:

- `Long id`
- `String title`
- `String destination`
- `String country`
- `String description`
- `String whyRecommended`
- `BigDecimal estimatedTotalCost`
- `CurrencyCode currency`
- `Integer suggestedDurationDays`
- `Integer matchScore`
- `String personalNote`
- `Instant createdAt`

Serialize `Instant` as an ISO-8601 timestamp. The creation timestamp is assigned by the backend, never accepted from the client.

### 2.5 JPA Entity (`entity`)

#### `SavedIdea`

One JPA entity, mapped to `saved_ideas`. Use a normal class, `@Entity`, `@Table(name = "saved_ideas")`, Lombok `@Getter` and a protected no-argument constructor (for example, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`). **Do not use `@Data`.** Avoid broad setters. Use a constructor/factory for initial values and a domain method for the allowed update.

| Field | Java type | JPA mapping / rule |
|---|---|---|
| `id` | `Long` | `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`. |
| `title` | `String` | `nullable = false`, length 100. |
| `destination` | `String` | `nullable = false`, length 100. |
| `country` | `String` | `nullable = false`, length 100. |
| `description` | `String` | `nullable = false`, length 1000. |
| `whyRecommended` | `String` | `nullable = false`, length 1000. |
| `estimatedTotalCost` | `BigDecimal` | `nullable = false`, precision 12, scale 2. |
| `currency` | `CurrencyCode` | `@Enumerated(EnumType.STRING)`, `nullable = false`, length 3. |
| `suggestedDurationDays` | `Integer` | `nullable = false`, value 1–30 enforced by request/service validation. |
| `matchScore` | `Integer` | `nullable = false`, value 0–100 enforced by request/service validation. |
| `personalNote` | `String` | Nullable, length 500. |
| `createdAt` | `Instant` | `nullable = false`, `updatable = false`; set by the backend at creation. |

Entity behavior:

- Use `@PrePersist` to set `createdAt` to `Instant.now()` only when it is null.
- Do not expose a public setter for `id` or `createdAt`.
- Provide `updateDetails(String title, String personalNote)` to update only those two editable fields.
- Do not put HTTP, repository or AI logic in the entity.
- Do not generate `equals()` / `hashCode()` over all mutable entity fields.

### 2.6 Repository (`repository`)

`SavedIdeaRepository` extends `JpaRepository<SavedIdea, Long>`.

Add the derived query method:

```java
List<SavedIdea> findAllByOrderByCreatedAtDesc();
```

`GET /api/ideas` must return the newest saved ideas first. Do not rely on unspecified database row order.

No custom DAO or generic repository abstraction is required.

## 3. Smart Trip Match Scoring Specification

Implement scoring strictly in `MatchScoreService`. It must be deterministic and independent of Spring, HTTP, the database and Ollama.

Public methods:

```java
MatchScoreResponse calculateScore(
        RecommendationRequest request,
        AiTravelRecommendation recommendation
);

List<Interest> findMatchingInterests(
        RecommendationRequest request,
        AiTravelRecommendation recommendation
);
```

Private helpers:

- `calculateInterestsScore(...)`
- `calculateBudgetScore(...)`
- `calculateDurationScore(...)`

### 3.1 Interests score — maximum 50

- `N` = number of unique interests in `request.interests()`.
- `M` = number of unique interests in the intersection of `request.interests()` and `recommendation.interestTags()`.
- `interestsScore = round(50 * M / N)` using `RoundingMode.HALF_UP`.
- AI tags not selected by the user add no points.
- `findMatchingInterests()` returns only the intersection, preserving the order from the user's request.
- Use set-based comparison so duplicates cannot inflate the score. The incoming request must still reject duplicates as described above.
- Reject null or empty required inputs rather than dividing by zero or returning an arbitrary score.

### 3.2 Budget score — 0 or 30

```java
estimatedTotalCost.compareTo(request.budget()) <= 0 ? 30 : 0
```

Both values are in the same requested currency. Equality earns 30 points.

### 3.3 Duration score — 0 or 20

```java
suggestedDurationDays <= request.durationDays() ? 20 : 0
```

Equality earns 20 points.

### 3.4 Total score

```text
totalScore = interestsScore + budgetScore + durationScore
```

The result must always be between 0 and 100 for valid inputs.

### 3.5 Reference test cases (`MatchScoreServiceTest`)

| Interest matches | Interest points | Budget condition | Duration condition | Total |
|---|---:|---|---|---:|
| 3/3 | 50 | Within budget (+30) | Within duration (+20) | **100** |
| 2/3 | 33 | Within budget (+30) | Within duration (+20) | **83** |
| 1/3 | 17 | Within budget (+30) | Within duration (+20) | **67** |
| 0/3 | 0 | Within budget (+30) | Within duration (+20) | **50** |
| 3/3 | 50 | Over budget (+0) | Within duration (+20) | **70** |
| 3/3 | 50 | Within budget (+30) | Over duration (+0) | **80** |
| 2/3 | 33 | Over budget (+0) | Over duration (+0) | **33** |
| 0/3 | 0 | Over budget (+0) | Over duration (+0) | **0** |

Also test:

- Duplicate interests do not increase the score (defensive test of the service).
- Exact budget boundary earns 30 points.
- Exact duration boundary earns 20 points.
- Rounding behavior for fractions, including 1/3 → 17 and 2/3 → 33.
- `findMatchingInterests()` returns the correct intersection in request order.
- Null/empty invalid inputs are rejected safely.

## 4. Services

### 4.1 `TravelRecommendationAssistant`

Implement as one concrete Spring service class (`@Service`) for the Ollama integration. It receives a configured LangChain4j chat model through constructor injection and returns `AiRecommendationBatch`.

Contract:

```java
AiRecommendationBatch generateRecommendations(RecommendationRequest request);
```

Responsibilities:

- Build a clear prompt using the normalized user request.
- Ask for exactly three different destinations.
- Ask for all fields required by `AiTravelRecommendation`.
- Require `interestTags` values to use only the `Interest` enum names.
- Require `estimatedTotalCost` to be an approximate total cost for one person for the whole trip, in the requested currency.
- Make clear that prices are estimates and must not be presented as live or verified prices.
- Ask the model to return only the required structured JSON, without prose or Markdown fences.
- Convert the model response to `AiRecommendationBatch`.

Parsing rule:

- Verify the structured-output / JSON parsing API against the LangChain4j version already pinned in the project. Do not assume that LangChain4j automatically converts arbitrary model text into a Java record.
- If the configured integration returns JSON text, deserialize it explicitly with the project's existing Jackson `ObjectMapper` (or the supported structured-output mechanism for the pinned version).
- Treat malformed JSON, incompatible field types, unknown enum values and an invalid response structure as `InvalidAiResponseException`.
- Do not add a new JSON library if Jackson is already available through the existing Spring Boot dependencies.

The assistant handles failures from the model call and response parsing as separate categories; see Section 6.

### 4.2 `TravelRecommendationService`

Dependencies through constructor injection:

- `TravelRecommendationAssistant`
- `MatchScoreService`

Contract:

```java
RecommendationBatchResponse generateRecommendations(
        RecommendationRequest request
);
```

Flow:

1. Call `assistant.generateRecommendations(request)`.
2. Validate the entire batch and every recommendation using the rules in Section 2.3.
3. If the response is invalid, throw `InvalidAiResponseException`.
4. For each valid recommendation, call `MatchScoreService.calculateScore(...)`.
5. Obtain `matchingInterests` from `MatchScoreService.findMatchingInterests(...)` so that the matching logic is not duplicated.
6. Map the AI DTO and score data to `RecommendationResponse`. Set `currency` from the original user request.
7. Return one `RecommendationBatchResponse` containing exactly three items.

Do not persist recommendations. Do not add repository dependencies.

### 4.3 `SavedIdeaService`

Dependency through constructor injection:

- `SavedIdeaRepository`

Public methods:

```java
SavedIdeaResponse create(SaveIdeaRequest request);
List<SavedIdeaResponse> findAll();
SavedIdeaResponse update(Long id, UpdateSavedIdeaRequest request);
void delete(Long id);
```

Responsibilities:

- Map `SaveIdeaRequest` to `SavedIdea`.
- Map `SavedIdea` to `SavedIdeaResponse`.
- Return saved ideas ordered by `createdAt` descending using `findAllByOrderByCreatedAtDesc()`.
- On update, find the entity or throw `ResourceNotFoundException`, then call `updateDetails(...)`.
- On delete, first find the entity or throw `ResourceNotFoundException`, then call `repository.delete(entity)`.
- Do not use `repository.deleteById(id)` alone: its standard behavior does not guarantee a 404 when the ID does not exist.
- Use `@Transactional` for write operations and `@Transactional(readOnly = true)` for read operations where appropriate.

Only `title` and `personalNote` may be changed by the update method.

## 5. REST Controllers and Endpoints

Controllers only map HTTP requests, apply request validation and delegate to services. No business logic or direct repository access in controllers.

### 5.1 `RecommendationController`

| HTTP | Endpoint | Request | Response |
|---|---|---|---|
| POST | `/api/recommendations` | `@Valid @RequestBody RecommendationRequest` | `200 OK` with `RecommendationBatchResponse` |

### 5.2 `SavedIdeaController`

| HTTP | Endpoint | Request | Response |
|---|---|---|---|
| POST | `/api/ideas` | `@Valid @RequestBody SaveIdeaRequest` | `201 Created` with `SavedIdeaResponse` |
| GET | `/api/ideas` | None | `200 OK` with `List<SavedIdeaResponse>`, newest first |
| PATCH | `/api/ideas/{id}` | `@PathVariable Long id`, `@Valid @RequestBody UpdateSavedIdeaRequest` | `200 OK` with updated `SavedIdeaResponse` |
| DELETE | `/api/ideas/{id}` | `@PathVariable Long id` | `204 No Content`; `404` when not found |

Do not add a GET-by-ID endpoint unless separately requested. It is not required by the current MVP scope.

## 6. Exceptions and Global Error Handling

Use a single `@RestControllerAdvice` class named `GlobalExceptionHandler`. Return consistent client-safe error responses, preferably Spring `ProblemDetail` if supported by the project's Spring version. Never expose stack traces, internal URLs, credentials, raw model diagnostics or sensitive implementation details to the client.

Map errors as follows:

| Exception / situation | HTTP status | Meaning |
|---|---:|---|
| `MethodArgumentNotValidException` | 400 | Request DTO validation failed. |
| `HttpMessageNotReadableException` | 400 | Malformed JSON, invalid enum value or unreadable request body. |
| Relevant request constraint failures | 400 | Invalid request parameter/path value. |
| `ResourceNotFoundException` | 404 | Saved idea ID does not exist. |
| `InvalidAiResponseException` | 502 | Ollama responded, but its output was malformed, unparsable or failed the AI response contract. |
| `AiServiceUnavailableException` | 503 | Ollama could not be reached, timed out or was unavailable. |
| Unexpected unhandled exception | 500 | Generic internal server error with no stack trace in the response. |

### 6.1 AI failure classification

Use `AiServiceUnavailableException` (503) for failures that mean the AI service could not provide a response:

- Connection refused or connection failure.
- DNS / connection establishment failure.
- Connection or read timeout.
- Ollama service unavailable or relevant server-side service failure.

Use `InvalidAiResponseException` (502) when Ollama was reached and returned output that cannot be accepted:

- Malformed or unparsable JSON.
- Unknown enum value or incompatible field type in the model output.
- Null response, null recommendation list or null list element.
- Fewer or more than three recommendations.
- Duplicate destination/country pair.
- Missing, blank, overlong or otherwise invalid recommendation fields.

Do not convert every exception into `AiServiceUnavailableException`. In particular, JSON parsing and response-contract failures are not connectivity failures. Preserve the original cause in server-side logs, but do not expose it to the frontend.

`GlobalExceptionHandler` is first introduced in Phase 3 for validation and CRUD exceptions. Extend the same handler when AI exceptions are introduced. Do not create duplicate advice classes.

## 7. Configuration

### 7.1 H2 and JPA (`application.yml`)

Use an in-memory H2 database:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:travelideas
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    open-in-view: false
  h2:
    console:
      enabled: false
```

The database is intentionally in-memory. Saved ideas are cleared when the backend restarts. Do not add file-based persistence for this MVP.

### 7.2 Ollama properties

Use environment-overridable values in `application.yml`:

```yaml
app:
  ai:
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      model: ${OLLAMA_MODEL:llama3.2:3b}
      connect-timeout: ${OLLAMA_CONNECT_TIMEOUT:5s}
      read-timeout: ${OLLAMA_READ_TIMEOUT:60s}
```

Create `OllamaProperties` as a configuration-properties record/class with:

- `String baseUrl`
- `String model`
- `Duration connectTimeout`
- `Duration readTimeout`

Use `@ConfigurationProperties(prefix = "app.ai.ollama")` and register it using the project's existing Spring Boot configuration approach (`@ConfigurationPropertiesScan` or `@EnableConfigurationProperties`). Validate required properties where appropriate.

`OllamaConfig` creates and configures the LangChain4j Ollama model bean. Configure connection and response timeouts explicitly using the HTTP-client/model API supported by the pinned LangChain4j version. Do not guess builder methods: inspect the installed dependency/API before coding. Do not add an unapproved Spring AI or LangChain4j Boot starter.

### 7.3 CORS (`CorsConfig`)

Allow the frontend origin `http://localhost:5173` only for `/api/**`.

Allow only the methods needed by the API (`GET`, `POST`, `PATCH`, `DELETE`, and preflight `OPTIONS`) and the required headers (at least `Content-Type`). Do not enable credentials because this MVP has no authentication.

## 8. Frontend Architecture (React + Vite)

Keep frontend code simple. Use functional components and React hooks. Do not add Redux, routing libraries or additional state-management libraries for this MVP.

Suggested structure:

```text
frontend/
    .env                  # local file, do not commit
    .env.example          # committed template
    src/
        services/
            travelApi.js
        components/
            TravelForm.jsx
            RecommendationList.jsx
            RecommendationCard.jsx
            SavedIdeas.jsx
            SavedIdeaCard.jsx
        App.jsx
        main.jsx
        index.css
```

### 8.1 Environment variables

Local `frontend/.env`:

```env
VITE_API_BASE_URL=http://localhost:8080
```

Commit `frontend/.env.example` with the same non-secret example value. Do not commit `.env`. The repository `.gitignore` must ignore `.env` and `.env.*` while allowing `.env.example`.

### 8.2 API service

All backend HTTP calls must live in `src/services/travelApi.js`. Components must not call `fetch` or Axios directly.

Provide functions for:

- `generateRecommendations(request)`
- `saveIdea(request)`
- `getSavedIdeas()`
- `updateSavedIdea(id, request)`
- `deleteSavedIdea(id)`

Do not implement a GET-by-ID frontend function because the current API does not define that endpoint.

### 8.3 Components and UI behavior

- `TravelForm`: collects departure city, interests, budget, currency, duration and transport.
- `RecommendationList`: displays the three generated results.
- `RecommendationCard`: displays destination details, estimated total cost, suggested days, matching interests and the score breakdown (Interests / Budget / Duration); provides a Save action.
- `SavedIdeas`: loads and displays saved ideas.
- `SavedIdeaCard`: displays a saved idea and allows inline editing of `title` and `personalNote`, plus deletion.
- `App`: owns the top-level state and coordinates form submission, recommendations and saved ideas.

Handle loading, empty and error states. Show useful validation/API error messages without exposing server stack traces. After save, update the saved-ideas UI. After edit or delete, refresh or update the displayed list consistently.

When saving a recommendation, send all required `SaveIdeaRequest` fields. Default `title` to `destination`; `personalNote` may be null.

## 9. Testing Strategy

Use the existing JUnit 5, Mockito and Spring Boot test dependencies. Do not add a test framework unless approved.

### Phase 1 tests

- `@DataJpaTest` for `SavedIdeaRepository`.
- Verify a `SavedIdea` can be saved and read from H2.
- Verify `createdAt` is populated by the backend.
- Verify the repository returns records ordered by creation time descending.

### Phase 2 tests

- Unit tests for all eight reference scoring cases.
- Duplicate-interest defensive behavior.
- Exact budget and duration boundaries.
- HALF_UP rounding cases.
- Matching-interest intersection and ordering.
- Invalid null/empty inputs are rejected safely.

### Phase 3 tests

- `SavedIdeaService` unit tests for create, list, update and delete.
- `@WebMvcTest` for the Saved Ideas endpoints.
- Verify create returns 201; list returns 200; update returns 200; delete returns 204.
- Verify missing IDs for update and delete return 404.
- Verify PATCH updates only title and note.
- Verify omitted/null `personalNote` clears the note.
- Verify invalid request fields return 400.
- Verify GET list is newest-first.

### Phase 4 tests

- Test assistant prompt construction and response parsing without requiring a live Ollama instance.
- Mock the configured LangChain4j model or isolate the parser to test valid JSON conversion.
- Test malformed JSON, unknown enum values, incompatible types and invalid response shape.
- Test mapping of connection/timeout failures to `AiServiceUnavailableException`.
- Do not make automated unit tests depend on a locally running Ollama service.

### Phase 5 tests

- Unit-test `TravelRecommendationService` with a mocked `TravelRecommendationAssistant`.
- Verify exactly three valid recommendations are mapped to the response.
- Verify null batches, wrong list size, null elements, duplicate destinations and invalid fields are rejected.
- Verify each recommendation receives the correct score and matching interests.
- Verify currency is taken from the user's request.
- Controller test for `POST /api/recommendations`, including request validation and expected error responses.

### Phase 6 tests / acceptance

Manually verify the complete UI flow:

1. Submit a valid travel request.
2. See exactly three recommendations.
3. See matching interests and all three score components.
4. Save a recommendation.
5. See it in Saved Ideas.
6. Edit title and personal note.
7. Clear the personal note by omitting it or sending null.
8. Delete a saved idea.
9. See loading, empty and error states where appropriate.

### Phase 7 tests

- Run the full backend test suite.
- Verify frontend/backend communication and CORS in a browser.
- Verify all endpoints and error mappings.
- Ensure README instructions work from a clean checkout.

## 10. Implementation Phases — Strict Order

Do not create or implement files from future phases early. If an earlier phase reveals a genuine dependency conflict, stop and report it before changing the phase scope.

### Phase 1 — Persistence

Create/update only:

- `CurrencyCode`
- `SavedIdea`
- `SavedIdeaRepository`
- `SaveIdeaRequest`
- `UpdateSavedIdeaRequest`
- `SavedIdeaResponse`
- H2/JPA settings in `application.yml`
- Repository test(s)

Verify:

- Entity has no `@Data` and uses a protected no-argument constructor.
- `createdAt` is assigned by `@PrePersist`.
- Money uses `BigDecimal` with the defined precision/scale.
- Enum is persisted as `EnumType.STRING`.
- H2 repository save/read test passes.
- Newest-first repository query works.

### Phase 2 — Scoring and recommendation input models

Create/update only:

- `Interest`
- `TransportMode`
- `RecommendationRequest`
- `AiTravelRecommendation`
- `MatchScoreResponse`
- `MatchScoreService`
- A small custom validation annotation/validator for unique interests only if needed to meet the request validation contract
- `MatchScoreServiceTest` and relevant request-validation tests

Verify:

- All eight reference scoring cases pass.
- Rounding, boundaries, duplicates and matching-interests ordering are tested.
- `MatchScoreService` has no Spring, AI, HTTP or database dependency.

### Phase 3 — Saved Ideas CRUD

Create/update only:

- `SavedIdeaService`
- `SavedIdeaController`
- `ResourceNotFoundException`
- `GlobalExceptionHandler` (initial CRUD and request-validation mappings)
- Service and controller tests

Verify:

- POST, GET, PATCH and DELETE work.
- Update changes only title and personal note.
- Omitted/null personal note clears the note.
- List is newest-first.
- Missing update/delete ID returns 404.
- Invalid request returns 400.

### Phase 4 — AI Integration

Create/update only:

- `AiRecommendationBatch`
- `OllamaProperties`
- `OllamaConfig`
- `TravelRecommendationAssistant` (concrete service class)
- `AiServiceUnavailableException`
- `InvalidAiResponseException`
- Assistant/parser tests
- Extend the existing `GlobalExceptionHandler` with AI exception mappings

Verify:

- Manual Ollama model configuration works with existing dependencies.
- Base URL, model and timeouts are configurable.
- Structured JSON is explicitly and correctly mapped to `AiRecommendationBatch`.
- Connectivity/timeouts and malformed AI output map to different exception types.
- Tests do not require a running Ollama instance.

### Phase 5 — Recommendation API

Create/update only:

- `RecommendationResponse`
- `RecommendationBatchResponse`
- `TravelRecommendationService`
- `RecommendationController`
- `CorsConfig`
- Recommendation service and controller tests

Verify:

- AI batch validation checks exactly three unique valid recommendations.
- Score and matching interests are calculated in Java.
- Currency comes from the request.
- POST `/api/recommendations` returns three response DTOs.
- Generation has no database dependency and does not persist results.
- CORS is configured for `http://localhost:5173` on `/api/**`.

### Phase 6 — Frontend

Create/update only:

- `frontend/.env.example`
- local `frontend/.env` only when needed (never commit it)
- `src/services/travelApi.js`
- `TravelForm.jsx`
- `RecommendationList.jsx`
- `RecommendationCard.jsx`
- `SavedIdeas.jsx`
- `SavedIdeaCard.jsx`
- `App.jsx`
- frontend styles and entry point as needed

Verify the end-to-end UI flow described in Section 9. Do not add future features or extra state-management libraries.

### Phase 7 — Finalization

Create/update only:

- Integration tests or final test adjustments
- `README.md`
- Demo asset (GIF or short video), if required by the assignment
- `.gitignore` only if necessary to ensure local `.env` and build artifacts are ignored while `.env.example` remains tracked

Verify CORS, full test suite, startup instructions and the final acceptance checklist below.

## 11. Final Acceptance Checklist (12 Checks)

The MVP is ready only when all applicable checks pass:

1. Backend builds and starts using the project's existing Java/Maven setup.
2. Frontend installs and starts using the documented commands.
3. H2 starts correctly and `SavedIdea` is the only persisted domain entity.
4. A valid recommendation request produces exactly three distinct recommendations.
5. Invalid request data is rejected with HTTP 400 and useful field-level messages.
6. AI connectivity/timeout errors return 503; malformed or invalid AI output returns 502.
7. Smart Trip Match is calculated in Java and all scoring tests pass.
8. Saved ideas can be created and the endpoint returns 201.
9. Saved ideas can be listed newest-first and the endpoint returns 200.
10. Saved idea title/note can be updated; missing IDs return 404; note-clearing semantics work.
11. Saved ideas can be deleted; existing IDs return 204 and missing IDs return 404.
12. Frontend/backend CORS works, README setup instructions are complete, `.env.example` is present, and the demo asset is included if required.

## 12. Copilot Workflow Rules

Work on one phase at a time using this cycle:

1. **Inspect:** read `.github/copilot-instructions.md`, this `IMPLEMENTATION_PLAN.md`, and the relevant existing source/tests.
2. **Analyze:** report the exact files to create/update, key fields/method signatures, tests to add and any conflicts with existing code. Do not modify files yet.
3. **Wait for approval:** do not write code until approval is given.
4. **Implement:** implement only the approved phase, with the smallest maintainable change.
5. **Verify:** run relevant tests and report the actual command and result. Do not claim tests passed unless they were run successfully.
6. **Review:** summarize changed files, design decisions, test coverage and any remaining issues.

General rules:

- Do not implement future phases early.
- Do not invent requirements or add features not listed here.
- Do not add dependencies, starters, libraries, abstractions or extra entities without approval.
- Follow KISS, YAGNI, DRY and SOLID pragmatically. Prefer straightforward code over overengineering.
- Keep business logic in services; controllers should remain thin.
- Use constructor injection.
- Never expose JPA entities directly from REST controllers.
- Use `BigDecimal` for monetary values.
- Do not use Lombok `@Data` on JPA entities.
- Keep API calls in `travelApi.js`; React components must not call the backend directly.
- Inspect existing code before changing it. Preserve working code and project conventions.
- If this plan conflicts with existing project code or a pinned library API, stop and explain the conflict. Do not silently redesign the project.

### Prompt template for each phase

Replace `[N]` with the phase number and use this prompt with Copilot Chat:

```text
Read .github/copilot-instructions.md and IMPLEMENTATION_PLAN.md.
Inspect the existing project and prepare a plan for Phase [N] ONLY.
Do NOT create or modify any files yet.

Report briefly:
1. Exact files/classes/tests you will create or update.
2. Their fields and method signatures.
3. How the implementation follows the specification and existing project conventions.
4. Any conflicts, missing dependencies or library API uncertainties.
5. The tests and commands you will use to verify the phase.

Do not implement future phases. Wait for my approval before writing code.
```

After approving the analysis, ask Copilot to implement only that approved phase and run its verification tests.