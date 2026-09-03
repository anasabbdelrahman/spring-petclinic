# Plan: Return HTTP 404 for Nonexistent Owners - 2026-09

## Objective

Change Spring PetClinic so requests containing an owner ID that does not exist return HTTP 404 instead of HTTP 500.

The existing `templates/error.html` page must continue to render HTML errors, and JSON clients must receive a JSON response with status 404.

This document is an implementation plan only. No production code or tests were modified while preparing it.

## Research handoff

This plan uses the findings recorded in:

- `CLAUDE.md`
- `architecture-2026-09.md`
- `planning-depth-2026-09.md`

The current behavior was established through code inspection. It has not yet been confirmed by executing the missing-owner request.

## Current behavior and cause

A request such as:

```http
GET /owners/9999
```

is expected to return HTTP 500.

Spring calls the following method before executing the mapped controller handler:

```text
OwnerController.findOwner(Integer ownerId)
```

Location:

```text
src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java
```

The method calls:

```java
this.owners.findById(ownerId)
```

When the repository returns `Optional.empty()`, the method throws a generic `IllegalArgumentException`.

The application has no custom exception handler that maps this exception to HTTP 404. Spring therefore treats it as an unhandled server error.

The existing template already supports HTTP 404:

```text
src/main/resources/templates/error.html
```

It contains a `th:case="404"` branch using the existing `error.404` message key. No template or translation changes are required.

## Selected design

Create an owner-specific exception:

```text
OwnerNotFoundException
```

Annotate it with:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
```

The exception belongs in the `owner` package because:

- It represents an owner-specific condition.
- The repository is organized primarily by feature.
- `OwnerController` and `OwnerRepository` are already in this package.
- No cross-package import is required in `OwnerController`.

### Alternatives considered

#### Inline ResponseStatusException

This would correctly produce HTTP 404, but it would repeat the status and message construction at every owner lookup.

#### ControllerAdvice and ExceptionHandler

This could support the required behavior but would introduce a global component and additional error-response handling for a single owner-specific condition.

#### Selected approach

A custom `OwnerNotFoundException` with `@ResponseStatus` is the smallest design that:

- Gives the condition clear HTTP semantics
- Avoids matching every `IllegalArgumentException`
- Centralizes the owner-not-found message
- Uses Spring MVC's existing exception-resolution behavior
- Preserves the existing error-page flow

## Scope

### In scope

- Missing-owner handling in `OwnerController`
- HTTP 404 responses for all `OwnerController` routes containing `ownerId`
- Controller-level tests
- HTML integration testing
- JSON integration testing
- Regression verification

### Out of scope

- Missing pets
- Missing visits
- `PetController`
- `VisitController`
- Pagination behavior for `?page=0`
- Removing the duplicate owner lookup from `showOwner()`
- Changing `error.html`
- Adding or changing translation keys
- Enabling exception messages in production error responses

## Files to create

### Production exception

```text
src/main/java/org/springframework/samples/petclinic/owner/OwnerNotFoundException.java
```

Purpose:

- Represent an owner ID that does not exist
- Map the condition to HTTP 404
- Construct a consistent internal exception message

### Exception tests

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundExceptionTests.java
```

Purpose:

- Verify that `OwnerNotFoundException` maps to HTTP 404
- Verify that its message includes the owner ID

### Integration tests

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java
```

Purpose:

- Verify the full HTML 404 response
- Verify the full JSON 404 response

## Files to modify

### OwnerController

```text
src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java
```

Methods:

- `findOwner(Integer ownerId)`
- `showOwner(int ownerId)`

Replace the two owner-not-found `IllegalArgumentException` instances with `OwnerNotFoundException`.

### OwnerControllerTests

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java
```

Add controller-level tests for all three routes containing an owner ID.

## Implementation steps

### Step 1: Verify the existing baseline

Before making changes, run the existing owner controller tests:

```bash
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests"
```

Expected result:

```text
BUILD SUCCESSFUL
```

This confirms the baseline before implementation.

### Step 2: Create OwnerNotFoundException

Create:

```text
src/main/java/org/springframework/samples/petclinic/owner/OwnerNotFoundException.java
```

Use the repository's standard Apache license header.

The class should have this behavior:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OwnerNotFoundException extends RuntimeException {

	public OwnerNotFoundException(int ownerId) {
		super("Owner not found with id: " + ownerId);
	}

}
```

Required imports:

```java
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
```

Do not add a `reason` to `@ResponseStatus`. The public response should rely on the existing translated HTTP 404 message rather than exposing the internal exception message.

### Step 3: Update OwnerController

In `OwnerController.findOwner(Integer ownerId)`, replace the existing `IllegalArgumentException` with:

```java
.orElseThrow(() -> new OwnerNotFoundException(ownerId));
```

In `OwnerController.showOwner(int ownerId)`, replace the second `IllegalArgumentException` with:

```java
.orElseThrow(() -> new OwnerNotFoundException(ownerId));
```

Keep both lookup sites in this change.

The second lookup in `showOwner()` is redundant, but removing it is a separate refactoring with a different scope.

### Step 4: Add OwnerNotFoundExceptionTests

Create:

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundExceptionTests.java
```

