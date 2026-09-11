# Human Review Gate: HTTP 404 for a Missing Owner - 2026-09

Verdict: **GATE CHANGE REQUIRED**, approved 2026-09-11. Outcome: amend the specification, the plan,
and the task list. No production code and no test code was changed by this gate, and Task 2 was not
executed.

## 1. Gate timing

Held **after Task 1 and before Task 2** of the four-phase workflow.

- Task 1 was complete and committed as `7d57293 Return 404 for a missing owner in OwnerController`:
  `OwnerNotFoundException.java` created, the two `orElseThrow` lambdas in `OwnerController` converted,
  and `OwnerNotFoundIntegrationTests` created with its four methods.
- Task 2 (the AC-6 and AC-7 regression-boundary tests) had not started, and does not start as part of
  this gate.

Artifacts reviewed side by side: `spec-owner-not-found-2026-09.md` and
`plan-owner-not-found-four-phase-2026-09.md`, with `tasks-owner-not-found-four-phase-2026-09.md` read
only to establish the Task 2 boundary. Every load-bearing claim was re-derived from the repository
rather than taken from the documents.

## 2. Finding: AC-5 had no failing witness

AC-5's third bullet requires that `POST /owners/1/edit` with a valid form produce "a 3xx redirect to
`/owners/1` **and the owner is saved**". Plan section 7 credited this to
`OwnerControllerTests.processUpdateOwnerFormSuccess` with the parenthetical "Confirmed already present
at `OwnerControllerTests.java:199-221` and `:245-257`". The method is present. The coverage was not:

1. **"and the owner is saved" had no assertion that could fail.** `processUpdateOwnerFormSuccess`
   (`OwnerControllerTests.java:211-221`) asserted only `status().is3xxRedirection()` and
   `view().name("redirect:/owners/{ownerId}")`. `OwnerRepository` is a `@MockitoBean` (`:70-71`), and
   there was no `verify(...).save(...)` anywhere in the owner test package. Deleting
   `this.owners.save(owner)` at `OwnerController.java:157` would have left the **entire suite green**:
   the mock-level test still sees the same view name, `ClinicServiceTests` saves through the repository
   without touching the controller, and `PetClinicIntegrationTests` issues only GETs (`:47-63`). The
   save was *inferable* from the asserted view name - `OwnerController.java:145-159` reaches that
   string only after the save - but that coupling is incidental to the current control flow, not
   asserted.
2. **The redirect target was not asserted.** The test asserted the un-expanded view-name template
   `redirect:/owners/{ownerId}`, never the resolved `Location`. AC-5 names `/owners/1`. The repository
   already had the direct idiom in use at `OwnerControllerTests.java:275`.
3. **AC-5's GET wording was unsatisfiable as written.** It required the `owner` model attribute to
   contain "the stored owner, pets, and visits", asserted against the Mockito fixture `george()`
   (`:73-90`) plus a synthetic visit (`:100-102`). In the seeded data, owner 1 owns exactly one pet,
   Leo (`db/h2/data.sql:36`), and every seeded visit belongs to pets 7 and 8, which are owner 6's
   (`data.sql:50-53`). **Seeded owner 1 has no visits at all.** The clause passed only because the
   fixture contradicted the data it stood for, and would have failed outright if anyone had moved it to
   the integration level - the obvious next step, which the word "stored" invited.

Why this was worth stopping for rather than noting in passing: Task 2 was defined to grant AC-5
"formal credit", and Task 3 then flips the specification's `Status:` line to implemented. Both builds
would have been green throughout, because a missing assertion produces exactly that. The defect would
have closed the workflow as verified, and the confusing part is that nothing would have looked wrong.

## 3. Unsupported assumption: the test mapping read as an exhaustive ceiling

Named exactly: **plan section 3, the row rejecting `OwnerNotFoundExceptionTests`** - *"section 8 of the
specification enumerates exactly one new test class, and the integration tests already prove the
observable 404 on all three routes"* - and the row immediately below it, *"Section 8 declares that
class unmodified"*.

