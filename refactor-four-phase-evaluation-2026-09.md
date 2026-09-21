# Refactor through the four-phase loop: evaluation

Branch: `homework/refactor-four-phase-loop`. Date: 2026-09-21.
Target function: `PetController.processCreationForm`
(`src/main/java/org/springframework/samples/petclinic/owner/PetController.java`).
Named structural move: Extract Method, applied three times.

This document is an evaluation artifact only. It changes no production code, no
test and no configuration.

## 1. Commit sequence

| SHA | Subject | Files | Lines |
|---|---|---|---|
| `e1c8a35` | Characterize pet creation form behavior | `PetControllerTests.java` | +82 |
| `f3067f7` | Extract duplicate-name check in pet creation form | `PetController.java` | +7 / -1 |
| `4b0db9d` | Extract future birth date check in pet creation form | `PetController.java` | +5 / -1 |
| `183ff67` | Extract duplicate constraint handling in pet creation form | `PetController.java` | +9 / -5 |

The sequence enforces three invariants. The characterization commit precedes
every production change. Each production commit touches exactly one file and is
independently revertible: reverting any one of them restores the code it moved
and deletes the helper it added, and the helper is called from exactly one place
and defined in the same commit. The three edited regions are textually disjoint
-- the duplicate-name condition, the birth-date condition and the `catch` body
-- but reverting them in an arbitrary order was not tested, so no claim is made
about that.

Verification recorded at each gate:

- `./mvnw -q spring-javaformat:apply`, exit 0 every time.
- Focused suite `PetControllerTests`: 19 tests, 0 skipped, 0 failures, 0 errors.
- Full `./gradlew build`: 23 suites, 96 tests, 0 skipped, 0 failures, 0 errors,
  including `checkFormatMain`, `checkFormatTest`, `checkstyleMain`,
  `checkstyleTest` and `checkstyleNohttp`.
- `git diff --check` clean.
- `git hash-object` on `PetControllerTests.java` equal to
  `2d902ac3f215243b15739bb1b7c510625bb1ca26` at every gate after `e1c8a35`,
  proving no committed characterization test was edited to accommodate an
  implementation.

## 2. Overfitting-review verdict

**APPROPRIATELY SCOPED**, with one demonstrated coverage hole.

The review was performed by a reviewer with no prior context on the work,
running in a disposable git worktree, because the tests under review were
written in the same session that produced the refactor and cannot be assessed
unanchored from inside it.

All five characterization tests were killed by the mutation aimed at them, 5 of
5, which is the evidence that none of them passes by accident. Comparing
`e1c8a35`'s `PetController` against `183ff67`'s, the reviewer found no
differences detected in the checked behaviors: view names, field error codes,
default messages, the order in which errors are inserted into the
`BindingResult`, repository interactions, exception identity and wrapping, flash
attributes, clock-read count and position, and short-circuit order inside both
moved expressions. That is a statement about what was examined, not a proof that
nothing else changed.

Overfitting is low. Only two of the five tests reach below the HTTP surface, and
both are defensible.

- `processCreationFormSavesOwnerExactlyOnceOnSuccess` verifies a mock
  interaction rather than a response, making it the most implementation-coupled
  of the five. M5 shows the coupling is load-bearing rather than incidental:
  swapping `saveAndFlush` for `save` breaks three tests, because without a flush
  inside the `try` the constraint violation never surfaces there at all. The
  `times(1)` cardinality is the weakest part of the assertion.
- `processCreationFormWithoutBirthDateReportsRequiredNotTypeMismatch` casts the
  model entry at `BindingResult.MODEL_KEY_PREFIX + "pet"`. No MockMvc matcher
  can express "exactly these codes and no others", and proving the absence of
  `typeMismatch.birthDate` is that test's entire purpose.

`processCreationFormPropagatesUnrelatedDataIntegrityViolation` is the strongest
of the five: it asserts reference identity of the original exception through the
cause chain, so it tolerates any servlet wrapper and would survive a rewrite of
`isDuplicatePetNameViolation` that classified violations by SQL error code
instead of constraint-name substring.

The hole is M8. Flipping `isAfter` to `!isBefore`, so that a birth date of
exactly today is rejected, survived with no failures. That boundary belongs to
the one rule Step 2 reshaped. M9 and M11 are narrower gaps. M12 survived
correctly and is a false alarm.

## 3. M1-M12 mutation evidence

