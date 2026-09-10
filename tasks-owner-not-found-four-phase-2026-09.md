# Task List: HTTP 404 for a Missing Owner (Four-Phase Workflow) - 2026-09

Phase: Tasks. Authority: `spec-owner-not-found-2026-09.md` (Specify, approved) and
`plan-owner-not-found-four-phase-2026-09.md` (Plan, approved). No production code, test,
configuration, specification, or plan file was modified while writing this list, and no task below
has been executed.

Branch: `homework/four-phase-workflow`. Three tasks, each sized for one Claude Code session and each
committable on its own.

**Homework 2.3 requires implementing Task 1 only.** Tasks 2 and 3 are specified here so the remaining
work is predictable, not so it is done now.

## Dependency and order

- **Task 1 before Task 2.** Task 2 adds two methods to the test class Task 1 creates. Running Task 2
  first would leave the AC-6 and AC-7 assertions with no file to live in and no 404 behavior to guard.
- **Task 1 is independently committable** even though it omits the AC-6 and AC-7 methods. Its diff
  touches only the two `orElseThrow` lambdas in `OwnerController` plus one new exception class.
  Neither route in AC-6 or AC-7 reaches those lines: `GET /owners/abc` fails in path-variable
  conversion before `findOwner` is invoked, and `GET /owners?page=0` throws from inside
  `processFindForm` after `findOwner` has already returned a blank `Owner`. Both statuses are therefore
  unchanged by construction, and Task 1 ships a complete, working behavior change: the three in-scope
  routes return 404 with the existing error page and write nothing.
- **Task 2 before Task 3.** Task 3 runs the full builds and the closing documentation, so it must see
  the final six-method test class.
- **After Task 1 the specification is satisfied but not yet fully test-covered**: AC-6 and AC-7 have no
  automated assertion until Task 2. That is the one accepted gap of the split, and it is stated in
  Task 1's commit message.

## Traceability

| AC | Requirement | Covered by | Verified in |
| --- | --- | --- | --- |
| AC-1 | 404 for `GET /owners/9999`, both `Accept` headers | `unknownOwnerDetailsReturnsNotFound` | Task 1 |
| AC-2 | 404 for `GET /owners/9999/edit` | `unknownOwnerEditFormReturnsNotFound` | Task 1 |
| AC-3 | 404 for `POST /owners/9999/edit`, valid and invalid form, no data change | `unknownOwnerUpdateReturnsNotFoundAndChangesNoData` | Task 1 |
| AC-4 | English 404 error page body | `unknownOwnerRendersEnglishNotFoundPage` plus manual check 1.9.2 | Task 1 |
| AC-5 | Owner 1 behavior unchanged | `OwnerControllerTests.showOwner`, `.initUpdateOwnerForm`, `.processUpdateOwnerFormSuccess`, unmodified | Task 1 as a non-regression guard; Task 2 as designated criterion coverage |
| AC-6 | `GET /owners/abc` stays 400 | `nonNumericOwnerIdReturnsBadRequest` | Task 2 |
| AC-7 | `GET /owners?page=0` stays 500 | `paginationErrorIsNotTreatedAsNotFound` | Task 2 |
| AC-8 | `GET /oups` stays 500 | `CrashControllerIntegrationTests.triggerExceptionHtml`, unmodified | Task 2 |
| Spec section 7 | Error-case invariant | AC-3 row above | Task 1 |

---

# Task 1 - owner-not-found 404 behavior and its primary coverage

## 1.1 Goal

Make `GET /owners/{ownerId}`, `GET /owners/{ownerId}/edit`, and `POST /owners/{ownerId}/edit` return
404 for a well-formed numeric ID with no matching owner, rendering the existing error page and writing
nothing, and cover that behavior with four integration tests.

## 1.2 Specification criteria covered

FR-1, FR-2, FR-3, FR-4, FR-6, constraints 1 through 5, AC-1, AC-2, AC-3, AC-4, and the section 7
error-case invariant. AC-5 is guarded against regression here but is formally credited to Task 2.
AC-6, AC-7 and AC-8 are out of this task.

## 1.3 Exact files

Created:

1. `src/main/java/org/springframework/samples/petclinic/owner/OwnerNotFoundException.java`
2. `src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java`

Modified:

3. `src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java`

No fourth file. If a fourth file appears in `git status`, stop (see 1.7).

## 1.4 Implementation steps

