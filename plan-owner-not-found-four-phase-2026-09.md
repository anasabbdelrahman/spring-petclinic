# Implementation Plan: HTTP 404 for a Missing Owner (Four-Phase Workflow) - 2026-09

Phase: Plan. Authority: `spec-owner-not-found-2026-09.md` (approved in the Specify phase).
No production code, test, configuration, or specification file was modified while writing this
plan. The task list is deliberately not included; it belongs to the Tasks phase.

Relationship to earlier artifacts: this plan supersedes `plan-owner-not-found-handling-2026-09.md`,
which predates the approved specification. Section 3 records where the two differ and why.

Amended once, after Task 1 and before Task 2, by the human review gate recorded in
`review-gate-2026-09.md`: AC-5's verification (sections 4, 7, 8 risk 2, 9.4, 11, 12, 13), the
demotion of the sibling-controller probes to an informational scope-leak check (sections 8 risk 6,
10.5, 11), and the section 12 contradiction over the specification's `Status:` line. No change to the
selected approach, and no production or test file was touched by the gate.

## 1. Current behavior and verified root cause

Observed by running the application on this branch (`./gradlew bootRun`, default H2 profile,
port 8098, 2026-09-10) and reading the server log:

| Request | Status today | Resolved from |
| --- | --- | --- |
| `GET /owners/1` | 200 | `owners/ownerDetails` |
| `GET /owners/1/edit` | 200 | `owners/createOrUpdateOwnerForm` |
| `GET /owners/9999` (`text/html` and `application/json`) | 500 | `IllegalArgumentException: Owner not found with id: 9999...` |
| `GET /owners/9999/edit` | 500 | same exception |
| `POST /owners/9999/edit` (valid form and blank `firstName`) | 500 | same exception |
| `GET /owners/abc` | 400 | `MethodArgumentTypeMismatchException` |
| `GET /owners?page=0` | 500 | `IllegalArgumentException: Page index must not be less than zero` |
| `GET /oups` | 500 | `RuntimeException: Expected: controller used to showcase...` |
| `DELETE /owners/9999` | 405 | `HttpRequestMethodNotSupportedException` |

Root cause: `OwnerController` reports a missing owner as `IllegalArgumentException`, which Spring MVC
has no mapping for, so the servlet container's error dispatch renders `error.html` with `status=500`.

Two lookup sites throw it:

- `OwnerController.findOwner(Integer)` at lines 64-70, the `@ModelAttribute("owner")` method. It runs
  before **every** handler method in the controller, so it is the site that actually produces the 500
  on all three in-scope routes. The logged message ends with `and the owner exists in the database.`,
  which is this method's wording - direct evidence that it throws first.
- `OwnerController.showOwner(int)` at lines 172-174, a second lookup of the same owner. Because
  `findOwner` has already thrown for a missing owner, **this branch is unreachable for the
  missing-owner case**; it only ever returns a present `Optional`.

Two facts make the fix small. First, no exception handling of any kind exists in `src/main/java`:
no `@ControllerAdvice`, `@ExceptionHandler`, `@ResponseStatus`, or `ResponseStatusException`.
Second, the 404 presentation already exists: `templates/error.html` has a `th:case="404"` branch bound
to `#{error.404}`, and `messages/messages.properties:50` defines
`error.404=The requested page was not found.` Verified live: `GET /no-such-page` with
`Accept: text/html, Accept-Language: en` returns 404 and a body containing `Something happened...`
and `The requested page was not found.`, and containing neither `An internal server error occurred.`
nor `Whitelabel Error Page`. `GET /oups` shows the same page with the 500 branch. Together these
prove that a status raised through Spring's error-response path renders `error.html` with the
status-specific message, which is what AC-4 requires.

## 2. Selected implementation approach

Introduce one owner-specific exception in the `owner` package and throw it from both lookup sites.

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
class OwnerNotFoundException extends RuntimeException {