Method: one exact-string change to `PetController.java` at a time, all 19 test
cases run, the mutation reverted, and the worktree confirmed clean afterwards.
12 numbered mutations over 13 runs (M3 was run in two variants).

| # | Mutation | Intended behavior / test | Result | Exact failing test | Conclusion |
|---|---|---|---|---|---|
| M1 | Duplicate check made case-sensitive (`getPet(name, true)` -> stream `equals`) | Case-insensitive duplicate detection; `processCreationFormWithDuplicateNameIgnoringCase` | KILLED | `...WithDuplicateNameIgnoringCase`: `AssertionError: No BindingResult for attribute: pet` | Non-vacuous, but the signal is indirect: the request redirected, so no `BindingResult` existed. Asserting `status().isOk()` before the model matcher would fail more legibly. |
| M2 | Swallow non-matching `DataIntegrityViolationException` (return form view instead of `throw ex`) | Unrelated violation propagates; `processCreationFormPropagatesUnrelatedDataIntegrityViolation` | KILLED | `...PropagatesUnrelatedDataIntegrityViolation`: `AssertionError: Expecting actual not to be null` | The rethrow branch is genuinely pinned; the `catchThrowable` assertion fires when nothing is thrown. |
| M3 | Flash text changed to `"New Pet Added"` | Success flash message; `processCreationFormAddsFlashMessageOnSuccess` | KILLED | `...AddsFlashMessageOnSuccess`: `Flash attribute 'message' expected:<New Pet has been Added> but was:<New Pet Added>` | Exact message text is pinned. |
| M3b | Flash attribute removed entirely | Same | KILLED | `...AddsFlashMessageOnSuccess`: `expected:<New Pet has been Added> but was:<null>` | Presence is pinned as well as content. |
| M4 | `saveAndFlush` called twice | Exactly one persistence write on success; `processCreationFormSavesOwnerExactlyOnceOnSuccess` | KILLED | `...SavesOwnerExactlyOnceOnSuccess`: `TooManyActualInvocations: Wanted 1 time` | Cardinality is pinned. |
| M5 | `saveAndFlush` replaced with `save` | Same | KILLED, plus 2 collateral | `...SavesOwnerExactlyOnceOnSuccess`: `WantedButNotInvoked`; also `...PropagatesUnrelatedDataIntegrityViolation` and pre-existing `...WithDataIntegrityViolation` | The method choice is load-bearing, not stylistic: a `DataIntegrityViolationException` may occur during `saveAndFlush`, and without the flush inside the `try` it never reaches the `catch`. Pinning the exact method is justified, not overfitted. |
| M6 | `!= null` birth-date guard dropped | Absent birth date yields `required` only; `processCreationFormWithoutBirthDateReportsRequiredNotTypeMismatch` | KILLED | `...WithoutBirthDateReportsRequiredNotTypeMismatch`: `ServletException: Request processing failed: NullPointerException: Cannot invoke "java.time.LocalDate.isAfter(...)" because the return value of "Pet.getBirthDate()" is null` | The guard's position as the leading operand is pinned; dropping it turns an omitted date into a 500. |
| M7 | Birth date rejected when absent (`== null \|\|`) | Same | KILLED | `...WithoutBirthDateReportsRequiredNotTypeMismatch`: `Expecting actual: ["required","typeMismatch.birthDate"] to contain exactly: ["required"]` | The absence assertion works; this is the test's core purpose. |
| M8 | Boundary: `isAfter` -> `!isBefore`, rejecting a birth date of today | Today must be accepted; no test targets it | SURVIVED | no failure (0 of 19) | Coverage gap. The boundary of the one rule Step 2 reshaped is unpinned. It is not deterministically covered at `@WebMvcTest` level with the current clock design, because `LocalDate.now()` is still read in the controller. |
| M9 | The two `rejectValue` blocks swapped, so the birth-date error is inserted before the duplicate-name error | Order in which errors are inserted into the `BindingResult` | SURVIVED | no failure | Gap: insertion order is unpinned. Whether that order changes anything a user sees was not demonstrated and is not claimed here. |
| M10 | Literal redirect template replaced with computed `"redirect:/owners/" + owner.getId()` | Literal URI template preserved; `processCreationFormSuccess` (pre-existing) | KILLED | `processCreationFormSuccess`: `View name expected:<redirect:/owners/{ownerId}> but was:<redirect:/owners/null>` | Pre-existing coverage already protects the literal, and shows concretely why it must not be "simplified". |
| M11 | `duplicate` default message `"already exists"` changed to `"dup"` | Third argument of `rejectValue` | SURVIVED | no failure | Gap: every test asserts only the error code, never the default-message argument. `183ff67` moved this string into a new method, where a typo would go unnoticed. What the default message affects at render time was not demonstrated. |
| M12 | `pet.isNew()` conjunct dropped | Creation-path-only semantics | SURVIVED | no failure | Not a gap; a false alarm. On `POST /pets/new` there is no `petId`, so `findPet` returns a new `Pet` and `initPetBinder` disallows binding `id`; `pet.isNew()` cannot be false on the creation path, so the conjunct cannot change the outcome. Consequence worth noting: the comment above `isDuplicateNameForNewPet` documents a distinction no test can detect. |

