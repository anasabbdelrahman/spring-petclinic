# Workflow Evaluation: HTTP 404 for a Missing Owner - 2026-09

Phase: closing (Task 3). Authority: `spec-owner-not-found-2026-09.md`,
`plan-owner-not-found-four-phase-2026-09.md`,
`tasks-owner-not-found-four-phase-2026-09.md`, and `review-gate-2026-09.md`.

This document changes no production code, no test, and no configuration. The only other file
Task 3 touched is the specification's `Status:` line.

## 1. Branch reference reconciliation

The task list header and task list section 3.4.5 both name `homework/four-phase-workflow`. That
branch is **not** the branch this work now sits on, but it does still exist and nothing was renamed
or deleted. Verified against git:

| Ref | Commit | Contents |
| --- | --- | --- |
| `homework/four-phase-workflow` (local and `origin`) | `7d57293` | Task 1 |
| `homework/iterative-workflow` (local, current `HEAD`) | `0c02b98` | Task 1 and Task 2 |

`homework/four-phase-workflow` remains the **historical Task 1 branch**, preserved at the commit it
ended on, which is why the task list written during that homework names it. Homework 2.5 then
continued from that work on a new branch, `homework/iterative-workflow`, following this repository's
one-branch-per-homework pattern; `homework/four-phase-workflow` is a direct ancestor of it, so the
Task 1 history is shared rather than duplicated.

The specification's new `Status:` line therefore names `homework/iterative-workflow`: it is the branch
that contains **both** completed implementation commits - Task 1 at `7d57293` and Task 2 at
`0c02b98` - and it is the active Homework 2.5 branch on which Task 3 ran both builds and the manual
sweep. Naming `homework/four-phase-workflow` instead would point at a branch holding only Task 1,
which lacks the AC-6, AC-7 and AC-5 coverage that the status line's verifier claim depends on.

The task list is left unchanged, as a historical planning artifact recording what was true when it
was written. Task 3 section 3.3 does not permit editing it in any case.

## 2. What each phase caught that the previous one had missed

**Specify caught what a prose request had missed.** The starting request was "a missing owner should
not be a 500". Writing the criteria down forced three boundaries into the open that the request did
not mention at all: a non-numeric ID is a *different* failure that already returns 400 (AC-6), an
invalid page index throws the *same exception type* and must keep its status (AC-7), and a genuine
fault must stay 500 (AC-8). It also forced FR-4 - that the missing-owner check precede validation, so
an invalid form for a missing owner is 404 and not 200-with-field-errors.

**Plan caught what Specify could not.** The specification deliberately left the approach open.
Reading the repository then invalidated the most natural implementation. `@ExceptionHandler(
IllegalArgumentException.class)` on `OwnerController` would have satisfied every 404 criterion and
broken AC-7, because `OwnerController.processFindForm` throws that identical type for `?page=0`. The
specification named the hazard; only the plan could establish that the two conditions were
indistinguishable by type in the same controller. That single finding selected the design: bind the
status to a dedicated `OwnerNotFoundException` so AC-7 holds by construction rather than by test luck.

Plan also corrected its own `@ControllerAdvice` reasoning. The first draft rejected a
`@ControllerAdvice` on `OwnerNotFoundException` by citing constraint 4 (no cross-cutting change to
`PetController` or `VisitController`). That was wrong: neither sibling controller throws
`OwnerNotFoundException`, so such an advice could not have affected them and constraint 4 was never at
stake. The rejection stands, but on the honest ground - it is unnecessary infrastructure, since
`@ResponseStatus` already expresses the status and no handler need exist. Distinguishing "forbidden"
from "unnecessary" matters: the former would have barred a future advice for other reasons.

**Tasks caught a sequencing fact.** Splitting the work exposed that Task 1 ships a complete behavior
change while leaving AC-6 and AC-7 without automated assertions until Task 2. Task 1's commit message
states that gap rather than implying full coverage.

**The human review gate caught what all four documents had agreed on.** See section 3.