	OwnerNotFoundException(int ownerId) {
		super("Owner not found with id: " + ownerId);
	}

}
```

`ResponseStatusExceptionResolver` reads the annotation and calls `HttpServletResponse.sendError(404)`,
the container dispatches to `/error`, and `BasicErrorController` renders `error.html` with
`status=404`. That is the same mechanism already observed for `/no-such-page` and `/oups`.

Why this is preferred:

1. **It cannot widen.** The 404 is bound to a type thrown only where `findById` returned empty.
   `GET /owners?page=0` throws a plain `IllegalArgumentException` from inside `processFindForm`, so it
   is untouched and AC-7 holds by construction rather than by test luck.
2. **It satisfies constraint 4 without a handler.** No advice, no `@ExceptionHandler`, nothing
   cross-cutting. `PetController` and `VisitController` keep their own lookups and their 500s.
   Controllers still call `OwnerRepository` directly; no service layer appears.
3. **Ordering is free.** The check already lives in the `@ModelAttribute` method, which Spring MVC
   invokes before binding and validation of the `@Valid Owner` argument. FR-4 is met by the existing
   execution order, not by new sequencing code - and no `save` is reached, so AC-3 holds.
4. **The two sites cannot drift.** Status and message are defined once, so the unreachable
   `showOwner` branch cannot silently reintroduce a 500 if a future refactor changes lookup order.
5. **Zero import churn in `OwnerController`.** The exception is in the same package, and
   `IllegalArgumentException` needed no import, so the controller diff is exactly two `orElseThrow`
   lambdas.
6. **FR-6 holds.** No template, message key, translation, migration, dependency, service layer, or
   build change. One new class is not in FR-6's list, and it is the smallest unit that gives the
   condition explicit HTTP semantics.

The annotation carries **no** `reason` attribute. With a reason, the resolver would resolve it against
the `MessageSource` and pass it to `sendError(status, reason)`, putting non-contractual text into the
servlet error message for no benefit; constraint 2 says exception text is not part of the contract.
The exception message keeps the owner ID for logs only and is asserted nowhere.

## 3. Alternatives considered and rejected

| Alternative | Why rejected |
| --- | --- |
| `@ExceptionHandler(IllegalArgumentException.class)` on `OwnerController` | Fatal. Verified that `?page=0` throws the same type from the same controller, so it would become 404 and break AC-7 and constraint 3. |
| `@ControllerAdvice` handling `IllegalArgumentException` globally | Rejected for the same reclassification reason as the row above, one scope wider: it would convert pagination failures and every other unrelated `IllegalArgumentException` into 404, breaking AC-7, and it would additionally change `PetController` and `VisitController`, which throw that same type for a missing owner or pet - behavior section 6 puts out of scope and constraint 4 forbids changing. |
| `@ControllerAdvice` handling `OwnerNotFoundException` globally | Would **not** change `PetController` or `VisitController`, because neither throws that exception, so constraint 4 is not at stake. Rejected because cross-cutting infrastructure is unnecessary for a single owner-specific exception: the selected `@ResponseStatus` exception already expresses the HTTP status, so no handler needs to exist at all. |
| Inline `ResponseStatusException(HttpStatus.NOT_FOUND, ...)` at both sites | Correct and one file smaller, but it duplicates status plus message at two sites, names nothing, and needs two new imports in the controller. Kept as the fallback if a reviewer objects to the new class; behaviorally identical. |
| Inspecting the `IllegalArgumentException` message for `Owner not found` | Couples control flow to text that constraint 2 declares non-contractual. Fragile. |
| Returning a 404 `ResponseEntity` or `ModelAndView` from each handler | Invasive: `initUpdateOwnerForm()` takes no arguments today, and the POST binds onto the object `findOwner` returns, so a null-owner path would break binding and move the check after binding, violating FR-4. It would also have to reproduce error-page rendering by hand. |
| Changing `spring.web.error.*` or `spring.mvc.throw-exception-if-no-handler-found` | Section 6 puts error-related application properties out of scope, and neither affects a controller-thrown exception. |
| A new `OwnerNotFoundExceptionTests` unit test (present in the superseded plan) | Asserts the annotation and the exception message, both implementation detail. The integration tests already prove the observable 404 on all three routes, so the class would add no failing witness for any acceptance criterion. Note the reasoning: section 8 of the specification is a coverage floor, not a ceiling - a test is added when a criterion clause has no witness, which is not the case here. |
| New missing-owner MockMvc cases added to `OwnerControllerTests` (present in the superseded plan) | MockMvc resolves the status without running the container error dispatch that AC-4 needs, so a missing-owner case there would assert less than the integration test does. This rejects *new missing-owner cases only*. It does not bar Task 2 from adding the two AC-5 assertions to the existing `processUpdateOwnerFormSuccess` (section 7), which need no error dispatch. |
| Asserting JSON body fields such as `error` or `path` (present in the superseded plan) | Section 6 puts JSON body shape out of scope; AC-1 specifies status only for the JSON request. |

## 4. Files created and modified

Created:

1. `src/main/java/org/springframework/samples/petclinic/owner/OwnerNotFoundException.java`
2. `src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java`

Modified:

3. `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java` - two `orElseThrow`
   lambdas only; no signature, mapping, annotation, or import changes.
4. `src/test/java/org/springframework/samples/petclinic/owner/OwnerControllerTests.java` - in Task 2
   only, and only two added assertions inside the existing `processUpdateOwnerFormSuccess`:
   `redirectedUrl("/owners/1")` and `verify(this.owners).save(any(Owner.class))`. No existing
   assertion, fixture, mock stub, import, or other method changes. The required static imports for
   `redirectedUrl`, `verify`, and `any` are already present in that file.

Nothing else. Four files across the feature - items 1 to 3 in Task 1, item 4 in Task 2 - plus this
plan document, which is a Plan-phase artifact and not part of the implementation.

## 5. Classes, methods, annotations and fixtures involved

Production:

- `OwnerNotFoundException` - package-private, `extends RuntimeException`, annotated
  `@ResponseStatus(HttpStatus.NOT_FOUND)`; imports `org.springframework.http.HttpStatus` and
  `org.springframework.web.bind.annotation.ResponseStatus`; Apache 2.0 header; short Javadoc in the
  style of `PetValidator`. No `serialVersionUID` - it is never serialized and no configured gate
  requires one.
- `OwnerController.findOwner(Integer ownerId)` - `@ModelAttribute("owner")`,
  `@PathVariable(name = "ownerId", required = false)`. The exception is constructed only in the
  `ownerId != null` branch, so unboxing to the `int` constructor parameter is safe.
- `OwnerController.showOwner(int ownerId)` - `@GetMapping("/owners/{ownerId}")`, returns
  `ModelAndView("owners/ownerDetails")`. Its duplicate lookup stays; only the exception type changes.
- Untouched: `initUpdateOwnerForm()`, `processUpdateOwnerForm(...)`, `processCreationForm(...)`,
  `processFindForm(...)`, `setAllowedFields(...)`, `OwnerRepository`, `Owner`, `PetValidator`,
  `PetTypeFormatter`.

Test:

- `OwnerNotFoundIntegrationTests` in package `org.springframework.samples.petclinic.owner`.
  Annotations: `@SpringBootTest(webEnvironment = RANDOM_PORT)` and
  `@AutoConfigureTestRestTemplate`. Injected: `@LocalServerPort int port`,
  `@Autowired TestRestTemplate rest`, `@Autowired OwnerRepository owners`.
  Imports follow `CrashControllerIntegrationTests` for
  `org.springframework.boot.resttestclient.TestRestTemplate` and
  `...resttestclient.autoconfigure.AutoConfigureTestRestTemplate`, and
  `PetClinicIntegrationTests` for `org.springframework.boot.test.web.server.LocalServerPort`.
- No `properties = {...}`: `spring.web.error.include-message` stays at its default so the assertions
  reflect production behavior. No `@Transactional` - the server runs on another thread, so
  transactional rollback would neither cover nor detect a real write and would make the section 7
  invariant meaningless. No `@DirtiesContext`, matching the two existing integration classes.
- Fixtures: `UNKNOWN_OWNER_ID = 9999` (absent - `db/h2/data.sql` seeds exactly ten owners) and
  `EXISTING_OWNER_ID = 1` (George Franklin) as the control owner. Form fixture reuses the field names
  and values of `OwnerControllerTests.processUpdateOwnerFormSuccess`: `firstName=Joe`,
  `lastName=Bloggs`, `address=123 Caramel Street`, `city=London`, `telephone=1616291589`, sent as
  `APPLICATION_FORM_URLENCODED` in a `LinkedMultiValueMap`. The invalid variant sets `firstName` to the
  empty string, which is genuinely invalid because `Person.firstName` is `@NotBlank`.
- URLs are absolute, `"http://localhost:" + this.port + ...`, as in `CrashControllerIntegrationTests`;
  that form is proven to pass nohttp in this repository.
- Owner-1 snapshots read scalar fields only (`getFirstName`, `getLastName`, `getAddress`, `getCity`,
  `getTelephone`). `Owner.pets` is `FetchType.EAGER`, so no lazy-loading hazard exists either way.

## 6. Ordered implementation steps

**Step 1 - baseline.** Run `/function-summary` on
`src/main/java/.../owner/OwnerController.java:findOwner` and `:showOwner` (harness pre-change
comprehension lever), then confirm the tree is clean and the existing suites pass:
`./mvnw test -Dtest='OwnerControllerTests,PetControllerTests,VisitControllerTests,CrashControllerIntegrationTests'`.

**Step 2 - create the exception.** Add
`src/main/java/org/springframework/samples/petclinic/owner/OwnerNotFoundException.java` exactly as
sketched in section 2, with the repository's Apache 2.0 header, tabs at width 4, LF endings, and a
final newline.

**Step 3 - `findOwner`.** In `OwnerController` lines 64-70, replace the `IllegalArgumentException`
lambda with `.orElseThrow(() -> new OwnerNotFoundException(ownerId))`. Keep the ternary, the
`@ModelAttribute("owner")` annotation, and the `required = false` path variable unchanged.

**Step 4 - `showOwner`.** In `OwnerController` lines 172-174, replace the second
`IllegalArgumentException` lambda with `.orElseThrow(() -> new OwnerNotFoundException(ownerId))`.
Do not delete the duplicate lookup: section 6 of the specification puts that refactor out of scope.
Expect this line to show as uncovered - it is unreachable while `findOwner` throws first, and it is
changed only so the two sites cannot diverge.

**Step 5 - formatting and style gates.** `./mvnw spring-javaformat:apply`, then
`./mvnw validate` and `./gradlew checkFormatMain checkFormatTest checkstyleMain checkstyleTest checkstyleNohttp`.
Do not hand-order imports; let the formatter decide.

**Step 6 - early AC-4 checkpoint, before writing the tests.** This is the assertion with the most
framework behavior behind it, so prove it by hand first. Start `./gradlew bootRun`, then run the
section 10 commands for `/owners/9999` and for `/owners/9999/edit`. Two things must hold:

- `GET /owners/9999` returns 404 **and** a non-empty body containing `Something happened...` and
  `The requested page was not found.`, that is, the existing 404 error page. If the status is 404 but
  the body is empty, the error dispatch did not run; stop and re-check that the resolver reached
  `sendError` before writing any test around it.
- `GET /owners/9999/edit` returns 404. This is the more informative of the two probes:
  `initUpdateOwnerForm()` takes no arguments and never queries the repository, so a 404 on that route
  can only come from the class-level `@ModelAttribute("owner")` method. It confirms that `findOwner` is
  the effective missing-owner path for every route in the controller, which is the assumption the whole
  test class rests on.

**Step 7 - create the integration test.** Add
`src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java` with
exactly the six methods named in section 8 of the specification:
`unknownOwnerDetailsReturnsNotFound` (both `Accept` headers, status only),
`unknownOwnerEditFormReturnsNotFound`,
`unknownOwnerUpdateReturnsNotFoundAndChangesNoData` (valid then invalid body; snapshot
`owners.count()` and owner 1's scalars before, assert equality plus `owners.findById(9999)` empty after
each), `unknownOwnerRendersEnglishNotFoundPage` (`Accept: text/html`, `Accept-Language: en`; two
positive and two negative body assertions), `nonNumericOwnerIdReturnsBadRequest`, and
`paginationErrorIsNotTreatedAsNotFound`. Use `rest.exchange(...)`, which does not throw on 4xx/5xx.

**Step 8 - focused tests.** Section 9, commands 3 and 4.

**Step 9 - regression tests.** Section 9, command 5.

**Step 10 - both full builds.** Section 9, commands 6 and 7.

**Step 11 - manual verification.** Section 10 in full, including the unchanged-boundary probes.

**Step 12 - diff review.** `git status --short`, `git diff --check`, `git diff --stat`, confirm the diff
matches what section 4 predicts for the task in hand - three files in Task 1, the two test files
in Task 2 - then stage and run `/review-changes` per `HARNESS.md` before committing.

## 7. Acceptance criterion to verification mapping

| AC | Verified by | Assertion | Command |
| --- | --- | --- | --- |
| AC-1 | `OwnerNotFoundIntegrationTests.unknownOwnerDetailsReturnsNotFound` | `NOT_FOUND` for `GET /owners/9999` with `Accept: text/html` and again with `Accept: application/json`; no body assertion on the JSON call | 9.3 |
| AC-2 | `OwnerNotFoundIntegrationTests.unknownOwnerEditFormReturnsNotFound` | `NOT_FOUND` for `GET /owners/9999/edit` | 9.3 |
| AC-3 | `OwnerNotFoundIntegrationTests.unknownOwnerUpdateReturnsNotFoundAndChangesNoData` | `NOT_FOUND` for both POST bodies; after each: `owners.count()` unchanged, `owners.findById(9999)` empty, owner 1's five scalar fields unchanged | 9.3 |
| AC-4 | `OwnerNotFoundIntegrationTests.unknownOwnerRendersEnglishNotFoundPage` | body contains `Something happened...` and `The requested page was not found.`; contains neither `An internal server error occurred.` nor `Whitelabel Error Page` | 9.3, plus manual 10.3 |
| AC-5 | `OwnerControllerTests.showOwner` and `.initUpdateOwnerForm` - existing, unchanged; `.processUpdateOwnerFormSuccess` - existing, strengthened in Task 2 | `isOk()` plus view names and the `owner` model attribute, including the repository-returned owner's pets and their visits, for the GETs (already present at `OwnerControllerTests.java:199-209` and `:245-257`). For the POST, the existing `is3xxRedirection()` and `view().name("redirect:/owners/{ownerId}")` at `:211-221`, **plus** `redirectedUrl("/owners/1")` and `verify(this.owners).save(any(Owner.class))` added in Task 2. Rationale: the existing two assertions check the un-expanded view-name template and leave the save to a mocked repository, so AC-5's redirect-target and "the owner is saved" clauses had no assertion that would fail if either regressed - deleting `OwnerController.java:157` left the whole suite green | 9.4 |
| AC-6 | `OwnerNotFoundIntegrationTests.nonNumericOwnerIdReturnsBadRequest` | `BAD_REQUEST` for `GET /owners/abc` | 9.3 |
| AC-7 | `OwnerNotFoundIntegrationTests.paginationErrorIsNotTreatedAsNotFound` | `INTERNAL_SERVER_ERROR` for `GET /owners?page=0`, that is, not `NOT_FOUND` | 9.3 |
| AC-8 | `CrashControllerIntegrationTests.triggerExceptionHtml` - existing, unmodified | `INTERNAL_SERVER_ERROR` for `GET /oups` | 9.5 |

Section 7 of the specification (error-case invariant) is covered by the AC-3 row.

## 8. Risks and regression boundaries

1. **Broad `IllegalArgumentException` handling.** The dominant risk, and the reason for a dedicated
   type. `?page=0` and owner-not-found are the *same* exception class today, differing only in
   message, so any handler keyed on `IllegalArgumentException` inside `OwnerController` reclassifies
   pagination failures. Boundary: `GET /owners?page=0` stays 500 (AC-7). Review check: the diff
   contains no `@ExceptionHandler`, no `@ControllerAdvice`, and no `catch`.
2. **Existing-owner behavior.** Changing only the exception type inside `orElseThrow` cannot alter the
   present-`Optional` path. Boundary: AC-5 via `OwnerControllerTests`. All three AC-5 methods keep
   every existing assertion; Task 2 only *adds* two assertions to `processUpdateOwnerFormSuccess`. If
   an existing assertion, the `george()` fixture, or a mock stub has to change to keep those methods
   green, the approach is wrong, not the test.
3. **Validation ordering on POST.** Relies on Spring MVC invoking `@ModelAttribute` methods before
   binding and validating the `@Valid Owner` argument. The invalid-form half of AC-3 is the guard: if
   ordering were reversed, that request would return 200 with field errors instead of 404.
4. **Persistent-state safety.** `this.owners.save(...)` is only reachable after `findOwner` returns, so
   a 404 cannot write. AC-3 asserts it positively rather than by inspection. No `@Transactional` on the
   test, so a real write would be observed rather than rolled back.
5. **HTML versus JSON.** The 404 comes from the status, not from content negotiation, so both
   representations get it; only the HTML body is asserted. Deliberate omission: no assertion on the
   JSON payload's `error`, `message`, `path`, or `timestamp` fields (section 6, out of scope).
6. **`PetController` and `VisitController` stay out of scope.** They have their own lookups
   (`PetController:67-70` and `:83-84`, `VisitController:66-67`) and still throw
   `IllegalArgumentException`, so `/owners/9999/pets/new`, `/owners/9999/pets/1/edit`, and
   `/owners/9999/pets/1/visits/new` return 500 today - each confirmed by hand on this branch. Per the
   approved Specify outcome, no acceptance criterion freezes those statuses, because doing so would
   pull explicitly out-of-scope behavior into the contract. They are instead protected structurally
   (no cross-cutting handler) and observed once by the informational scope-leak check in section 10.5.
   That check looks for one thing only: a 404, which would mean this feature leaked across
   controllers. Their exact status is out of scope; a non-404 change there is recorded, not failed.
7. **Error-page rendering depends on `sendError` triggering the container's error dispatch.** Mitigated
   by the step 6 checkpoint before any test depends on it. Supporting evidence already gathered:
   `/no-such-page` renders the 404 branch and `/oups` renders the 500 branch in this application.
8. **`messages_en.properties` has no `error.404` key.** Resolution falls back to the base bundle, which
   the live `Accept-Language: en` probe confirms. Do not "fix" it - constraint 1 forbids message-file
   edits, and `I18nPropertiesSyncTest` currently passes.
9. **Log noise changes.** A resolved 404 no longer produces the Tomcat ERROR stack trace. Section 6 puts
   logging out of scope: expected side effect, asserted nowhere.
10. **A second `@SpringBootTest` context.** The new class's context key differs from
    `PetClinicIntegrationTests`, so the suite starts one more context (a few seconds). Accepted;
    section 8 of the specification requires the full-context harness.
11. **Native image and AOT.** No new resource, so `PetClinicRuntimeHints` needs no change. The new test
    follows the two existing integration classes and adds no `@DisabledInNativeImage` or
    `@DisabledInAotMode`.

## 9. Automated verification commands

```bash
# 9.1 format and style (Maven primary)
./mvnw spring-javaformat:apply
./mvnw validate

