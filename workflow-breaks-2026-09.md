# Where WORKFLOW.md Broke - 2026-09

Observations from applying WORKFLOW.md to the owner-details single-lookup task
(`spec-owner-details-single-lookup-2026-09.md`). Each item is classified as a **wrong rule**
(the rule as written produces the wrong answer) or a **rule needing enforcement** (the rule
is right, nothing measures or blocks on it). `WORKFLOW.md` was left unmodified until the
closing gate; the validated items below are the inputs to that revision.

## Preliminary observations (before implementation)

### 1. The one-page rule was not enforced

**Observed.** Section 1 asks for "five components, one page." The first spec for a change
whose diff is expected to be roughly four lines came to **1,412 words** and 167 lines,
roughly three pages. It passed every gate as written, because no gate looks at length. It
took an explicit instruction to cut it; the rewrite lands at 797 words with nothing required
by the task removed, which proves the overage was slack rather than necessary detail.

**Classification: rule needing enforcement.** "One page" is the right target and the section
5 rationale ("ceremony outweighing the change") is the right reason. The defect is that
"one page" is unmeasurable prose sitting in a document with no checkpoint that reads it.

**Proposed improvement.** Give the tier a number and hang it on the gate that already runs:

- Lightweight manual spec: **800 words maximum**, investigation notes and alternatives kept
  in separate files.
- Add to the before-planning gate: run `wc -w` on the spec and cut before proceeding. A
  spec over budget is a finding at that gate, in the same way an unobservable criterion is.

The separate-file rule matters as much as the number: the first draft grew because probe
output, a rejected alternative, and seven unsupported assumptions all landed in a document
whose job is to state the contract.

### 2. The lightweight implementation gate names an artifact that does not exist

**Observed.** Section 3's before-implementation gate says "compare spec and plan side by
side." At lightweight intensity there is no plan: section 1 prescribes a spec and section 5
prescribes manual Markdown, and neither produces a plan document. The gate was run by
treating a three-line approach note inside the spec as the plan, which is an interpretation,
not the rule.

**Classification: wrong rule.** As written the gate is unrunnable at the intensity the
workflow most often uses. A gate that cannot be executed as specified is either skipped or
silently redefined by whoever is holding it, and both outcomes are worse than a gate whose
input is stated.

**Proposed improvement.** Make the gate's input tier-dependent and keep its three questions:

- **Lightweight:** compare the spec against a named approach recorded in the spec itself -
  target method, chosen code shape, and the in-repo precedent for that shape - then ask for
  the strongest senior objection and name every unsupported assumption. No plan document.
- **Stepped-up:** the existing side-by-side spec-and-plan comparison, plus a peer.

The three questions are what caught the binding risk here; only the artifact naming was
wrong, so the fix should not weaken them.

### 3. The intensity step-down boundary required interpretation

**Observed.** Section 1 steps down to no spec for "mechanical ... changes with an obvious
verification," and its boundary test is "whether I can fully check correctness as I make the
change," with reach beyond the edited file as the trigger to step up. This change answers
*yes* to full checkability - one test run - and reaches beyond nothing: one method, one file.
Read literally, the workflow says write no spec. That reading is wrong, and not marginally:
the obvious implementation of this "mechanical" refactoring is
`showOwner(@ModelAttribute("owner") Owner owner)`, which turns a read endpoint into a
request-parameter binding sink. The risk lives in the *implementation* of a
behavior-preserving change, which is a category the boundary test cannot see.

**Classification: wrong rule.** The discriminator is checkability of the *intended* change.
It should be the risk of the *plausible implementations* of that change.

**Proposed improvement.** Add a third clause to the step-down test, phrased as the failure it
prevents in the style of the rest of the document:

> Step up from "no spec" when the change is behavior-preserving by intent but a plausible
> implementation of it would add or alter observable behavior. Write the invariant the
> implementation must not break, and a test for it.
>
> *Prevents:* a refactoring justified as having no behavior change shipping the behavior
> change hidden in how it was carried out.