Add:

#### mapsToHttpNotFound

Use `AnnotatedElementUtils.findMergedAnnotation()` to read the `ResponseStatus` annotation.

Assert that:

- The annotation exists
- Its value is `HttpStatus.NOT_FOUND`

#### messageNamesTheOwnerId

Create:

```java
new OwnerNotFoundException(9999)
```

Assert that the message:

- Starts with `Owner not found with id:`
- Contains `9999`

This tests the internal message where its behavior is deterministic. Do not require the message to appear in the rendered HTTP response.

### Step 5: Add OwnerControllerTests

In:

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java
```

Add:

```java
private static final int UNKNOWN_OWNER_ID = 9999;
```

For each new test, explicitly stub:

```java
given(this.owners.findById(UNKNOWN_OWNER_ID)).willReturn(Optional.empty());
```

Add these tests:

#### showOwnerWithUnknownIdReturnsNotFound

Request:

```text
GET /owners/9999
```

Assertion:

```java
status().isNotFound()
```

#### initUpdateOwnerFormWithUnknownIdReturnsNotFound

Request:

```text
GET /owners/9999/edit
```

Assertion:

```java
status().isNotFound()
```

This test proves that the `@ModelAttribute` method produces the 404 because `initUpdateOwnerForm()` does not query the repository itself.

#### processUpdateOwnerFormWithUnknownIdReturnsNotFound

Request:

```text
POST /owners/9999/edit
```

Use the same valid form parameters as the existing successful update test.

Assertion:

```java
status().isNotFound()
```

Do not assert a view name, model, or rendered body in these controller tests. MockMvc verifies the resolved HTTP status, while the integration tests verify the error page.

### Step 6: Add OwnerNotFoundIntegrationTests

Create:

```text
src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java
```

Follow the existing pattern in:

```text
src/test/java/org/springframework/samples/petclinic/system/CrashControllerIntegrationTests.java
```

Use:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
```

Inject:

- The random server port
- `TestRestTemplate`

Use the real Spring Boot application and the default H2 database. Do not create a nested test application because the owner repository and database are required.

Do not set `spring.web.error.include-message`. The integration tests should verify production-equivalent public behavior.

Add:

#### unknownOwnerRendersNotFoundErrorPage

Send:

```http
GET /owners/9999
Accept: text/html
```

Assert:

- Status is `HttpStatus.NOT_FOUND`
- The body is not null
- The body contains `Something happened...`
- The body contains `The requested page was not found.`
- The body does not contain `An internal server error occurred.`
- The body does not contain `An unexpected error occurred.`
- The body does not contain `Whitelabel Error Page`

Do not assert that the exception's dynamic message appears in the response.

#### unknownOwnerReturnsNotFoundJson

Send:

```http
GET /owners/9999
Accept: application/json
```

Read the response as:

```java
ParameterizedTypeReference<Map<String, Object>>
```

Assert:

- Status is `HttpStatus.NOT_FOUND`
- `status` equals `404`
- `error` equals `Not Found`
- `path` equals `/owners/9999`
- `timestamp` exists

### Step 7: Format the changes

Run:

```bash
./gradlew format
```

If the formatting task is blocked by the repository's checkstyle dependencies, run:

```bash
./gradlew formatMain formatTest \
  -x checkstyleMain \
  -x checkstyleTest \
  -x checkstyleNohttp
```

Then validate formatting and nohttp rules:

```bash
./gradlew checkFormatMain checkFormatTest checkstyleMain checkstyleTest checkstyleNohttp
```

### Step 8: Run focused tests

Run the exception tests:

```bash
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerNotFoundExceptionTests"
```

Run the controller tests:

```bash
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests"
```

Run the most important controller method independently:

```bash
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerControllerTests.initUpdateOwnerFormWithUnknownIdReturnsNotFound"
```

Run the integration tests:

```bash
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerNotFoundIntegrationTests"
```

To force Gradle to rerun an unchanged test task:

```bash
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerNotFoundIntegrationTests" --rerun
```

### Step 9: Run regression tests

Run:

```bash
./gradlew test \
  --tests "org.springframework.samples.petclinic.system.CrashControllerIntegrationTests" \
  --tests "org.springframework.samples.petclinic.service.ClinicServiceTests" \
  --tests "org.springframework.samples.petclinic.system.I18nPropertiesSyncTest" \
  --tests "org.springframework.samples.petclinic.PetClinicIntegrationTests"
```

These tests verify that:

- `/oups` still returns HTTP 500
- Existing owner requests still succeed
- Repository behavior remains unchanged
- Internationalization checks still pass

### Step 10: Run the complete build

Run:

```bash
./gradlew build
```

The new owner tests use H2 and do not require Docker.

The MySQL and PostgreSQL tests are designed to skip when Docker is unavailable. Check:

```text
build/reports/tests/test/index.html
```

to confirm their status on the implementation machine.

### Step 11: Perform a manual smoke test

Start the application:

```bash
./gradlew bootRun
```

From another terminal, verify:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/owners/9999
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/owners/9999/edit
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/owners/1
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/oups
```

Expected statuses:

```text
/owners/9999       -> 404
/owners/9999/edit  -> 404
/owners/1          -> 200
/oups              -> 500
```

Check the HTML error message:

```bash
curl -s http://localhost:8080/owners/9999 |
  grep -F "The requested page was not found."
```

Check JSON content negotiation:

```bash
curl -s \
  -H "Accept: application/json" \
  http://localhost:8080/owners/9999
```

Expected JSON fields include:

```json
{
  "status": 404,
  "error": "Not Found",
  "path": "/owners/9999"
}
```

Stop the application after the smoke test.

### Step 12: Review the Git changes

Run:

```bash
git status --short
git diff --stat
git diff
```

Confirm that the change contains only:

```text
src/main/java/org/springframework/samples/petclinic/owner/OwnerNotFoundException.java
src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java
src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundExceptionTests.java
src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java
src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java
```

Confirm that these remain unchanged:

- `templates/error.html`
- `messages*.properties`
- `application*.properties`
- `PetController.java`
- `VisitController.java`
- `pom.xml`
- `build.gradle`

## Risks and mitigations

### ModelAttribute execution order

The missing-owner exception occurs in `findOwner()` before the mapped handler executes.

Mitigation:

- Change the exception in `findOwner()`
- Test the edit-form route, whose handler does not query the repository

### Error-page rendering

The controller tests verify only HTTP status. They do not execute the container's complete error dispatch.

Mitigation:

- Use a full integration test to verify the existing HTML error page
- Test JSON content negotiation separately

### Exception-message exposure

The exception's dynamic message is not guaranteed to appear in the rendered response.

Mitigation:

- Verify the internal message in the exception unit test
- Do not assert it in HTML or JSON integration tests
- Leave production error-message configuration unchanged

### Existing search behavior

An owner search with no matches currently returns HTTP 200 with a field-level `notFound` error.

Mitigation:

- Keep `processFindFormNoOwnersFound()` unchanged and passing
- Do not convert an empty search result into HTTP 404

### Non-numeric owner IDs

`GET /owners/abc` is handled separately as an invalid path-variable type.

Mitigation:

- Leave the behavior unchanged
- Do not include it in this owner-not-found change

### Missing pets and visits

Other controllers contain their own missing-owner and missing-pet behavior.

Mitigation:

- Keep `PetController` and `VisitController` outside this change
- Document them as possible follow-up work

### Duplicate owner query

`showOwner()` performs a second owner lookup after `findOwner()` has already loaded the owner.

Mitigation:

- Keep it unchanged to avoid combining an error-handling fix with a separate refactoring
- Track removal as follow-up technical debt

## Acceptance criteria

1. `GET /owners/9999` returns HTTP 404.
2. `GET /owners/9999/edit` returns HTTP 404.
3. `POST /owners/9999/edit` returns HTTP 404.
4. The HTML response uses the existing `error.html` page.
5. The HTML response displays `The requested page was not found.`
6. The HTML response does not display an HTTP 500 or generic-error message.
7. A request with `Accept: application/json` returns JSON containing status 404.
8. `OwnerNotFoundException` is annotated with `@ResponseStatus(HttpStatus.NOT_FOUND)`.
9. The exception's internal message contains the missing owner ID.
10. Existing valid-owner requests continue returning HTTP 200.
11. An unmatched owner search continues returning HTTP 200 with a field error.
12. `/oups` continues returning HTTP 500.
13. The owner controller, integration, i18n, and service tests pass.
14. `./gradlew build` succeeds.
15. No templates, translations, application properties, or build files are changed.

## Colleague verification

> Could you execute this plan without asking me any clarifying questions? If not, which step, decision, or expected result is unclear?

**Reviewer:** To be completed  
**Questions asked:** To be completed  
**Revisions made:** To be completed  
**Final result:** To be completed