# 9.2 style gates (Gradle)
./gradlew checkFormatMain checkFormatTest checkstyleMain checkstyleTest checkstyleNohttp

# 9.3 the new test class
./mvnw test -Dtest=OwnerNotFoundIntegrationTests
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerNotFoundIntegrationTests" --rerun

# 9.4 AC-5. Existing methods unchanged; in Task 2 this class gains two assertions inside
# processUpdateOwnerFormSuccess and nothing else.
./mvnw test -Dtest=OwnerControllerTests

# 9.5 regression set, including AC-8
./mvnw test -Dtest='CrashControllerIntegrationTests,PetControllerTests,VisitControllerTests,ClinicServiceTests,I18nPropertiesSyncTest,PetClinicIntegrationTests,OwnerTests,ValidatorTests'

# 9.6 full Maven build
./mvnw verify

# 9.7 full Gradle build
./gradlew build
```

`MySqlIntegrationTests` and `PostgresIntegrationTests` need a Docker daemon; if Docker is absent, note
which tests were skipped rather than reporting an unqualified green. `spring-boot-docker-compose` is on
the test classpath, but `spring.docker.compose.skip.in-tests` defaults to true - proven in-repo by
`PostgresIntegrationTests` having to set it to `false` - so the new test needs no Docker.

## 10. Manual verification steps

```bash
# 10.1 start the app on a spare port
./gradlew bootRun --args='--server.port=8098 --spring.docker.compose.lifecycle-management=NONE'