This keeps genuine step-downs - renames, formatting, typos - outside the spec requirement,
because no plausible implementation of a rename adds an input surface.

## Implementation observations

Recorded at the before-commit gate, after implementing against the shortened spec.

**The single-commit boundary held, and the probe showed why it had to.** The change is two
files, 20 insertions, 10 deletions: the handler, its Javadoc, two now-unused imports, and
two test additions. One rationale, one revert. More usefully, the boundary was *tested*: with
the forbidden `showOwner(@ModelAttribute("owner") Owner owner)` shape in place,
`showOwner()` including its new `times(1)` assertion **still passed**, and only
`showOwnerIgnoresRequestParameters` failed. So the exactly-once assertion cannot detect the
binding regression, and the binding guard cannot detect a reintroduced second lookup. Two
independent witnesses for one change. Splitting them across commits, as originally planned,
would have shipped the implementation choice in a commit with no evidence for it.

**The zero-argument handler worked, with no surprises.** `public String showOwner()` returning
`"owners/ownerDetails"` is sufficient; `findOwner` supplies the `owner` attribute untouched.
`findOwner` was not modified. Removing the handler's body made `java.util.Optional` and
`org.springframework.web.servlet.ModelAndView` unused, so both imports went with it -
`@PathVariable` stays, still used by `findOwner` and `processUpdateOwnerForm`. The tree
already contained the precedent (`initUpdateOwnerForm`), and spring-javaformat reflowed the
new Javadoc without complaint. 52 tests in the `owner` package pass, 0 skipped.

**FR-4 was preserved, and is now proven rather than asserted.** WORKFLOW.md section 2 asks
for the witness to be proven once by breaking the production line. Done: under the forbidden
argument shape the guard fails with `property 'firstName' was "Injected"`. That also settles
an assumption named at the implementation gate as unsupported - that an `@ModelAttribute`
argument would in fact bind query parameters here. It does. FR-4 was not a hypothetical.

**Implementation did need one thing the shortened spec no longer contained.** The 1,412-to-797
word cut removed the approach note, so the spec states the binding *invariant* (FR-4) but
names neither the chosen handler shape nor its in-repo precedent. Here that cost nothing,
because the approving instruction specified the zero-argument shape directly. Absent that, an
implementer reading only the spec would know what not to do and not what to do - and could
satisfy FR-1 with the `@ModelAttribute`-argument shape, fail FR-4, and have to discover the
conflict from a test.

### 4. Word budget and "approach recorded in the spec" are in direct tension

**Observed.** Preliminary improvements 1 and 2 collide. Improvement 2 makes the lightweight
implementation gate compare the spec against "a named approach recorded in the spec itself."
Improvement 1 imposes an 800-word budget - and satisfying that budget is *precisely* what
deleted the approach note from this spec. Two fixes written an hour apart, each sound alone,
that cannot both be followed.

**Classification: wrong rule** - improvement 1 as drafted, not the budget idea itself. A
budget that counts the contract and the approach against one another prices the wrong thing.

**Proposed improvement.** Budget the contract only, and make the approach a required,
explicitly small component rather than optional prose competing for the same words:

- 800 words covers purpose, scope, requirements, acceptance criteria, constraints and
  mapping.
- Plus a mandatory **Approach** block of at most 60 words, outside the budget: target
  method, chosen shape, in-repo precedent. It is what the implementation gate compares
  against, so it cannot be the first thing cut.

Verify this against the next task rather than assuming it; both preliminary items were
plausible before use too.

## Closing-gate observations

Recorded after the full build on commit `3f7b503`.

**Both builds are green on the same 83 tests.** `./mvnw verify`: BUILD SUCCESS, 83 tests, 0
failures, 0 errors, 0 skipped, including `MySqlIntegrationTests` (Testcontainers) and
`PostgresIntegrationTests` (Docker Compose). `./gradlew build`: BUILD SUCCESSFUL, 22 test
classes, 83 tests, 0 failures, 0 errors, 0 skipped, with `checkFormatMain`,
`checkFormatTest`, `checkstyleMain`, `checkstyleTest` and `checkstyleNohttp` all passing.