**Task 3 caught an over-broad criterion of its own, plus one reporting artifact and one pre-existing
behavior.** The criterion is the more interesting of the three: task list 3.9.4 and 3.6 forbid any
browser console error on `/owners/9999`, which a correctly-404 page cannot satisfy, because the
browser always reports the main document's own non-2xx status. That criterion therefore **did not
pass literally**, while no application defect existed - the only phase-authored criterion in this
workflow that a correct implementation could not meet. See sections 4.5 and 5. None of the three is a
defect in the feature; all are recorded so a later reader is not surprised by them.

## 3. The three Specify- and Plan-phase defects found by reading the repository

Found by the human review gate held between Task 1 and Task 2 (`review-gate-2026-09.md`), by
re-deriving every load-bearing claim from the repository rather than from the documents.

**Defect 1 - AC-5's POST clause had no failing witness (material).** AC-5 required that
`POST /owners/1/edit` produce a 3xx to `/owners/1` *and that the owner be saved*.
`OwnerControllerTests.processUpdateOwnerFormSuccess` asserted only `status().is3xxRedirection()` and
the **un-expanded** view-name template `redirect:/owners/{ownerId}`. `OwnerRepository` is a
`@MockitoBean`, and no `verify(...).save(...)` existed anywhere in the owner test package. Deleting
`this.owners.save(owner)` from `OwnerController` left the **entire suite green**. The criterion whose
subject is "the write actually happens" was certified by a suite that could not detect the write
stopping - in a feature whose whole subject is the boundary between "wrote nothing" and "wrote".

**Defect 2 - AC-5's GET wording contradicted the seed data.** AC-5 required the `owner` model
attribute to contain "the stored owner, pets, **and visits**". In the seeded H2 data owner 1 owns
exactly one pet, Leo, and every seeded visit belongs to pets 7 and 8, which are owner 6's. Seeded
owner 1 has **no visits at all**. The clause passed only because the Mockito fixture contradicted the
data it stood for, and would have failed outright the moment anyone moved it to the integration level -
which is exactly what the word "stored" invited. Task 3's browser check independently re-confirmed
this: `/owners/1` renders the "Pets and Visits" table with Leo and an empty visit list (section 6).

**Defect 3 - the test mapping was read as a ceiling rather than a floor.** Plan section 3 rejected
both obvious remedies for defect 1 by citing specification section 8; plan section 7 then reported
AC-5 as satisfied because a row existed for it; task list sections 2.3 and 2.5 permitted Task 2 one
file while freezing `OwnerControllerTests`. Every route to a failing witness was closed by an approved
artifact, and the gate's own remediation hit the same wall - fixing AC-5 required editing the
specification, which plan section 12 froze. Two consistency faults travelled with it: plan section 12
froze the specification that task list 3.4.5 *must* edit, and plan section 11 criterion 6 promoted
explicitly out-of-scope sibling-controller statuses to pass/fail product criteria that plan risk 6
argues against. Both would have surfaced during Task 3 as phantom stop conditions.

Why this mattered more than its size suggests: four artifacts traced criteria *forward* to test names,
and all three mapping tables agreed with each other. A mapping table cannot distinguish "asserted"
from "mentioned in a test that happens to pass". Both builds were green throughout. Nothing looked
wrong.

## 4. Measured results (Task 3, 2026-09-11)

### 4.1 Both builds

| Command | Result | Tests | First attempt? |
| --- | --- | --- | --- |
| `./mvnw verify` | BUILD SUCCESS, 46.6 s | 82 run, 0 failures, 0 errors, 0 skipped | yes |
| `./gradlew build` | BUILD SUCCESSFUL, 41 s | 82 completed | yes |

**No Docker-dependent class was skipped.** The Docker daemon was running, so `MySqlIntegrationTests`
(2 tests, Testcontainers) and `PostgresIntegrationTests` (2 tests, Docker Compose) both **executed and
passed**. This is a stronger result than plan section 9 anticipated, which allowed for naming them as
skipped. Reported as measured, not as the plan's fallback.

Per-class results relevant to this feature:

| Class | Tests | Result |
| --- | --- | --- |
| `OwnerNotFoundIntegrationTests` | 6 | all pass |
| `OwnerControllerTests` | 15 | all pass |
| `CrashControllerIntegrationTests` | 2 | all pass, unmodified |
| `PetControllerTests` | 14 | all pass, unmodified |
| `VisitControllerTests` | 4 | all pass, unmodified |
| `ClinicServiceTests` | 12 | all pass |
| `MySqlIntegrationTests` | 2 | all pass (Docker present) |
| `PostgresIntegrationTests` | 2 | all pass (Docker present) |