The assumption is that specification section 8's AC-to-test mapping is a **ceiling on verification
rather than a floor**. The specification never said that. Section 8 stated that
`OwnerNotFoundIntegrationTests` is new and that the listed existing classes are unmodified; it never
claimed the mapping was complete or that no further assertion could be added.

Consequence, and it was already realised: any coverage gap inside section 8 became unfixable by
construction. Plan section 3 rejected both obvious remedies by citing section 8, plan section 7 then
reported AC-5 as satisfied because a row existed for it, and task list sections 2.3 and 2.5 permitted
Task 2 exactly one file while freezing `OwnerControllerTests`. Every route to a failing witness was
closed by an approved artifact. The gate's own remediation also ran into it: correcting AC-5 required
editing the specification, which plan section 12 froze.

Both the specification and the plan now state explicitly that the mapping is a minimum, and that a
criterion clause without a failing witness is a defect in the mapping.

## 4. Decision

**Amend the specification and the plan** (and the task list that derives from them). Not "accept the
assumption": that would knowingly close a criterion whose most consequential clause - *the write
actually happens* - has no assertion that fails when it stops happening, in a feature whose entire
subject is the boundary between "wrote nothing" and "wrote". Not "amend the plan only": the plan is
faithful to its authority, so patching plan section 7 alone would leave plan and specification in
conflict on a document the plan treats as authoritative.

## 5. Why strengthening the existing controller test beats a new integration test

The gate's first proposal was a new `existingOwnerUpdateRedirectsAndPersists` method in
`OwnerNotFoundIntegrationTests`. It was rejected in review in favour of adding two assertions to the
existing `OwnerControllerTests.processUpdateOwnerFormSuccess`, which is smaller and clearer:

1. **Cohesion.** `OwnerNotFoundIntegrationTests` exists for missing-owner behavior; its class Javadoc
   says so. A successful existing-owner update belongs to the class that already owns owner-1 happy
   paths, next to the sibling cases `processUpdateOwnerFormUnchangedSuccess` and
   `processUpdateOwnerFormWithIdMismatch`.
2. **No state-changing request in a read-only class.** Every existing method in
   `OwnerNotFoundIntegrationTests` is a non-mutating probe, and one of them asserts a global
   no-write invariant over `owners.count()` and owner 1's stored fields. Adding a real `POST` that
   saves owner 1 to that class would put a mutation and an unchanged-data invariant in the same
   test class against the same shared H2 context, coupling two tests through persistent state for no
   gain. That is a durable maintenance hazard, not a theoretical one.
3. **Smaller diff, no new surface.** Two assertion lines inside one method, versus a new test method
   with its own form fixture, `HttpEntity`, header setup, and post-request repository re-read. No new
   import is needed either: `redirectedUrl`, `verify`, and `any` are already statically imported in
   `OwnerControllerTests`, and `TEST_OWNER_ID` is already `1`.
4. **The witness lands where the criterion is read.** A future reader checking AC-5 opens
   `processUpdateOwnerFormSuccess`. Putting the redirect-target and persistence assertions anywhere
   else leaves that method looking like the whole of AC-5's POST coverage while it is not.
5. **It keeps the fix inside Task 2's existing shape.** Task 2 was already test-only and already the
   task that credits AC-5. The cost is two assertions in a second test file, not a new task.

What is *not* claimed: a mocked-repository `verify` proves the handler calls `save`, not that a row
changed in the database. That is the right level for AC-5, which is a non-regression criterion about
controller behavior; end-to-end persistence is already exercised by `ClinicServiceTests`.

## 6. Concrete document changes

### `spec-owner-not-found-2026-09.md`

- **AC-5, first bullet.** "contains the stored owner, pets, and visits" replaced by
  implementation-neutral wording: the `owner` model attribute "contains the requested owner, including
  any associated pets and their visits. This clause does not require the seeded owner 1 to have any
  particular pet or visit." The replacement names no class and no mechanism, so it constrains the
  observable model contents only, and it no longer implies that owner 1 must have a visit.