**1.4.1** Run `/function-summary` on
`src/main/java/org/springframework/samples/petclinic/owner/OwnerController.java:findOwner`, then on
`:showOwner`. Confirm `git status --short` shows no modified source file before starting.

**1.4.2** Record the baseline by running
`./mvnw test -Dtest='OwnerControllerTests,PetControllerTests,VisitControllerTests,CrashControllerIntegrationTests'`
and noting that it passes before any edit.

**1.4.3** Create `OwnerNotFoundException.java` with the repository's Apache 2.0 header, tabs at width
4, LF endings, and a final newline:

```java
@ResponseStatus(HttpStatus.NOT_FOUND)
class OwnerNotFoundException extends RuntimeException {

	OwnerNotFoundException(int ownerId) {
		super("Owner not found with id: " + ownerId);
	}

}
```

Imports: `org.springframework.http.HttpStatus` and
`org.springframework.web.bind.annotation.ResponseStatus`. Package-private. No `reason` attribute on
the annotation. No `serialVersionUID`. Add a two-line Javadoc in the style of `PetValidator`.

**1.4.4** In `OwnerController.findOwner(Integer)` (lines 64-70), replace the `IllegalArgumentException`
lambda with `.orElseThrow(() -> new OwnerNotFoundException(ownerId))`. Keep the ternary, the
`@ModelAttribute("owner")` annotation, and `@PathVariable(name = "ownerId", required = false)` exactly
as they are.

**1.4.5** In `OwnerController.showOwner(int)` (lines 172-174), replace the second
`IllegalArgumentException` lambda with `.orElseThrow(() -> new OwnerNotFoundException(ownerId))`. Keep
the duplicate `findById` call; removing it is out of scope per specification section 6. This branch is
unreachable while `findOwner` throws first, so it will show as uncovered; it is changed so the two
sites cannot diverge later.

**1.4.6** Run `./mvnw spring-javaformat:apply`. Do not hand-order the imports. `OwnerController` needs
no new import, because the exception is in the same package.

**1.4.7** Manual 404 checkpoint, performed before the test class is written. Start the app with
`./gradlew bootRun --args='--server.port=8098 --spring.docker.compose.lifecycle-management=NONE'`,
then run the two checks in 1.9.2 and 1.9.3. Both must pass before continuing to 1.4.8. Stop the app
afterwards.

**1.4.8** Create `OwnerNotFoundIntegrationTests.java` in package
`org.springframework.samples.petclinic.owner` with `@SpringBootTest(webEnvironment = RANDOM_PORT)` and
`@AutoConfigureTestRestTemplate`; inject `@LocalServerPort int port`,
`@Autowired TestRestTemplate rest`, and `@Autowired OwnerRepository owners`. Add no `properties`
attribute, no `@Transactional`, and no `@DirtiesContext`. Constants `UNKNOWN_OWNER_ID = 9999` and
`EXISTING_OWNER_ID = 1`. URLs are absolute, `"http://localhost:" + this.port + ...`, matching
`CrashControllerIntegrationTests`. Assertions use AssertJ `assertThat`. Requests use
`rest.exchange(...)`, which returns 4xx and 5xx rather than throwing. Write exactly these four
methods:

- `unknownOwnerDetailsReturnsNotFound` - `GET /owners/9999` with `Accept: text/html`, then again with
  `Accept: application/json`; assert `HttpStatus.NOT_FOUND` for both. Assert nothing about either body.
- `unknownOwnerEditFormReturnsNotFound` - `GET /owners/9999/edit`; assert `HttpStatus.NOT_FOUND`.
- `unknownOwnerUpdateReturnsNotFoundAndChangesNoData` - before the requests, snapshot
  `owners.count()` and owner 1's `getFirstName`, `getLastName`, `getAddress`, `getCity`,
  `getTelephone` into local variables. Then `POST /owners/9999/edit` twice with
  `Content-Type: application/x-www-form-urlencoded` and a `LinkedMultiValueMap` body: first
  `firstName=Joe`, `lastName=Bloggs`, `address=123 Caramel Street`, `city=London`,
  `telephone=1616291589`; then the same body with `firstName` set to the empty string. After each
  request assert `HttpStatus.NOT_FOUND`, `owners.count()` equal to the snapshot,
  `owners.findById(9999)` empty, and owner 1's five fields equal to their snapshots.