### 4.2 Nine status probes (plan 10.2)

Required `404 404 404 404 200 200 400 500 500`; measured **exactly that**, in order:

| # | Request | Status |
| --- | --- | --- |
| 1 | `GET /owners/9999` (`text/html`) | 404 |
| 2 | `GET /owners/9999` (`application/json`) | 404 |
| 3 | `GET /owners/9999/edit` | 404 |
| 4 | `POST /owners/9999/edit` (valid form) | 404 |
| 5 | `GET /owners/1` | 200 |
| 6 | `GET /owners/1/edit` | 200 |
| 7 | `GET /owners/abc` | 400 |
| 8 | `GET /owners?page=0` | 500 |
| 9 | `GET /oups` | 500 |

### 4.3 AC-4 body and the no-write check

`Something happened` present (2 occurrences, heading and title), `The requested page was not found.`
present, `An internal server error occurred.` absent, `Whitelabel Error Page` absent. After the POST
sweep, `/owners/9999` is still 404 and owner 1 is still listed as Franklin.

### 4.4 Informational scope-leak probes (plan 10.5)

Not product criteria: their exact status is out of scope per specification section 6, and only a 404
would be a failure. Measured:

| Request | Status |
| --- | --- |
| `GET /owners/9999/pets/new` | 500 |
| `GET /owners/9999/pets/1/edit` | 500 |
| `GET /owners/9999/pets/1/visits/new` | 500 |

No 404. The feature did not leak across controllers, which is the structural consequence of binding
the status to a type only `OwnerController` throws.

### 4.5 First-attempt record

**All specification acceptance criteria and all functional verification commands passed on the first
attempt.** No verification command was re-run to obtain a green result, and no file was corrected in
order to make one pass.

**One planned task-list criterion did not pass literally: the zero-console-error criterion.** Task
list section 3.9 criterion 4 requires that "the recorded console output contains no error", and
section 3.6 makes a console error on `/owners/9999` a stopping condition. One console entry was
observed, so the criterion is **not** satisfied as written and is reported as such rather than as a
pass.

What the entry was: an expected **network-level** report of the intentional 404 status of the *main
document* itself. Investigation established that it reflects no JavaScript, asset-loading, or
rendering defect - the comparison with `/no-such-page` and the correctly rendered layout are set out
in section 5. **No code change was required, and all specification acceptance criteria still pass.**

| Verification | First attempt | Note |
| --- | --- | --- |
| `./mvnw verify` | pass | - |
| `./gradlew build` | pass | - |
| Nine status probes | pass | all nine correct in one run |
| AC-4 body greps | pass | - |
| No-write check | pass | - |
| Scope-leak probes | pass | all 500 |
| Browser render `/owners/9999` | pass | shared layout, correct 404 message |
| **Browser console `/owners/9999`** | **did not pass literally** | one expected main-document 404 entry; criterion 3.9.4 requires none. Not an application defect - section 5 |
| Browser render `/owners/1` | pass | no console output at all |
| `git diff --check`, `git status --short` | pass | - |

**What this revealed about the criterion, not about the code.** Task list criteria 3.9.4 and 3.6 were
drawn **too broadly**: they forbid "a console error" without distinguishing an expected
main-document 404 - which the browser reports for *any* correctly-404 page, including pages this
feature never touches - from the JavaScript, asset-loading, and rendering errors the criterion was
actually written to catch. A criterion that a correct implementation cannot satisfy is a defect in
the criterion. Phrased to its intent it would read: no JavaScript error, no failed subresource load,
and no rendering fault, with the main document's own status excluded. That wording would have passed
on the first attempt with no investigation needed. The task list is left unchanged as a historical
planning artifact, and section 3.3 does not permit editing it.

## 5. Browser evidence (Task 3.4.4)

Playwright MCP, `http://localhost:8098`, app started with
`./gradlew bootRun --args='--server.port=8098 --spring.docker.compose.lifecycle-management=NONE'`.