# 10.2 statuses: expect 404, 404, 404, 404, 200, 200, 400, 500, 500
B=http://localhost:8098
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html'       $B/owners/9999
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: application/json' $B/owners/9999
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html'       $B/owners/9999/edit
curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Accept: text/html' \
  -d 'firstName=Joe&lastName=Bloggs&address=123 Caramel Street&city=London&telephone=1616291589' \
  $B/owners/9999/edit
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html'       $B/owners/1
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html'       $B/owners/1/edit
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html'       $B/owners/abc
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html'       "$B/owners?page=0"
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html'       $B/oups

# 10.3 AC-4 body: first two greps must match, last two must print nothing
curl -s -H 'Accept: text/html' -H 'Accept-Language: en' $B/owners/9999 > /tmp/o9999.html
grep -c 'Something happened' /tmp/o9999.html
grep -o 'The requested page was not found.' /tmp/o9999.html
grep -o 'An internal server error occurred.' /tmp/o9999.html
grep -o 'Whitelabel Error Page' /tmp/o9999.html

# 10.4 no write happened
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html' $B/owners/9999   # still 404
curl -s -H 'Accept: text/html' "$B/owners?lastName=" | grep -c 'Franklin'        # owner 1 intact

# 10.5 informational scope-leak check, not a product criterion: all three return 500 today.
# A 404 here means this feature leaked across controllers - investigate. Any other status is
# out of scope (specification section 6) and is recorded rather than treated as a failure.
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html' $B/owners/9999/pets/new
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html' $B/owners/9999/pets/1/edit
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html' $B/owners/9999/pets/1/visits/new
```

Then, per the memory note that template and UI claims need browser evidence, open
`http://localhost:8098/owners/9999` in a browser (Playwright MCP is available), confirm the PetClinic
layout with the not-found message renders rather than a bare or white page, and confirm the browser
console reports no errors. Stop the application afterwards.

