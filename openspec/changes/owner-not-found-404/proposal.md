## Why

A well-formed integer owner ID with no matching record is a client error, but `OwnerController` currently throws `IllegalArgumentException` for it, so `GET /owners/9999`, `GET /owners/9999/edit`, and `POST /owners/9999/edit` all return **500**. A 500 for a missing record misleads users, hides genuine faults in monitoring, and leaves this path untested.

## What Changes

- On exactly three routes — `GET /owners/{ownerId}`, `GET /owners/{ownerId}/edit`, `POST /owners/{ownerId}/edit` — a numeric `{ownerId}` with no matching owner produces **404** instead of 500. This route list is exhaustive for this change.
- The 404 status applies regardless of the request's `Accept` header. Only the HTML body is specified: the existing `error.html` template renders with its existing `error.404` message.
- On `POST /owners/{ownerId}/edit` the missing-owner check precedes form validation, so a missing owner yields 404 for a valid *or* an invalid submission, and the request performs no persistent write.
- Existing-owner behavior is unchanged: status, view name, model attributes, and redirect target for owner 1 stay exactly as they are today.
- Explicitly **not** changed: `GET /owners/abc` stays 400, `GET /owners?page=0` stays 500, `GET /oups` stays 500.
- No breaking changes. No new template, message key, translation, migration, dependency, service layer, or build-file change.

## Capabilities

### New Capabilities

- `owner-management`: HTTP behavior of the owner detail and owner edit routes served by `OwnerController`, including how a missing owner is reported and which neighbouring error classifications are deliberately left untouched.

### Modified Capabilities

None — `openspec/specs/` is currently empty, so this change introduces the first capability.

## Impact

- **Code**: `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java` (the two owner lookups: the `@ModelAttribute("owner") findOwner(...)` method and the duplicate lookup inside `showOwner(...)`), plus one new owner-package exception type. Changes stay inside the `owner` package; controllers keep calling `OwnerRepository` directly.
- **Tests**: one new class `OwnerNotFoundIntegrationTests`, following the full-context H2 precedent of `PetClinicIntegrationTests`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@AutoConfigureTestRestTemplate` and an injected `TestRestTemplate`, plus an injected `OwnerRepository` for the persistent-state invariant.
- **Tests (existing)**: `OwnerControllerTests.processUpdateOwnerFormSuccess` is strengthened with `redirectedUrl("/owners/1")` and `verify(this.owners).save(any(Owner.class))` so AC-5's redirect-and-save bullet is actually verified; its current assertions are kept and no other method changes. `CrashControllerIntegrationTests` stays unmodified and remains the existing verifier for `/oups` (AC-8).
- **Out of bounds**: no global `@ControllerAdvice` or other cross-cutting handler — `PetController` and `VisitController` production code, tests, and behavior must not change. No mapping of `IllegalArgumentException` as a class to 404, which would reclassify the `?page=0` pagination failure. `PetController` and `VisitController` non-modification is a scope guard here, not an owner-management requirement — their current missing-owner status is not frozen by this change's specs.
- **Not touched**: templates, `messages*.properties`, error-related application properties, `build.gradle`, `pom.xml`, logging/metrics/alerting behavior.