**`/owners/9999`** - HTTP status 404, page title
`PetClinic :: a Spring Framework demonstration`. The page renders through the shared layout fragment,
not as a bare or unstyled page: the Spring PetClinic header image, the full navigation bar
(Home, Find Owners, Veterinarians, Error), the pets image, the `Something happened...` heading, the
`The requested page was not found.` paragraph, and the VMware Tanzu footer logo. Screenshot captured
and reviewed; it was written to the git-ignored `.playwright-mcp/` directory so the working tree
still holds exactly the two documentation files Task 3 permits.

**Console output for `/owners/9999`, recorded verbatim:**

```
[ERROR] Failed to load resource: the server responded with a status of 404 () @ http://localhost:8098/owners/9999:0
```

This console output is **not** clean: one error entry is present, so the planned zero-console-error
criterion (task list 3.9.4) did not pass literally. See section 4.5.

**It is not an application defect, and here is the evidence.** The entry is an expected network-level
report of the *main document's own* intended 404 status - note the target is the navigated URL itself
at offset `:0`, not a stylesheet, script, or image. Rather than assert that, it was checked against a
404 this feature does not touch: navigating to `/no-such-page` produces the **identical** single
console error and zero warnings. Combined with the correctly rendered layout described above - which
confirms the CSS resolved - this shows no JavaScript error, no failed asset load, and no rendering
fault. Task 3.6's stopping condition exists to catch a broken error page, because `error.html`
renders through the shared layout and a break there would affect every error page; the evidence shows
no such break, which is why the sweep continued. The criterion's over-breadth is analysed in
section 4.5.

**`/owners/1`** - renders normally with no console output at all: the `Owner Information` table
(George Franklin, 110 W. Liberty St., Madison, 6085551023), the `Edit Owner` and `Add New Pet` links,
and the `Pets and Visits` table containing Leo (2010-09-07, cat) with an **empty visit list**. That
empty list is the live confirmation of review-gate defect 2.

The application was stopped afterwards.

## 6. Pre-existing behavior observed but not changed

`templates/error.html:18` renders `<p th:text="${message}">` unconditionally, so the 404 page displays
`Owner not found with id: 9999`. Recorded rather than acted on, for three reasons. It is
**pre-existing and not specific to this feature**: `/oups` displays
`Expected: controller used to showcase what happens when an exception is thrown` and `/no-such-page`
displays `No static resource no-such-page.` through the same line. It is **within the contract**:
constraint 2 states that exception messages are not part of the contract and must not be asserted, and
no test asserts this text. And it is **out of Task 3's permitted scope**: `error.html` is frozen by
plan section 12 and task list 1.5.

Worth a future specification of its own, because the text echoes a client-supplied ID back into the
response body, and `server.error.include-message` is not configured anywhere in
`src/main/resources/`. That is a separate decision, not a Task 3 edit.

A second reporting artifact: surefire's `.txt` summary for `PetControllerTests` and
`PetValidatorTests` prints `Tests run: 0`, because both keep their cases in JUnit `@Nested` classes.
The XML reports show all 14 and all 7 cases executing and passing. A summary line, not a coverage gap.

## 7. Acceptance criteria at close

All eight re-run as a whole-suite regression under both toolchains.

| AC | Verifier | Status |
| --- | --- | --- |
| AC-1 | `unknownOwnerDetailsReturnsNotFound` + probes 1, 2 | pass |
| AC-2 | `unknownOwnerEditFormReturnsNotFound` + probe 3 | pass |
| AC-3 | `unknownOwnerUpdateReturnsNotFoundAndChangesNoData` + probe 4, section 4.3 | pass |
| AC-4 | `unknownOwnerRendersEnglishNotFoundPage` + sections 4.3, 5 | pass |
| AC-5 | `showOwner`, `initUpdateOwnerForm`, and the strengthened `processUpdateOwnerFormSuccess` + probes 5, 6, section 5 | pass |
| AC-6 | `nonNumericOwnerIdReturnsBadRequest` + probe 7 | pass |
| AC-7 | `paginationErrorIsNotTreatedAsNotFound` + probe 8 | pass |
| AC-8 | `CrashControllerIntegrationTests.triggerExceptionHtml`, unmodified + probe 9 | pass |
| Spec section 7 | AC-3 row | pass |

