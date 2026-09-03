# Planning Depth Comparison - 2026-09

## Task

Plan how to make requests for nonexistent owners return HTTP 404 instead of HTTP 500, while preserving the existing error page and adding automated tests.

This exercise used the Research artifact from Homework 1.2:

`architecture-2026-09.md`

No production code or tests were created or modified.

## Method

The planning exercise was performed in three fresh Claude Code sessions:

1. Default adaptive reasoning
2. `ultrathink` for one design-focused turn
3. `/effort high` for a testing and verification session

Each session used written files as the handoff rather than relying on previous chat history.

## Default reasoning

### Prompt

```text
Read CLAUDE.md and architecture-2026-09.md as the written handoff from the Research phase.

Plan how to make requests for nonexistent owners return HTTP 404 instead of HTTP 500, while preserving the existing error page and adding automated test coverage.

Do not write or modify code.

Before producing the plan, inspect the relevant production code, tests, templates, and configuration.

The plan must include:

1. The current behavior and its cause.
2. The proposed error-handling design and why it fits this repository.
3. Every file that must be created or modified.
4. The exact classes and methods affected.
5. Detailed implementation steps in execution order.
6. Risks, edge cases, and compatibility concerns.
7. Tests that must be created or updated.
8. Exact Gradle commands for verifying success.
9. Testable acceptance criteria.

Do not use vague steps such as "determine later" or "update as needed." A colleague should be able to execute the plan without asking a clarifying question.
```

### Response summary

The default response recommended creating a generic `ResourceNotFoundException` annotated with `@ResponseStatus(HttpStatus.NOT_FOUND)`.

It proposed creating the exception in the `system` package and replacing not-found exceptions across:

- `OwnerController`
- `PetController`
- `VisitController`

It also proposed controller tests, integration tests, an exception contract test, formatting commands, regression checks, and manual smoke tests.

### Strengths

- It inspected the repository before planning.
- It identified that `OwnerController.findOwner()` executes before the mapped handler.
- It named production and test files.
- It included implementation steps, risks, verification commands, and acceptance criteria.
- It recognized that `error.html` already supports HTTP 404.
- It separated production configuration from test-only configuration.

### Weaknesses

The default response expanded the scope beyond nonexistent owners. It included missing pets, `PetController`, `VisitController`, and eleven tests.

It also contained internal counting inconsistencies:

- It described seven existing throw sites, but listed six.
- It described the missing-pet gap as an eighth case when it was the seventh.
- It said six files would be modified, but listed seven.

It assumed that the dynamic exception message would appear in the rendered error page without proving that behavior.

It also stated that Docker was required for the full build, even though the MySQL and PostgreSQL tests contain mechanisms for skipping when Docker is unavailable.

### Default reasoning conclusion

The default response was detailed and useful, but it was too broad and contained assumptions that needed verification.

More detail did not automatically produce a more accurate or appropriately scoped plan.

## Ultrathink reasoning

### Prompt

```text
Read CLAUDE.md, architecture-2026-09.md, and the saved default plan.

Re-evaluate only the error-handling design step.

The scope must remain strictly limited to requests for nonexistent owners. Do not include missing pets, PetController, or VisitController.

Compare these approaches:

1. OwnerNotFoundException annotated with @ResponseStatus
2. ResponseStatusException thrown inline
3. @ControllerAdvice with @ExceptionHandler

Recommend the approach that best fits this repository. Name the exact production and test files, classes, and methods involved. Explain the tradeoffs, risks, and how the existing error.html page will continue to work.

Do not write or modify code. ultrathink
```

### Response summary

The `ultrathink` response compared three possible designs:

1. A custom `OwnerNotFoundException` annotated with `@ResponseStatus`
2. An inline `ResponseStatusException`
3. A centralized `@ControllerAdvice`

It recommended:

```text
OwnerNotFoundException annotated with @ResponseStatus(HttpStatus.NOT_FOUND)
```

It placed the exception in:

```text
src/main/java/org/springframework/samples/petclinic/owner/OwnerNotFoundException.java
```

The reasoning was that the exception is specific to the owner feature and the repository is organized primarily by feature.