- `unknownOwnerRendersEnglishNotFoundPage` - `GET /owners/9999` with `Accept: text/html` and
  `Accept-Language: en`; assert the body is not null, contains `Something happened...`, contains
  `The requested page was not found.`, does not contain `An internal server error occurred.`, and does
  not contain `Whitelabel Error Page`.

Do not add `nonNumericOwnerIdReturnsBadRequest` or `paginationErrorIsNotTreatedAsNotFound`; they
belong to Task 2.

**1.4.9** Run `./mvnw spring-javaformat:apply` again, then the commands in 1.8.

**1.4.10** Stage the three files, then ask the user to run `/review-changes`. Per `HARNESS.md` that
command is configured `disable-model-invocation: true`, so Claude must not invoke it; the user types
it. Address any finding it returns inside the three files of 1.3.

**1.4.11** Commit with the message in 1.11, then stop. Do not start Task 2.

## 1.5 Must remain unchanged

- `OwnerControllerTests.java` and `CrashControllerIntegrationTests.java`.
- `PetController.java`, `VisitController.java`, `PetControllerTests.java`, `VisitControllerTests.java`.
- `templates/error.html` and every file under `src/main/resources/messages/`.
- `application*.properties`, everything under `src/main/resources/db/`, `pom.xml`, `build.gradle`,
  `src/checkstyle/*`, `PetClinicRuntimeHints.java`.
- `OwnerRepository.java`, `Owner.java`, `Person.java`.
- `spec-owner-not-found-2026-09.md` and `plan-owner-not-found-four-phase-2026-09.md`.
- Behavior: owner 1 on all three routes; 400 for `GET /owners/abc`; 500 for `GET /owners?page=0`; 500
  for `GET /oups`; 405 for an unmapped method; 200 with a `lastName` field error for an unmatched
  search; 500 for the `PetController` and `VisitController` missing-owner routes; the duplicate lookup
  in `showOwner`.

## 1.6 Risks

1. The diff must contain no `@ControllerAdvice`, no `@ExceptionHandler`, and no `catch`. A handler keyed
   on `IllegalArgumentException` would turn `GET /owners?page=0` into a 404, because pagination throws
   that same type from the same controller.
2. AC-4 depends on `sendError` triggering the container's error dispatch. Checkpoint 1.4.7 exposes that
   before any test depends on it.
3. `@Transactional` on the test class would roll back or mask a real write on the server thread and
   would make the AC-3 invariant meaningless.
4. The `showOwner` line from 1.4.5 is unreachable and will report as uncovered. That is expected and is
   called out in 1.4.10's review handoff.

## 1.7 Stopping conditions

Stop, leave the tree uncommitted, and report if any of these occur:

- `GET /owners/9999` returns 404 with an empty body: the error dispatch did not run, so the AC-4
  assertion has no basis yet.
- `GET /owners/9999/edit` does not return 404: the class-level `@ModelAttribute` path is not the
  effective one, and 1.4.8's design assumption is wrong.
- Any of the four new test methods fails after two correction attempts confined to the files in 1.3.
- Making `OwnerControllerTests` pass appears to require editing it: the approach is wrong, not the test.
- `owners.count()` or owner 1's fields differ after either POST: a real write occurred, which is a
  production defect and not a test problem.
- `git status --short` lists any file outside 1.3.

## 1.8 Verification commands

```bash
# 1.8.1 formatting and style gates
./mvnw spring-javaformat:apply
./mvnw validate
./gradlew checkFormatMain checkFormatTest checkstyleMain checkstyleTest checkstyleNohttp

# 1.8.2 the four new tests
./mvnw test -Dtest=OwnerNotFoundIntegrationTests

# 1.8.3 non-regression guard, unmodified classes
./mvnw test -Dtest='OwnerControllerTests,PetControllerTests,VisitControllerTests,CrashControllerIntegrationTests'

# 1.8.4 diff shape
git diff --check
git status --short
git diff --stat
```

## 1.9 Manual checks (both mandatory, run at 1.4.7)