Constraint 5 - both builds keep working, no build file changed - holds: section 4.1.

## 8. Task 2 result, as reported

**Zero functional verification failures, and one cosmetic formatting adjustment.** No assertion
failed, no stopping condition in task list 2.6 fired, and no production file was touched: Task 2 was
test-only by definition and remained so.

The single adjustment was cosmetic - formatting, not behavior. The committed diff corroborates this:
`0c02b98` shows the `processUpdateOwnerFormSuccess` assertion chain reflowed, the one line
`.andExpect(view().name("redirect:/owners/{ownerId}"));` replaced by the same call without its
terminating semicolon plus the new `.andExpect(redirectedUrl(...))` line. That is exactly the reflow
task list 2.8 criterion 3 anticipated when it allowed "no deletions other than the reflow of the
assertion chain", and it is what `./mvnw spring-javaformat:apply` produces when a chain gains a link.
The commit's net effect is 19 insertions and 1 deletion across two test files, no method added or
removed.

The 2.7.2b failing-witness check was the substantive result: with `this.owners.save(owner)` commented
out, `processUpdateOwnerFormSuccess` failed on the `verify`, and `OwnerController.java` was restored
byte-identical afterwards. That is the check review-gate section 8 identified as the highest-leverage
missing gate, and it is now the one assertion in this feature proven to fail when its clause is
violated.

## 9. Task 1 and Task 2 commit sizes: should either have been split further?

| Commit | Files | Insertions | Deletions | Contents |
| --- | --- | --- | --- | --- |
| `7d57293` Task 1 | 3 | 187 | 5 | `OwnerNotFoundException.java` (35 new), `OwnerController.java` (2 lambdas, +2/-5), `OwnerNotFoundIntegrationTests.java` (150 new) |
| `0c02b98` Task 2 | 2 | 19 | 1 | 2 methods in `OwnerNotFoundIntegrationTests`, 2 assertions in `OwnerControllerTests` |

Task 1 is roughly ten times Task 2 by line count. That asymmetry is a poor split signal here, because
the 150 lines are a new test class - additive, reviewable in one pass, and carrying no branching
logic. The production change inside Task 1 is **seven lines across two files**.

**Task 1: correctly sized, should not have been split.** The tempting split is production from test.
It should be rejected: the first commit would then change observable HTTP behavior on three routes with
**zero** coverage, and the second would add tests for behavior already shipped. That inverts the
guarantee a commit boundary is supposed to give - each commit was to be independently committable, and
a behavior change whose tests land later is not independently safe to ship. Splitting the exception
class from the two `orElseThrow` call sites is worse still: the intermediate commit would contain a
dead class, and the reason both sites changed together was precisely so they cannot drift.

**Task 2: defensible, and the better candidate of the two.** At 19 lines it is plainly not too large,
but it is the one commit carrying **two independent rationales**: the AC-6/AC-7 regression boundary,
planned from the Tasks phase, and the AC-5 failing witness, which arrived later from the review gate
and addresses a different criterion in a different file. A purist split would give two commits of
roughly 15 and 4 lines. Not worth it: the two changes share one review context, the commit message
separates the rationales into their own paragraphs, and splitting four lines of assertion into its own
commit is ceremony rather than clarity.

**Verdict: neither commit should have been split further.** If a reviewer insisted on one more
boundary, it belongs inside Task 2 and not inside Task 1 - the opposite of what the line counts
suggest. Line count was the wrong metric; the number of independent reasons to revert is the right one,
and by that measure Task 1 has one reason and Task 2 has two.

## 10. One concrete risk the iterative workflow caught earlier

**The AC-5 failing-witness defect, caught at the gate between Task 1 and Task 2 instead of at Task 3.**

Concretely: `OwnerControllerTests.processUpdateOwnerFormSuccess` asserted a 3xx and the un-expanded
view-name template, `OwnerRepository` was a mock, and no `verify(...).save(...)` existed in the owner
test package. Delete `this.owners.save(owner)` from `OwnerController.processUpdateOwnerForm` and every
suite stayed green - the mock-level test still saw the same view name, `ClinicServiceTests` saves
through the repository without touching the controller, and `PetClinicIntegrationTests` issues only
GETs. AC-5's "the owner is saved" clause was certified by a test that could not observe it.