Score: 9 runs killed, 4 survived, of which one (M12) is a false alarm. All five
characterization tests were killed by their intended mutation.

## 4. PR description

**Preserved.** `processCreationForm` goes from 31 physical lines to 27, with
three helpers, including two predicates, replacing inline logic; every
observable held. The method signature, annotations and
`POST /owners/{ownerId}/pets/new` mapping are unchanged; the error view
`pets/createOrUpdatePetForm` and the literal `redirect:/owners/{ownerId}` both
survive verbatim, and the literal is load-bearing, since M10 shows that
computing the URL instead yields `redirect:/owners/null`. Field error codes
(`duplicate` on `name`, `typeMismatch.birthDate`), the `"already exists"`
default message, the order in which errors are inserted into the
`BindingResult`, and the flash attribute `"New Pet has been Added"` are all
byte-identical. Validation errors cause no persistence call, because the
`result.hasErrors()` gate returns the form view before the `try` is entered,
while a `DataIntegrityViolationException` may occur during `saveAndFlush`, and
that path is unchanged: a violation matching the duplicate-name constraint
rejects the `name` field and returns the form view, and any other violation is
rethrown as the same unwrapped instance. Exactly one system-clock read remains,
at the same unconditional position ahead of the validation gate, with the
extracted predicate receiving that value as a parameter rather than reading the
clock itself. `processUpdateForm` is byte-identical. Across these dimensions, an
independent reviewer found no differences detected in the checked behaviors.

**Risked.** Step 3 was the only move labelled MEDIUM, because it pushed control
flow across a method boundary: `rejectDuplicateNameOrRethrow` returns a view
name and can also throw, so wrapping the rethrow, swallowing it, returning the
redirect instead of the form view, or losing the caller's `return` would each
have changed observable behavior, and the last would book a pet whose save had
failed and still show the success message. Steps 1 and 2 risked less
mechanically but were not free: hoisting an expression can silently reorder a
short circuit, and dropping the leading `!= null` guard on the birth date
converts an omitted date into a `NullPointerException` and a 500, as M6
demonstrates. The labels turned out to be inverted with respect to detection,
and Step 2 carries the one real gap: it reshaped the future-birth-date rule,
whose boundary, a birth date of exactly today, which must be accepted, is
unpinned, and M8 (`isAfter` changed to `!isBefore`) survived with no failures.
That boundary is not deterministically covered at `@WebMvcTest` level with the
current clock design, because `LocalDate.now()` is still read inside the
controller, so the `referenceDate` parameter the step introduced is a seam
nothing currently exercises. Two narrower gaps sit alongside it: the
`BindingResult` insertion order (M9) and the `"already exists"` default-message
argument (M11), the latter a string that Step 3 moved into a new method. The
refactor also left two genuine duplications, since the birth-date condition and
the `catch` body now exist extracted in the creation path and inline in
`processUpdateForm`.

**Mitigated.** A characterization commit landed first and was never edited
afterwards: `PetControllerTests.java` was verified byte-identical to `e1c8a35`
at every subsequent gate, so no test was bent to accommodate an implementation.
Each refactor was previewed as an exact diff and approved before application,
then verified with the focused suite, the full 96-test build including
checkstyle, nohttp and format checks, and `git diff --check`; each was committed
alone, touching one file, and each is independently revertible.
`PetClinicConcurrencyTests` exercised the extracted `catch` over real HTTP with
two racing requests on H2. Every edit was anchored on text unique to the
creation path, so the update path could not be matched by accident, and that was
confirmed by reading it back after each change. Three reviews ran at the commit
gates, and the two duplications were pre-declared in the previews, raised by the
reviewer exactly as predicted, and recorded as scope decisions in the commit
messages rather than left looking like oversights. Finally, a fresh-context
reviewer mutation-tested the suite in a disposable worktree: 12 mutations over
13 runs, with all five characterization tests killed by the mutation aimed at
them, which is the evidence that they are non-vacuous. That mitigation does not
extend to M8, M9 or M11, which survived, so behavior preservation in the
dimensions they cover rests on diff inspection rather than test evidence.