## 11. Success criteria

1. `GET /owners/9999`, `GET /owners/9999/edit`, and `POST /owners/9999/edit` each return exactly `404`,
   for a valid and for an invalid form body, and with `Accept: text/html` and `Accept: application/json`.
2. The HTML 404 body contains `Something happened...` and `The requested page was not found.` and
   contains neither `An internal server error occurred.` nor `Whitelabel Error Page`.
3. `owners.count()` is identical before and after both POSTs, `owners.findById(9999)` is empty after
   both, and owner 1's `firstName`, `lastName`, `address`, `city`, and `telephone` are byte-identical.
4. `GET /owners/1` returns 200 with view `owners/ownerDetails`; `GET /owners/1/edit` returns 200 with
   view `owners/createOrUpdateOwnerForm`; `POST /owners/1/edit` with a valid form returns 3xx to
   `redirect:/owners/{ownerId}`.
5. `GET /owners/abc` returns 400; `GET /owners?page=0` returns 500; `GET /oups` returns 500.
6. None of `/owners/9999/pets/new`, `/owners/9999/pets/1/edit`, or `/owners/9999/pets/1/visits/new`
   returns 404. **Informational, not a product criterion:** their exact status is out of scope
   (specification section 6), and all three return 500 today. A 404 means cross-cutting leakage and
   must be investigated; any other status is recorded in the closing document, not failed.