**AC-1 to AC-7 reconciled against observation, not against the mapping table.** The gate
originally would have been satisfied by "the named test passed," which is the defect section
2 warns about. Instead the running application was driven directly with `curl`, with
`org.hibernate.SQL` at DEBUG, and the owner `select` was counted per request:

- AC-1: `GET /owners/1` produced **exactly one** owner `select` in the SQL log. This is the
  only database-level evidence for the central one-round-trip claim - the Mockito `times(1)`
  assertion proves the controller called the repository once, whereas the SQL count proves
  the database was queried once.
- AC-2: 200, and the rendered body carried `Owner Information`, `Pets and Visits`, `George`,
  `Franklin`, `110 W. Liberty St.`, `Madison`, `6085551023` and pet `Leo`. The visit clause
  is witnessed only by the `OwnerControllerTests` fixture, exactly as the spec states, since
  seeded owner 1 has no visits.
- AC-3: 404 for `Accept: text/html` and 404 for `Accept: application/json`.
- AC-4: 404, body contains `Something happened` and `The requested page was not found.`, and
  contains neither `An internal server error occurred.` nor `Whitelabel Error Page`.
- AC-5: `GET /owners/1?firstName=Injected` returned 200 with `George` present and `Injected`
  **absent** from the rendered page. FR-4 holds in the real application, not only under
  MockMvc.
- AC-6: `GET /owners/abc` returned 400, with zero owner selects.
- AC-7: `GET /owners?page=0` returned 500, not 404, and rendered the internal-error message
  rather than the not-found one.

**The spec now matches the code.** Status set to implemented and verified, commit hash
recorded, the two "to add" witnesses marked as delivered.

### Verdict on each proposed improvement

| # | Improvement | Verdict |
| --- | --- | --- |
| 1 | 800-word lightweight budget, enforced by `wc -w` at the before-planning gate | **Changed.** The number and the enforcement point survive; the scope does not. It now budgets the contract only. As drafted it caused break 4. |
| 2 | Tier-dependent implementation gate | **Accepted** as drafted. It was the gate that surfaced the binding objection. |
| 3 | Plausible-implementation-risk trigger for intensity | **Accepted** as drafted, and validated twice: it is why a spec existed, and the probe proved the risk was real rather than theoretical. |
| 4 | Contract-only budget plus a mandatory Approach block of at most 60 words | **Accepted, and validated in use at this gate.** The block came to **35 words** and carried exactly the three facts an implementer needed - target method, chosen shape, in-repo precedent - while the contract stayed at **794 words**. Nothing had to be cut to fit it. |
| 5 | Explicit reviewer verdict before the commit gate passes | **Accepted, then insufficient on immediate retest, now strengthened.** The very next review produced the explicit verdict the rule demands - and its citations were invalid (observation 6). The rule now also requires spot-checking one material evidence claim or citation from the verdict against the source. |

One cost of improvement 1 is worth recording, since it landed on the revision itself:
bringing `WORKFLOW.md` from 1,126 draft words to 850 took six trim passes. The budget is
still the right rule - the first spec proved the failure it prevents - but a word ceiling is
cheap to state and expensive to hit, and that effort is real rather than free.

### 5. The initial before-commit review attempt ended without a verdict

**Observed.** `/review-changes` was run on the staged 30-line diff. The reviewer agent hit
its `maxTurns: 8` cap and stopped one step before reporting, with the partial output ending
mid-investigation: "Let me check if there are existing tests for the 404 scenario". It
consumed its whole budget on orientation - reading `CLAUDE.md` and hunting for the 404 tests
- not on diff size. It produced a verdict only after an explicit resume that handed it the
context it was looking for; it then returned "no actionable findings" in one turn, using
zero further tool calls. Section 3 says to "run `/review-changes`", and that instruction was
satisfied the moment the command was run. Nothing in the workflow distinguishes a review
that found nothing from a review that never finished.