What the timing was worth, counted in artifacts:

- **Caught at the gate (what happened):** amend three documents mid-stream and add two assertions to a
  test file Task 2 was already opening. Task 2's shape did not change - still test-only, still one
  commit. Cost: one review pass.
- **Caught at Task 3 (the next opportunity):** Task 3 runs both builds and flips the specification's
  `Status:` to implemented. Both builds were green the whole time, because a missing assertion produces
  exactly that. Task 3 has no permission to edit a test file - section 3.3 allows two documentation
  files and section 3.5 says stop if any `src/` file appears. So the defect would have had to either
  reopen a closed workflow across the specification, the plan, the task list and two commits, or be
  waived to preserve the close.
- **Caught after close:** never, by construction. The gap is invisible to every gate this project has
  - both builds, checkstyle, spring-javaformat, nohttp, and the staged-diff reviewer - because all of
  them pass when an assertion is absent.

That is the specific value of iterating with a human gate between commits rather than only at the end:
the cheapest moment to fix a coverage defect is while the file it lives in is still open, and the gate
sat exactly there. A second, smaller instance of the same effect: the Plan phase caught the
`@ExceptionHandler(IllegalArgumentException.class)` reclassification before a line was written, where
discovering it from a red AC-7 test after implementation would have meant redesigning the approach
rather than choosing it.

## 11. What the four-phase workflow cost, honestly

**Cost.** Four documents - specification, plan, task list, review gate - plus this evaluation, for a
production change of **seven lines in two files**. The documents run to roughly 1,600 lines. The
review gate re-derived every load-bearing claim from the repository, and the Plan phase ran the
application by hand before any test existed. For a developer who already knew that
`@ResponseStatus(HttpStatus.NOT_FOUND)` on a dedicated exception is the idiomatic Spring answer, the
code itself is a ten-minute change. The paperwork was the overwhelming majority of the elapsed time,
and the ratio is indefensible if measured against this change alone.

**What it prevented.** Three things, each of which would have shipped without it:

1. A `@ExceptionHandler(IllegalArgumentException.class)` that satisfies every 404 criterion and
   silently reclassifies `GET /owners?page=0` from 500 to 404. Only reading the repository revealed
   that a missing owner and an invalid page index were the same exception type in the same controller.
2. A closed, green, documented workflow certifying that "the owner is saved" while no assertion
   anywhere could detect the save being removed - in a feature about the boundary between writing and
   not writing.
3. An acceptance criterion contradicting the seed data it described, which would have misfired on the
   next person who strengthened it the way its own wording invited.

**The honest verdict.** The phases were not equally worth their cost. The Specify phase earned its
keep by forcing AC-6, AC-7 and AC-8 into existence - the three boundaries the original request never
mentioned and which turned out to be the entire technical difficulty. The Plan phase earned its keep
with one finding, the shared exception type. The Tasks phase was the thinnest: its main product was a
sequencing constraint and a stop boundary, which a competent developer would have arrived at anyway.
The review gate earned more than the Tasks phase did, in a fraction of the words.

Generalising from one 30-line feature would be wrong. What does generalise is narrower and does not
require four phases: **write the boundary criteria before choosing the approach, and for each criterion
clause name the single assertion that fails when it is violated, then prove it once by breaking the
production line.** Those two habits produced all three prevented defects above. Review-gate section 8
identifies the second as the highest-leverage gate missing from the normal workflow, and it takes
about thirty seconds per criterion - which is why it is the part of this exercise worth keeping when
the document scaffolding is not.

## 12. What follows

Nothing in this feature. Each of these needs its own specification: missing-owner and missing-pet 404s
in `PetController` and `VisitController` (currently 500, out of scope here); a considered status for an
invalid page index, which AC-7 knowingly pins to 500 so
`paginationErrorIsNotTreatedAsNotFound` will fail on a future legitimate pagination fix; removal of the
duplicate owner lookup in `showOwner`, whose `orElseThrow` remains unreachable while `findOwner` throws
first; and whether `error.html` should echo exception messages to clients (section 6).
