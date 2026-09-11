## Context

See `proposal.md` — Why. Design-relevant facts observed in the current tree:

- `OwnerController` resolves an owner in **two** places: the `@ModelAttribute("owner") findOwner(Integer ownerId)` method (`OwnerController.java:65`), which serves `GET /owners/{ownerId}/edit` and `POST /owners/{ownerId}/edit`, and a second, duplicate `findById` inside `showOwner(int ownerId)` (`OwnerController.java:172`). Both throw `IllegalArgumentException`, which Spring resolves to 500.
- `@ModelAttribute` methods run during argument resolution, before data binding and before `@Valid` validation of the bound object. So on `POST /owners/{ownerId}/edit`, `findOwner` already throws before `BindingResult` is consulted and before `owners.save(...)` is reached.
- `PetController` and `VisitController` live in the **same** `owner` package and throw their own `IllegalArgumentException` for a missing owner (`PetController.java:70,84`, `VisitController.java:67`). Any handler keyed on `IllegalArgumentException`, or any handler with package- or application-wide scope, would silently reclassify them — which the specs forbid.
- `GET /owners?page=0` reaches `PageRequest.of(page - 1, 5)` (`OwnerController.java:135`) and raises `IllegalArgumentException` from inside the same controller. So even a handler scoped to `OwnerController` alone is unsafe if it is keyed on the exception *class* rather than on a distinct owner-not-found signal.
- `error.html` already switches on `${status}` and renders `#{error.404}` = `The requested page was not found.` (`src/main/resources/templates/error.html:12`, `messages.properties:50`). Nothing about the error page needs to change; it just needs to be reached with status 404.
- `PetClinicIntegrationTests` is the full-context H2 precedent: `@SpringBootTest(webEnvironment = RANDOM_PORT)` against the real datasource, with repositories injected. `CrashControllerIntegrationTests` is **not** a usable model here — its nested `@SpringBootApplication` excludes `DataSourceAutoConfiguration`, `DataSourceTransactionManagerAutoConfiguration`, and `HibernateJpaAutoConfiguration` (`CrashControllerIntegrationTests.java:95-99`), so it runs without a database and could not observe owner state. It is relevant to this change only as the existing, unmodified verifier for `/oups` (AC-8).
- `OwnerControllerTests.processUpdateOwnerFormSuccess` (`OwnerControllerTests.java:211-221`) asserts `is3xxRedirection()` and the view name `redirect:/owners/{ownerId}`, but not the expanded redirect URL and not that a save occurred. AC-5's third bullet is therefore under-verified today.

## Goals / Non-Goals

**Goals:**

- Turn owner-not-found into a 404 with a signal narrow enough that `?page=0` inside the same controller, and the pet/visit lookups inside the same package, cannot be caught by it.
- Keep the no-write invariant a property of *where* the check happens, not of an extra guard that a future edit could reorder past.
- Reach the existing error page through the framework's normal error dispatch, so no template, message key, or property changes.

**Non-Goals:**

- Removing the duplicate lookup in `showOwner(...)`, or unifying owner lookup across the three controllers in the `owner` package. Both are tempting here and both are out of scope.
- Specifying the JSON error body. Only its status is contracted.
- Changing the log level or stack trace emitted for a 404.
- Changing `PetController` or `VisitController` behavior, or specifying what they return for a missing owner. They are held out by a scope guard, not by a requirement.

## Decisions

### D1: Signal not-found with a dedicated exception type, not a broad category

Introduce one new exception class in the `owner` package, annotated `@ResponseStatus(HttpStatus.NOT_FOUND)`, and throw it from the two owner lookups in `OwnerController`. Nothing else throws it.

*Why:* the blast radius is bounded by the set of throw sites, which is two lines in one file. `Spring`'s `ResponseStatusExceptionResolver` then sets 404 and lets the standard error dispatch render `error.html` — no handler registration, no advice, no ordering question.

*Alternatives considered:*