**Classification: rule needing enforcement.** Requiring a reviewer pass before commit is the
right rule; the reviewer itself behaved within its configured budget. The defect is that the
gate's pass condition is "the command was run" rather than "a verdict was returned", so an
unfinished review is indistinguishable from a clean one - the strictly worse failure, because
it reads as approval.

**Proposed improvement.** Make the gate's pass condition an artifact rather than an action: a
review passes only on an explicit verdict, either findings or an unambiguous "no findings".
A timeout, a turn-limit stop, or truncated output is **not** a pass; resume the reviewer,
retry it, or have a human complete the review. Where the stop is a turn-limit stop, resume
with the context the reviewer was reaching for rather than restarting it, which cost one turn
here instead of eight.

### 6. An explicit verdict carried invalid citations

**Observed.** With the three closing-gate documents staged, `/review-changes` again hit its
`maxTurns: 8` cap without reporting, and again produced a verdict only after an explicit
resume. That verdict was "No actionable findings", with five specific claims about where each
rule lives. Checked against the files, **every citation was wrong**:

- The spec's Approach block was cited at "lines 188-195" and a mapping sentence at "line
  290". `spec-owner-details-single-lookup-2026-09.md` is **123 lines** long, so both
  citations point past the end of the file. The real locations are lines 9-16 and line 111.
- In the revision under review, all five `WORKFLOW.md` ranges named the wrong text: the
  800-word budget was cited at lines 59-63 (actually 38-42; 59-63 was the before-commit
  gate), the tier-dependent implementation gate at lines 90-93 (actually 55-58; 90-93 was
  tool choice), the plausible-implementation trigger at lines 32-35 (actually 20-22; 32-35
  was the template list), the Approach block at lines 59-63 (actually 40-42), and the
  explicit-verdict rule at lines 94-98 (actually 59-63).

The resumed reviewer used **zero additional tool calls**, so nothing in the turn that
produced the verdict was newly read. Its conclusion happened to match what I had already
checked myself before resuming it, which is agreement with my own work rather than
independent confirmation. The verdict was rejected and the documents unstaged.

**Classification: wrong rule** - improvement 5 as drafted. Requiring an explicit verdict is
necessary but not sufficient. It correctly rejects silence, a timeout and a truncated stop,
but it accepts a fluent verdict whose evidence does not exist, which is the more dangerous
failure: silence looks like an incomplete gate, whereas a confident "no findings" looks like
a passed one.

**Proposed correction.** Before accepting any verdict, spot-check at least one material
evidence claim or citation from it against the source. A citation to a nonexistent or
incorrect line invalidates the review, and an invalid review is resumed, retried, or
completed manually - the same remedies a timeout already gets. One checked citation is cheap
and would have caught this instantly.

Contributing cause, on the harness rather than the workflow: `HARNESS.md` configures the
reviewer as `model: haiku` with `maxTurns: 8`, sized for small code diffs. It exhausted that
budget on orientation twice running - once on a 30-line code diff, once on a 421-line prose
diff - and when pushed to conclude, supplied plausible citations instead of read ones.

### Was skipping the running-app check at the before-commit gate acceptable?

**Acceptable for the commit, but it was not sufficient for closing.** Section 3 asks for the
affected path to be exercised in the running app "when practical", and the judgement to skip
it rested on genuinely strong coverage: `OwnerControllerTests` drives the route under
MockMvc, and `OwnerNotFoundIntegrationTests` drives it over real HTTP in a full application
context with the seeded database. Those cover the 200 and the 404, and the app run found no
defect they had missed - so nothing shipped that the tests had not already checked.

It was still the weaker choice, for a reason specific to this task: **every test-level
witness for AC-1 is a mock assertion**, so before the app run the central claim of the change
- one lookup - rested entirely on counting calls to a stubbed repository. The SQL count from
the running app is the only evidence that the *database* is queried once. For a change whose
entire purpose is the number of repository round trips, that evidence belongs at the gate
that guards the commit, not at the one that closes the task. The rule should stay "when
practical" rather than becoming mandatory; what this trial shows is that "the criterion is
only witnessed by mocks" is a strong signal that it is practical enough.