- **Section 8 preamble.** The blanket claim that both existing classes are unmodified is removed.
  `CrashControllerIntegrationTests` remains unmodified. `OwnerControllerTests` is stated to be **not**
  unmodified: it keeps every existing method, fixture, and assertion, and Task 2 adds the two
  assertions named in the AC-5 row. The mapping is declared a minimum, not a ceiling.
- **Section 8, AC-5 row.** Split by clause. `showOwner` and `initUpdateOwnerForm` stay existing and
  unchanged; `processUpdateOwnerFormSuccess` is marked "existing, strengthened in Task 2", with
  `redirectedUrl("/owners/1")` and `verify(this.owners).save(any(Owner.class))` named as the added
  assertions and the second identified as the failing witness for "the owner is saved".

No functional requirement, no acceptance-criterion outcome, no constraint, no route, and no
input/output example changed. `Status:` is still `proposed`; Task 3 closes it.

### `plan-owner-not-found-four-phase-2026-09.md`

- **Header.** Records that this gate amended the plan, and what it touched.
- **Section 3.** The `OwnerNotFoundExceptionTests` row no longer argues from "exactly one new test
  class"; it argues that the class would add no failing witness, and states that section 8 is a floor.
  The `OwnerControllerTests` row is narrowed to reject *new missing-owner MockMvc cases only*, and says
  so explicitly, so it no longer bars Task 2's two AC-5 assertions.
- **Section 4.** `OwnerControllerTests.java` added as a fourth file, Task 2 only, with the two
  assertions spelled out and the no-new-import note. "Three files in the implementation diff" becomes
  four across the feature, three in Task 1 and one in Task 2.
- **Section 7, AC-5 row.** Rewritten by clause, with the rationale that the previous two assertions
  checked the un-expanded view-name template and left the save to a mocked repository, and with the
  concrete evidence that deleting `OwnerController.java:157` left the whole suite green.
- **Section 8, risk 2.** "AC-5 via the unmodified `OwnerControllerTests`" corrected: all three methods
  keep every existing assertion, Task 2 only adds two, and the failure signal is now "an existing
  assertion, the `george()` fixture, or a mock stub has to change".
- **Section 8, risk 6, section 10.5, section 11 criterion 6.** The `PetController` and `VisitController`
  probes are demoted from "must stay 500" to an informational scope-leak check: their exact status is
  out of scope per specification section 6, the check fails only on a 404, and any other status is
  recorded rather than failed.
- **Section 9.4 comment, section 11 criteria 7 and 9.** "unmodified" and "exactly three files"
  corrected for the Task 2 edit.
- **Section 12.** The contradiction is resolved. `OwnerControllerTests.java` is frozen apart from the
  two added assertions. The blanket freeze on the specification now names its two exceptions
  explicitly: this gate's AC-5 amendments, and the Task 3 `Status:`-line rewrite that task list section
  3.4.5 always required.
- **Section 13 item 8.** "Whether `OwnerControllerTests` gains cases - no" refined to: no new method,
  two added assertions, decided at this gate.

### `tasks-owner-not-found-four-phase-2026-09.md`

- **Dependency section and traceability table.** Record the gate and the strengthened AC-5 row.
- **Section 1.5.** Notes that Task 1 changes neither existing test class and Task 2 adds two
  assertions to one of them.
- **Section 1.12, the Task 2 prediction.** Now includes the two assertions.
- **Sections 2.1 to 2.3.** Goal and criteria updated; Task 2 modifies **exactly two test files**,
  `OwnerNotFoundIntegrationTests.java` for AC-6 and AC-7 and `OwnerControllerTests.java` for the two
  assertions, with an explicit statement that no existing-owner update test is added to
  `OwnerNotFoundIntegrationTests`.
- **Section 2.4.** New step 2.4.3 specifies the two assertions and forbids any new method, import,
  fixture, or stub; the later steps renumber, and 2.4.5 checks both files' shapes.
- **Section 2.5.** Freezes everything else in `OwnerControllerTests` by name.
- **Section 2.6.** Three stopping conditions added, including "do not relax the assertion to match the
  observed value" if `redirectedUrl` fails.