The proposed exception would accept the owner ID and construct a consistent message:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OwnerNotFoundException extends RuntimeException {

	public OwnerNotFoundException(int ownerId) {
		super("Owner not found with id: " + ownerId);
	}

}
```

It proposed replacing the two `IllegalArgumentException` sites in:

```text
src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java
```

The affected methods were:

- `findOwner(Integer ownerId)`
- `showOwner(int ownerId)`

Missing pets, `PetController`, and `VisitController` were explicitly excluded.

### Strengths

- It corrected the default response's scope expansion.
- It selected an owner-specific exception.
- It followed the repository's feature-based package structure.
- It compared multiple valid designs.
- It explained the tradeoffs between local and centralized error handling.
- It identified the exact production files and methods.
- It clearly documented non-goals.

### Weaknesses

The `ultrathink` response still assumed that the exception's dynamic message would appear in the rendered error page.

It used Maven verification commands even though the original planning requirement requested Gradle.

It also overstated the Docker requirement for the full build.

The response described the existing `?page=0` HTTP 500 behavior as correct. A more accurate description is that it is a separate, pre-existing issue that is outside the scope of this change.

### Ultrathink conclusion

`ultrathink` produced better architectural reasoning than the default response. It was especially useful for comparing design alternatives and controlling scope.

However, deeper reasoning did not remove the need to verify behavioral and build-related claims.

## High-effort reasoning

### Prompt

```text
Read CLAUDE.md, architecture-2026-09.md, and the saved ultrathink plan.

Plan only the automated testing and verification work for making nonexistent-owner requests return HTTP 404.

Keep the scope strictly limited to owners.

Requirements:

1. Name every exact test file and test method.
2. Use existing repository test patterns.
3. Use Gradle commands only.
4. Include controller-level and full integration coverage.
5. Do not assume that OwnerNotFoundException.getMessage() appears in the rendered error page unless repository behavior proves it.
6. State Docker requirements and test-skipping behavior accurately.
7. Include risks, regression checks, and independently testable acceptance criteria.
8. Do not write or modify code.

Avoid unnecessary tests or unrelated pet and visit scenarios.
```

Before submitting this prompt, the session-wide reasoning level was changed using:

```text
/effort high
```

### Response summary

The high-effort response concentrated only on automated testing and verification.

It proposed three test files and seven test methods.

#### New exception tests

File:

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundExceptionTests.java
```

Methods:

- `mapsToHttpNotFound()`
- `messageNamesTheOwnerId()`

These tests verify that the exception maps to HTTP 404 and that its internal message includes the owner ID.

#### Updated controller tests

File:

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java
```

Methods:

- `showOwnerWithUnknownIdReturnsNotFound()`
- `initUpdateOwnerFormWithUnknownIdReturnsNotFound()`
- `processUpdateOwnerFormWithUnknownIdReturnsNotFound()`

These tests cover all three `OwnerController` routes containing an `ownerId`.

The `GET /owners/{ownerId}/edit` test is particularly important because its handler does not query the repository directly. A 404 on that route proves the exception originates from the `@ModelAttribute` method.

#### New integration tests

File:

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java
```

Methods:

- `unknownOwnerRendersNotFoundErrorPage()`
- `unknownOwnerReturnsNotFoundJson()`

The integration test follows the existing `CrashControllerIntegrationTests` pattern and uses:

- `@SpringBootTest`
- `@AutoConfigureTestRestTemplate`
- `TestRestTemplate`
- The default H2 database

The HTML test verifies:

- HTTP 404
- The existing error page is rendered
- The page contains `The requested page was not found.`
- The page does not contain the HTTP 500 message
- The page is not the Whitelabel error page

The JSON test should explicitly send:

```http
Accept: application/json
```

It verifies:

- HTTP 404
- `"status": 404`
- `"error": "Not Found"`
- `"path": "/owners/9999"`
- A timestamp exists

### Message-handling correction

The high-effort response correctly removed the assumption that `OwnerNotFoundException.getMessage()` appears in the HTML or JSON error response.

The message is tested directly in `OwnerNotFoundExceptionTests`, where its behavior is deterministic.

The integration tests verify only the public HTTP behavior that the application guarantees.

### Gradle verification

The high-effort response used Gradle only:

```bash
./gradlew format

./gradlew checkFormatMain checkFormatTest checkstyleMain checkstyleTest checkstyleNohttp

./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerNotFoundExceptionTests"

./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests"

./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests.initUpdateOwnerFormWithUnknownIdReturnsNotFound"

./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerNotFoundIntegrationTests"

./gradlew test \
  --tests "org.springframework.samples.petclinic.system.CrashControllerIntegrationTests" \
  --tests "org.springframework.samples.petclinic.service.ClinicServiceTests" \
  --tests "org.springframework.samples.petclinic.system.I18nPropertiesSyncTest" \
  --tests "org.springframework.samples.petclinic.PetClinicIntegrationTests"

./gradlew build
```