```bash
# 1.9.1 start
./gradlew bootRun --args='--server.port=8098 --spring.docker.compose.lifecycle-management=NONE'

# 1.9.2 check one: 404 plus the existing error page
B=http://localhost:8098
curl -s -o /tmp/o9999.html -w '%{http_code}\n' -H 'Accept: text/html' -H 'Accept-Language: en' $B/owners/9999
grep -o 'Something happened...' /tmp/o9999.html
grep -o 'The requested page was not found.' /tmp/o9999.html
grep -o 'An internal server error occurred.' /tmp/o9999.html
grep -o 'Whitelabel Error Page' /tmp/o9999.html
# required: 404, then the first two greps print a line each, the last two print nothing

# 1.9.3 check two: the edit form, whose handler never queries the repository
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html' $B/owners/9999/edit
# required: 404. This is the probe that proves the class-level @ModelAttribute("owner") method is the
# effective missing-owner path, because initUpdateOwnerForm() takes no arguments and never calls the
# repository itself.
```

## 1.10 Success criteria

1. `GET /owners/9999` returns 404 with `Accept: text/html` and with `Accept: application/json`.
2. `GET /owners/9999/edit` returns 404.
3. `POST /owners/9999/edit` returns 404 for the valid body and for the blank-`firstName` body.
4. The HTML 404 body contains `Something happened...` and `The requested page was not found.`, and
   contains neither `An internal server error occurred.` nor `Whitelabel Error Page`.
5. `owners.count()` is identical before and after both POSTs, `owners.findById(9999)` is empty after
   both, and owner 1's five scalar fields are identical to their snapshots.
6. `GET /owners/1` returns 200 and `GET /owners/1/edit` returns 200.
7. All four methods of `OwnerNotFoundIntegrationTests` pass; command 1.8.3 passes with zero failures
   and zero edits to those four classes.
8. Commands 1.8.1 report no formatting, checkstyle, or nohttp violation.
9. `git status --short` lists exactly the three files in 1.3, and `git diff --check` prints nothing.
10. The staged diff contains no `@ControllerAdvice`, no `@ExceptionHandler`, no `catch`, and no
    remaining reference to `IllegalArgumentException` in `OwnerController`.

## 1.11 Suggested commit message

```text
Return 404 for a missing owner in OwnerController

GET /owners/{id}, GET /owners/{id}/edit and POST /owners/{id}/edit reported a
missing owner as 500 because OwnerController threw IllegalArgumentException,
which Spring MVC has no mapping for.

Add a package-private OwnerNotFoundException annotated
@ResponseStatus(HttpStatus.NOT_FOUND) and throw it from both owner lookup sites.
Binding the status to an owner-specific type keeps GET /owners?page=0 at 500,
which a handler keyed on IllegalArgumentException would have reclassified. No
advice or exception handler is added, so PetController and VisitController are
untouched. The existing error.html 404 branch and error.404 message are reused.

Cover AC-1 through AC-4 and the no-write invariant with a new
OwnerNotFoundIntegrationTests. AC-6 and AC-7 assertions follow in the next
commit; neither route reaches the changed lines, so both statuses are unchanged.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
```

## 1.12 What Task 2 will do

Task 2 adds exactly two methods to `OwnerNotFoundIntegrationTests`,
`nonNumericOwnerIdReturnsBadRequest` and `paginationErrorIsNotTreatedAsNotFound`, asserting 400 for
`GET /owners/abc` and 500 for `GET /owners?page=0`. It then runs `OwnerControllerTests` and
`CrashControllerIntegrationTests` without editing either, and commits. It changes no production file.

---

# Task 2 - regression-boundary tests

## 2.1 Goal

Prove that the Task 1 change did not reclassify the two failures that are easiest to catch by accident,
and record AC-5 and AC-8 as verified by the existing unmodified tests.

## 2.2 Specification criteria covered

AC-6, AC-7, constraint 3, and formal credit for AC-5 and AC-8.

## 2.3 Exact files

Modified:

1. `src/test/java/org/springframework/samples/petclinic/owner/OwnerNotFoundIntegrationTests.java`

No production file. No other test file.

## 2.4 Implementation steps

**2.4.1** Add `nonNumericOwnerIdReturnsBadRequest`: `GET /owners/abc` with `Accept: text/html`; assert
`HttpStatus.BAD_REQUEST`.

**2.4.2** Add `paginationErrorIsNotTreatedAsNotFound`: `GET /owners?page=0` with `Accept: text/html`;
assert `HttpStatus.INTERNAL_SERVER_ERROR`, and assert the status is not `HttpStatus.NOT_FOUND` so the
intent of the test is legible at the failure message. Add a one-line comment stating that this test
exists to prevent a broad `IllegalArgumentException`-to-404 mapping, per specification AC-7, and does
not endorse 500 as the ideal pagination status.