## 5. Workflow evaluation and retrospective answers

### What each phase bought

Phase 1 earned its keep twice. It rejected `processFindForm` because its
extraction targets are the same lines the PAID grid logs as D5, which would have
pulled the work into the excluded vet module, and it rejected
`processUpdateForm` because its awkwardness lives in in-place mutation of a
Hibernate-managed entity, a failure mode the mocked `@WebMvcTest` suite
structurally cannot observe. Both were avoided by reading before writing.

Phase 2 mattered because three of the five branches it pinned had no coverage at
all beforehand, including the rethrow that Step 3 went on to move. Careful diff
review might well have caught a slip in those areas; the tests made detection
automatic rather than dependent on attention.

Phase 3's one-commit-per-move discipline kept every gate small. The largest diff
under review was nine lines.

Phase 4 produced evidence the earlier phases did not. M8's survival is the kind
of gap inspection can find in principle but did not find here across three
previews and three reviews, whereas running the mutation surfaced it
immediately.

### Where the loop leaked

The most instructive failure is one the loop caused. Two plan constraints, "use
exactly one named structural move, Extract Method" and "do not add today or
tomorrow controller tests, because they can cross midnight", were each
individually correct, and together they left the `isAfter` boundary unpinnable.
Dropping the midnight-crossing assertions was right; they really were
nondeterministic. But the natural replacement was a test of the rule with
literal dates, and that was ruled out by the single-move constraint.
Deterministic coverage requires an additional testability or design change
beyond the approved Extract Method-only plan, such as extracting a directly
testable rule or injecting/isolating the clock. Step 2 therefore built a
`referenceDate` seam and had no sanctioned way to use it. The gap follows from
the plan, not from a slip in execution.

### Friction worth fixing

`/review-changes` cost three wasted cycles: it hit its 8-turn cap twice,
returning nothing once and a bare preamble once, and it cannot be
model-invoked, so every gate needed a manual hand-off. `.claude/agents/reviewer.md`
sets `maxTurns: 8` on haiku for a job that must read `CLAUDE.md`, fetch the diff
and inspect neighbouring code. Raising the cap, or pre-loading the staged diff
into the prompt so the agent does not spend turns fetching it, addresses the same
cause that resuming-with-narrowed-scope fixed every time.

Separately, the reviewer raised the identical duplication finding at both the
Step 2 and Step 3 gates. That was cheap only because both previews had
pre-declared it, which is a pattern worth keeping: announce the residue before
the reviewer finds it and the gate becomes a confirmation rather than a debate.

### Which initially LOW step proved riskier, and why

Step 2, the birth-date extraction. Mechanically it was the most trivial of the
three, one condition moved and one parameter renamed, and it caused no defect.
But it sits on the weakest part of the safety net: M8 proves that a plausible
mistake in exactly that expression, `isAfter` changed to `!isBefore` or an
off-by-one at the boundary, would have shipped silently, and M6 shows the
adjacent hazard that dropping the null guard turns an omitted date into a 500.
Step 3, labelled MEDIUM, was by contrast the best-protected step in the set: its
rethrow branch is pinned by identity, `saveAndFlush`-related mutations kill three
tests at once, and `PetClinicConcurrencyTests` drives its `catch` over real
HTTP.

The labels were inverted because the grading considered intrinsic code
complexity, how easy the change is to get wrong, and ignored detection
probability, whether anything would report the mistake. Risk is the product of
the two. Grading both would have made Step 2 MEDIUM with an explicit note that
its boundary was unpinned, and the plan would then have had to either add a
unit-level test or accept the gap knowingly.

### The signal for diagnosing forward versus reverting

The deciding signal was whether a failing test was one the preview had
predicted. A previewed mechanical diff that failed a test named as relevant,
with a message matching a slip visible in the diff such as an inverted operator,
a typo or a wrong constant, would be diagnosed forward, because the fault is
inside the hunk already under inspection. A failure in a test not predicted to
be involved would mean the move was not what it appeared to be, and the correct
response is to restore the file, re-read, and re-preview rather than patch. Two
further conditions would have forced a revert regardless of the message: needing
to touch anything outside the previewed hunks, or needing to edit a committed
characterization test.