No commands were executed during the planning exercise.

### Docker correction

The new tests use H2 and do not require Docker.

The MySQL and PostgreSQL integration tests are part of the normal test task, but they contain mechanisms for skipping when Docker is unavailable.

Docker is needed to execute those database-specific tests, but it is not required for the owner-specific tests planned here.

### Risk analysis

The high-effort response identified several important risks:

- The exception originates from an `@ModelAttribute` method.
- MockMvc behavior should be confirmed with the focused edit-form test first.
- Removing `@ResponseStatus` would return the behavior to HTTP 500.
- An unmatched owner search must remain HTTP 200 with a field error.
- `GET /owners/abc` remains a separate HTTP 400 case.
- `/oups` must continue returning HTTP 500.
- Missing pets and visits remain outside scope.
- The existing 404 translations and template should remain unchanged.
- The new integration test creates an additional Spring application context.

### High-effort conclusion

The high-effort response produced the strongest testing and verification plan. It was more precise about test boundaries, public behavior, Docker, Gradle, and unsupported assumptions.

Its additional detail was useful because the task contained subtle framework behavior and multiple verification levels.

## Comparison

| Area | Default | Ultrathink | High effort |
|---|---|---|---|
| Primary focus | Complete implementation plan | Error-handling design | Testing and verification |
| Scope | Expanded to owners and pets | Owners only | Owners only |
| Exception | Generic `ResourceNotFoundException` | Owner-specific `OwnerNotFoundException` | Assumes `OwnerNotFoundException` |
| Design alternatives | Limited comparison | Detailed three-option comparison | Uses the selected design as a precondition |
| Test detail | Broad, with unnecessary scenarios | Better scoped but still contained assumptions | Exact files, methods, assertions, and order |
| Dynamic message | Assumed to appear in error page | Still assumed | Not asserted in rendered output |
| Build commands | Gradle | Maven | Gradle |
| Docker behavior | Described as required | Described as required | Correctly described as optional for these tests |
| Scope control | Weak | Strong | Strong |
| Best use | Initial draft | Design decision | Detailed verification planning |

## What changed

Across the three reasoning levels:

- The scope became smaller and more precise.
- The exception changed from generic to owner-specific.
- The design alternatives and tradeoffs became clearer.
- The integration-test pattern became explicit.
- Unsupported message assumptions were removed.
- Verification returned to Gradle.
- Docker behavior was described more accurately.
- Acceptance criteria became more concrete and testable.
- The pagination HTTP 500 was correctly identified as a separate issue rather than correct behavior.

## What stayed the same

All three responses agreed that:

- Missing owners should return HTTP 404 instead of HTTP 500.
- The failure begins in `OwnerController.findOwner()`.
- The existing `error.html` should remain unchanged.
- A dedicated exception is safer than mapping every `IllegalArgumentException` to HTTP 404.
- Controller-level and integration tests are needed.
- No implementation should occur during this homework.

## When I would use each level

### Default reasoning

I would use default reasoning for a small, familiar, low-risk change when the affected files and verification steps are already clear.

### Ultrathink

I would use `ultrathink` for one difficult design decision involving several reasonable alternatives, framework behavior, or architectural tradeoffs.

### High effort

I would use `/effort high` when the whole session requires deeper analysis, especially for a multi-file change, subtle framework behavior, important risks, or detailed test and verification planning.

## Final assessment

Default reasoning was sufficient to identify the main problem and produce an initial plan, but it expanded the scope and introduced avoidable inconsistencies.

`ultrathink` improved the architectural decision and produced a more focused solution.

`/effort high` was most useful for the detailed test and verification work because it checked assumptions more carefully and produced clearer acceptance criteria.

The strongest final plan should combine:

- The owner-specific production design from the `ultrathink` response
- The testing and verification strategy from the `/effort high` response

## When I would skip planning

I would skip formal planning for a tiny, low-risk, easily reversible change when the affected file is obvious and a fast automated test can verify the result.

## Saved raw outputs

The complete Claude responses were also preserved locally in:

```text
Default:
~/.claude/plans/read-claude-md-and-architecture-2026-09-sunny-fern.md

Ultrathink:
~/.claude/plans/read-claude-md-architecture-2026-09-md-a-valiant-sifakis.md

High effort:
~/.claude/plans/read-claude-md-architecture-2026-09-md-a-clever-nova.md
```