- **`@ControllerAdvice` mapping `IllegalArgumentException` → 404.** Rejected: reclassifies `?page=0` (AC-7) and every pet/visit missing-owner path, and the specs forbid a cross-cutting handler outright.
- **`@ExceptionHandler(IllegalArgumentException.class)` local to `OwnerController`.** Still rejected: `?page=0` raises `IllegalArgumentException` *from inside `OwnerController`*, so the local scope does not save it. Rejecting this one is the non-obvious call; "scoped to the controller" reads safe and is not.
- **Throw `ResponseStatusException(HttpStatus.NOT_FOUND)` directly.** Workable and one class lighter, but it puts an HTTP type in the middle of a domain lookup and gives the two throw sites nothing to share. A named type also lets the new test assert behavior against a concept rather than a status literal.
- **Have the new type extend `IllegalArgumentException`** for source compatibility. Rejected: it invites exactly the class-keyed handler this design rules out, and nothing in the tree catches the current exception (verified: no test or production code references it outside the throw sites).

### D2: Keep the check in `@ModelAttribute`, and mirror it in `showOwner`

Change both existing throw sites; do not add a new check inside `processUpdateOwnerForm`.

*Why:* the no-write invariant (AC-3) then holds structurally — `findOwner` runs before binding, so a missing owner cannot reach validation or `save(...)`, for a valid or an invalid form alike. An explicit guard at the top of `processUpdateOwnerForm` would be equivalent today and fragile tomorrow. `showOwner` keeps its own lookup because removing the duplicate is out of scope; consistency between the two sites means neither can drift into a 500.

### D3: Verify end-to-end against a real H2 context, and close the AC-5 gap

Add `OwnerNotFoundIntegrationTests` in the `owner` test package, following `PetClinicIntegrationTests`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@AutoConfigureTestRestTemplate`, an injected `TestRestTemplate` for requests, and an injected `OwnerRepository` for the persistent-state invariant.

*Why:* `@WebMvcTest` resolves the exception but does not run the error dispatch, so it cannot prove the status the client sees, the rendered error page, or the `?page=0` and `/owners/abc` guards. The no-write invariant additionally needs a real datasource to count owners and re-read a control owner, which a mocked repository cannot give.

*Why not `CrashControllerIntegrationTests` as the model:* it deliberately runs without a database (see Context). Copying its shape would make the invariant assertions impossible. It stays untouched as the `/oups` verifier.

*AC-5:* `OwnerControllerTests` remains the right level for the existing-owner paths, but `processUpdateOwnerFormSuccess` is strengthened with `redirectedUrl("/owners/1")` and `verify(this.owners).save(any(Owner.class))`. Its existing assertions stay. No new imports are needed — `verify`, `any`, and the `MockMvcResultMatchers.*` wildcard that supplies `redirectedUrl` are already present. `showOwner` and `initUpdateOwnerForm` already assert the model depth AC-5 describes and stay as they are.

## Risks / Trade-offs

- **A future contributor "cleans up" by adding a global `IllegalArgumentException` handler** → the `?page=0` and `/oups` guard scenarios in the delta spec fail loudly, and the design decision above records why the local variant is unsafe too.
- **Pet and visit routes keep returning 500 for a missing owner, so the application is now inconsistent** → accepted and explicit, but held as a scope guard (a `git diff` check in tasks), not as an owner-management requirement. This change neither fixes nor ratifies their behavior, so a later change can revisit it without amending this capability's spec.
- **The duplicate lookup in `showOwner` means two places must be kept in sync** → both are changed together here, and the guard scenarios cover the `GET /owners/{ownerId}` route end to end.
- **`@ResponseStatus` sets the status but the response body still travels through the error dispatch**, whose detail (for example whether the exception message is echoed) depends on `spring.web.error.include-message` → mitigated by the spec rule that exception text and other development-only fields are never asserted; the test asserts only the two stable message strings and the two strings that must be absent.

## Migration Plan

Not applicable. No data migration, no API version change, no configuration change. A revert is a revert of the source commit; nothing persists between deploys.