- **Section 2.7.** Adds the Gradle run of `OwnerControllerTests` and a 2.7.2b failing-witness check:
  comment out the save, confirm the test now fails on the `verify`, restore, and confirm
  `git diff` on `OwnerController.java` is empty.
- **Sections 2.8 and 2.9.** Success criteria and commit message rewritten for two files and the
  failing-witness check.
- **Sections 2.10, 3.4.3, 3.4.6, 3.9 criterion 3.** Task 3 aligned: the sibling-controller probes are
  informational, and the evaluation document must cover this gate's finding.

Tasks 1 and 3 were not executed. Task 1 remains committed as it was; Task 3 remains unstarted.

## 7. What the gate caught

One material defect and two consistency faults:

1. **AC-5's POST clause had no failing witness, and the workflow was structured to certify it anyway.**
   Demonstrable in seconds: remove `OwnerController.java:157` and the suite stays green. Caught at Task
   2 this would have meant amending three artifacts mid-stream; caught at Task 3, after a green
   `./mvnw verify` and the `Status:` flip, it would have meant reopening a closed workflow.
2. **AC-5's "stored owner, pets, and visits" contradicted the seed data**, which would have misfired on
   the next person who tried to strengthen the criterion the way its wording suggested.
3. **Plan section 12 froze the specification that task list section 3.4.5 must edit**, and plan section
   11 criterion 6 promoted explicitly out-of-scope sibling-controller behavior to a pass/fail product
   criterion that plan risk 6 argues against. Both would have surfaced later as a phantom stop
   condition.

What the gate did **not** catch, stated honestly: nothing is wrong with the Task 1 production change or
its core reasoning. The `@ResponseStatus` mechanism, the non-widening argument for AC-7
(`PageRequest.of(-1, 5)` at `OwnerController.java:133`, thrown from `processFindForm` and never from a
changed line), the `@ModelAttribute`-ordering claim behind FR-4, `count()` availability via
`OwnerRepository extends JpaRepository`, the `triggerExceptionHtml` method name at
`CrashControllerIntegrationTests.java:78` asserting 500 at `:84`, and the risk-8 note that `error.404`
exists only in the base bundle all hold. AC-1 through AC-4 and AC-6 through AC-8 are covered at
defensible levels. One inherited over-constraint is recorded but not changed: AC-7 pins the exact 500
for `?page=0`, so `paginationErrorIsNotTreatedAsNotFound` will fail on a future legitimate pagination
fix that specification section 6 leaves out of scope - the specification accepted that knowingly, with
a stated rationale, so the plan is faithful in implementing it.

No build was re-run during this gate. Statements about test outcomes rest on reading the committed
code, not on a fresh green run.

## 8. Highest-leverage missing gate in the normal workflow

A **failing-witness gate between "the suite is green" and "the criterion is credited"**: for each
acceptance-criterion clause, name the single assertion that fails if that clause is violated, then
prove it once by breaking the production line and watching that assertion fail.

Every artifact in this workflow traced criteria *forward* to test names - specification section 8, plan
section 7, task list traceability - and all three tables agreed with each other. That is precisely why
the AC-5 gap survived four documents and one human review of each: a mapping table cannot distinguish
"asserted" from "mentioned in a test that happens to pass". Applied to AC-5, this gate takes about
thirty seconds and fails immediately. It is higher-leverage than another prose review pass, because it
is the only check in the workflow that mock-level coverage cannot satisfy by accident. Task 2 section
2.7.2b now carries exactly this check for the two assertions it adds.

## 9. Confirmation

- **Task 2 was not executed.** No `nonNumericOwnerIdReturnsBadRequest`, no
  `paginationErrorIsNotTreatedAsNotFound`, no assertion added to `processUpdateOwnerFormSuccess`, no
  `./mvnw verify`, no `./gradlew build`.
- **No production code and no test code was modified by this gate.** The four files changed are all
  Markdown: this document, the specification, the plan, and the task list.
- **Nothing was staged or committed.** The working tree holds the four documentation changes for
  review.