**2.4.3** Run `./mvnw spring-javaformat:apply`, then the commands in 2.7.

**2.4.4** Confirm the class now holds exactly the six methods named in specification section 8.

**2.4.5** Stage the file and ask the user to run `/review-changes`, then commit with 2.9 and stop.

## 2.5 Must remain unchanged

Everything in 1.5, plus the three files Task 1 committed: `OwnerNotFoundException.java`,
`OwnerController.java`, and the four existing methods of `OwnerNotFoundIntegrationTests.java`.

## 2.6 Risks and stopping conditions

- If `nonNumericOwnerIdReturnsBadRequest` sees 404, the Task 1 change reached path-variable conversion,
  which it must not. Stop and report; the production change is wrong.
- If `paginationErrorIsNotTreatedAsNotFound` sees 404, a broad `IllegalArgumentException` mapping was
  introduced. Stop and report; this is the constraint 3 violation the test exists to catch.
- If either test needs a change to a production file to pass, stop: Task 2 is test-only by definition.

## 2.7 Verification commands

```bash
# 2.7.1 the full six-method class
./mvnw test -Dtest=OwnerNotFoundIntegrationTests
./gradlew test --tests "org.springframework.samples.petclinic.owner.OwnerNotFoundIntegrationTests" --rerun

# 2.7.2 AC-5 and AC-8 by their existing unmodified classes
./mvnw test -Dtest='OwnerControllerTests,CrashControllerIntegrationTests'

# 2.7.3 style and diff shape
./mvnw validate
git diff --check
git status --short

# 2.7.4 manual baseline for the two new assertions
B=http://localhost:8098
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html' $B/owners/abc      # required: 400
curl -s -o /dev/null -w '%{http_code}\n' -H 'Accept: text/html' "$B/owners?page=0" # required: 500
```

## 2.8 Success criteria

1. `OwnerNotFoundIntegrationTests` holds six methods, named exactly as in specification section 8, and
   all six pass under Maven and under Gradle.
2. `OwnerControllerTests` and `CrashControllerIntegrationTests` pass with zero edits, evidenced by
   their absence from `git status --short`.
3. `GET /owners/abc` returns 400 and `GET /owners?page=0` returns 500, in the test and by curl.
4. `git status --short` lists exactly one file, and `git diff --check` prints nothing.

## 2.9 Suggested commit message

```text
Add regression tests for the owner-not-found 404 boundary

GET /owners/abc must stay 400 and GET /owners?page=0 must stay 500. Pagination
throws the same IllegalArgumentException type that a missing owner used to
throw, so these two assertions are what would fail if the 404 were ever
reattached to that exception type rather than to OwnerNotFoundException.

Completes the section 8 test mapping: AC-6 and AC-7 are now automated, AC-5 and
AC-8 remain covered by OwnerControllerTests and
CrashControllerIntegrationTests, both unmodified.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
```

## 2.10 What Task 3 will do

Task 3 runs `./mvnw verify` and `./gradlew build`, performs the full manual sweep from plan section 10
including the browser check and the three `PetController` and `VisitController` boundary probes, sets
the specification's `Status:` line to implemented without touching any requirement, criterion, or
constraint, and writes a workflow evaluation document. It changes no code and no test.

---

# Task 3 - full builds, full verification, closing documentation

## 3.1 Goal

Prove both build toolchains are green, confirm the rendered page in a real browser, and close the
four-phase workflow with an accurate status and a written evaluation.

## 3.2 Specification criteria covered

Constraint 5 (both builds keep working) and the section 6 out-of-scope boundary for `PetController`
and `VisitController`. All eight acceptance criteria are re-run as a whole-suite regression.

## 3.3 Exact files

Modified:

1. `spec-owner-not-found-2026-09.md` - the `Status:` line on line 3 only.

Created:

2. `four-phase-evaluation-2026-09.md`

No code file, no test file, no configuration file.

## 3.4 Implementation steps

**3.4.1** Run `./mvnw verify`, then `./gradlew build`. Record which tests ran and which were skipped.

**3.4.2** If Docker is not running, `MySqlIntegrationTests` and `PostgresIntegrationTests` will skip.
Name the skipped classes in the evaluation document rather than reporting an unqualified green.