7. All six methods of `OwnerNotFoundIntegrationTests` pass. Command 9.5 passes with zero failures and
   zero edits to those classes. Command 9.4 passes with `OwnerControllerTests` edited in exactly one
   place: the two AC-5 assertions added to `processUpdateOwnerFormSuccess` in Task 2.
8. `./mvnw verify` and `./gradlew build` both succeed; any Docker-dependent skips are named explicitly.
9. `git status --short` lists exactly the section 4 files for the task in hand - three in Task 1, one
   in Task 2 plus `OwnerNotFoundIntegrationTests.java` - and `git diff --check` reports nothing.
10. The diff contains no `@ControllerAdvice`, no `@ExceptionHandler`, no `catch`, and no reference to
    `IllegalArgumentException` in `OwnerController`.

## 12. Must remain unchanged

- `src/main/resources/templates/error.html` and every file under `src/main/resources/messages/`.
- `src/main/resources/application*.properties` and everything under `src/main/resources/db/`.
- `PetController.java`, `VisitController.java`, `PetControllerTests.java`, `VisitControllerTests.java`.
- `CrashControllerIntegrationTests.java` - unmodified, per section 8 of the specification.
- `OwnerControllerTests.java` apart from the two assertions Task 2 adds inside
  `processUpdateOwnerFormSuccess`. No existing assertion, no fixture, no mock stub, no import, and no
  other method in that class changes.