The cost asymmetry made this easy. Each step was a single unstaged file before
its gate, so reverting cost one command and lost nothing, while debugging
forward risked accreting unplanned changes into a commit that was meant to be
one mechanical move. In practice the signal never fired: all three steps were
green on first application.

### The unplanned fixes deliberately stopped

Eight, each identified and left alone.

1. Unifying `processUpdateForm`'s birth-date condition with `isFutureBirthDate`,
   requested by the reviewer at the Step 2 gate.
2. Unifying its `catch` body with `rejectDuplicateNameOrRethrow`, requested
   again at the Step 3 gate.
3. The MySQL constraint-name defect: an unnamed unique key means
   `isDuplicatePetNameViolation` can never match under the `mysql` profile.
4. The missing-owner inconsistency: `PetController.findOwner` and `findPet`, and
   `VisitController.loadPetWithVisit`, throw `IllegalArgumentException` and
   yield 500 where `OwnerController` returns 404.
5. `OwnerNotFoundIntegrationTests`, which asserts that `/owners?page=0` returns
   500 and so cements an unhandled `IllegalArgumentException` as expected
   behavior.
6. The `.claude/settings.json` hook defects: the unanchored `Write|Edit` matcher
   that also matches `NotebookEdit`, and the `.env` regex that contradicts its
   own committed evidence file.
7. The unused `ModelMap` parameter and the GET-time aggregate mutation in
   `initCreationForm`.
8. The homework markdown artifacts accumulating at the repository root.

The first two are behavior-preserving and would have been legitimate refactors;
they were stopped only because they fall outside `processCreationForm`. The rest
are bug fixes or configuration changes, which a refactor must not smuggle in.

## 6. Remaining risks and deliberately deferred work

### Coverage gaps

| Gap | Evidence | Fix it would take |
|---|---|---|
| Birth date equal to today is unpinned, and it must be accepted | M8 survived, 0 failures | A test of the rule with literal dates, past and future. Deterministic coverage requires an additional testability or design change beyond the approved Extract Method-only plan, such as extracting a directly testable rule or injecting/isolating the clock |
| `BindingResult` error insertion order is unpinned | M9 survived | Assert both field errors and their order in one test |
| The `"already exists"` default-message argument is unpinned | M11 survived | Assert the default message, not only the `duplicate` code |
| Wall-clock dependence in the only test of the birth-date rule | `processCreationFormWithInvalidBirthDate` calls `LocalDate.now().plusMonths(1)`; pre-existing, and CLAUDE.md forbids this shape | Same fix as the boundary gap |
| `@Valid` and `PetValidator` composition is unpinned | Nothing covers a name longer than 30 characters yielding `size`, or a blank name and a future birth date producing both errors together | Add a composition test |
| M1's failure signal is indirect | `AssertionError: No BindingResult for attribute: pet` | Assert `status().isOk()` before the model matcher |

### Deliberately deferred

- The two duplications in `processUpdateForm`: the birth-date condition and the
  `catch` body. Unifying both is behavior-preserving and would make one scoped
  commit. The plan excluded it, and that choice was confirmed at the Step 2
  gate.
- The comment above `isDuplicateNameForNewPet`, which asserts
  creation-versus-update semantics that no test can distinguish, since
  `pet.isNew()` cannot be false on the creation path. If the helper were ever
  reused on the update path, nothing would catch it; the comment is doing work
  the suite is not.

### Observed bugs, out of scope for a refactor

- MySQL's unnamed unique key means a duplicate pet name surfaces as HTTP 500
  under the `mysql` profile only. H2 and Postgres name the constraint; MySQL
  does not.
- `PetController.findOwner` and `findPet`, and `VisitController.loadPetWithVisit`,
  throw `IllegalArgumentException` and yield 500 where `OwnerController` returns
  404.
- Two OPEN observations with no demonstrated external failure: the GET that
  mutates the loaded aggregate, and the rejected pet left attached to the
  in-memory aggregate after a violation.

### Standing caveat

What was verified is that no differences were detected in the behaviors listed
in section 2, under a suite that leaves M8, M9 and M11 undetected. Behavior
preservation outside those checked dimensions rests on diff inspection, not on
test evidence.