**3.4.3** Start the app on port 8098 and run plan section 10.2 through 10.5 in full, including the
three probes that must still return 500: `/owners/9999/pets/new`, `/owners/9999/pets/1/edit`, and
`/owners/9999/pets/1/visits/new`.

**3.4.4** With the app still running, open `http://localhost:8098/owners/9999` through Playwright MCP.
Confirm the PetClinic header, navigation bar, and the not-found message render as a normal page rather
than a bare or unstyled one, take a screenshot, and read the browser console. Record the console
output. Then open `http://localhost:8098/owners/1` and confirm the owner page still renders with its
pets and visits table. Stop the app.

**3.4.5** In `spec-owner-not-found-2026-09.md`, change line 3 from `Status: proposed.` to a status line
stating that the specification is implemented on `homework/four-phase-workflow` and naming
`OwnerNotFoundIntegrationTests` as its verifier. Change nothing else in that file: no functional
requirement, acceptance criterion, constraint, route, example, or test-mapping row.

**3.4.6** Write `four-phase-evaluation-2026-09.md` covering: what each phase caught that the previous
one had missed, the two Specify-phase defects found by reading the repository, the Plan-phase
correction to the `@ControllerAdvice` reasoning, the measured build and test results from 3.4.1, the
browser evidence from 3.4.4, and one honest statement of what the four-phase workflow cost in time
against what it prevented.

**3.4.7** Stage both documents, ask the user to run `/review-changes`, and commit with 3.8.

## 3.5 Must remain unchanged

Everything in 1.5 except the specification's `Status:` line, plus every file committed by Tasks 1 and
2. If any `src/` file appears in `git status --short` during Task 3, stop.

## 3.6 Risks and stopping conditions

- If `./mvnw verify` or `./gradlew build` fails on a test unrelated to the owner change, stop and
  report the failing class rather than adjusting the test.
- If the browser shows an unstyled page or a console error on `/owners/9999`, stop: `error.html`
  renders through the shared layout fragment, so a break there affects every error page.
- If any of the three sibling-controller probes returns 404, stop: cross-cutting behavior leaked in,
  violating constraint 4, and the Task 1 commit must be revisited.
- If closing the specification status appears to require editing a requirement, stop: the status line
  is the only permitted edit.

## 3.7 Verification commands

```bash
./mvnw verify
./gradlew build
git diff --check
git status --short
git diff -- spec-owner-not-found-2026-09.md
```

## 3.8 Suggested commit message

```text
Close the four-phase workflow for owner-not-found 404

Record the specification as implemented and add the workflow evaluation. Both
builds pass; the manual sweep confirms 404 on the three in-scope routes, 400 for
a non-numeric ID, 500 for ?page=0 and /oups, and 500 on the PetController and
VisitController routes that stay out of scope. The browser check confirms the
404 page renders through the shared layout with a clean console.

Documentation only: no production, test, or configuration file changes, and no
behavioral requirement in the specification is altered.

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
```

## 3.9 Success criteria

1. `./mvnw verify` and `./gradlew build` both succeed, and any skipped Docker-dependent class is named.
2. The nine status probes from plan section 10.2 return 404, 404, 404, 404, 200, 200, 400, 500, 500 in
   that order.
3. The three sibling-controller probes from plan section 10.5 each return 500.
4. A screenshot of `/owners/9999` shows the PetClinic layout with `The requested page was not found.`,
   and the recorded console output contains no error.
5. `git diff -- spec-owner-not-found-2026-09.md` shows one changed line, the `Status:` line.
6. `git status --short` lists exactly two documentation files.

## 3.10 What follows Task 3

Nothing in this feature. Possible separate work, each needing its own specification: missing-owner and
missing-pet 404s in `PetController` and `VisitController`, a considered status for an invalid page
index, and removal of the duplicate owner lookup in `showOwner`.

---

# Task 1 stop boundary

Homework 2.3 is Task 1 and nothing else. After completing 1.4.1 through 1.4.11 - the three-file
change, the four tests, the formatting and style gates, the two mandatory manual 404 checks, the
`/review-changes` handoff, and the commit - Claude must stop and report.

Claude must not begin Task 2 in the same session: not the `nonNumericOwnerIdReturnsBadRequest` method,
not the `paginationErrorIsNotTreatedAsNotFound` method, not `./mvnw verify`, not `./gradlew build`, not
the specification status line, and not the evaluation document. Task 2 starts only when the user asks
for it.