- `OwnerRepository.java`, `Owner.java`, `Person.java`, `BaseEntity.java`, `NamedEntity.java`.
- `pom.xml`, `build.gradle`, `src/checkstyle/*`, `PetClinicRuntimeHints.java`, `.editorconfig`.
- Every document in the repository root, including the superseded
  `plan-owner-not-found-handling-2026-09.md`, with two stated exceptions so that this section and the
  closing step no longer contradict each other. First, the human review gate held between Task 1 and
  Task 2 amended `spec-owner-not-found-2026-09.md` - the AC-5 GET wording and the AC-5 test-mapping
  row - together with this plan; see `review-gate-2026-09.md`. Second, the Task 3 closing step may
  rewrite that specification's `Status:` line and nothing else in it (task list section 3.4.5).
  Neither touches a functional requirement, an acceptance-criterion outcome, a constraint, a route, or
  an example.
- Behaviorally: the 400 for a non-numeric ID, the 500 for `?page=0`, the 500 for `/oups`, the 405 for
  unmapped methods, the 200-with-field-error for an unmatched last-name search, and the duplicate
  owner lookup in `showOwner`.

## 13. Unresolved decisions

**None.** Every judgment call is resolved above:

1. Named exception versus inline `ResponseStatusException` - resolved in favor of the named exception
   (section 2), with the inline form recorded as a behaviorally identical fallback (section 3).
2. Whether to convert the unreachable `showOwner` lookup - yes, so the two sites cannot drift; the
   duplicate lookup itself stays (step 4).
3. `@ResponseStatus` `reason` attribute - omitted (section 2).
4. Exception visibility and `serialVersionUID` - package-private, none (section 5).
5. Test harness shape - full context, `@AutoConfigureTestRestTemplate`, autowired `OwnerRepository`, no
   `properties`, no `@Transactional` (section 5).
6. JSON assertions - status only (section 3).
7. No new `PetController` or `VisitController` acceptance criterion; the boundary is protected
   structurally and probed manually (risk 6).
8. Whether `OwnerControllerTests` gains cases - no new test method (section 3). It does gain two
   assertions inside the existing `processUpdateOwnerFormSuccess`, decided at the Task 1 review gate
   because AC-5's redirect-target and persistence clauses had no failing witness (section 7).

The only item carrying residual technical uncertainty is risk 7, and step 6 resolves it by hand before
any test depends on it.
