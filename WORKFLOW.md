# My Spec-Driven Development Workflow

Every rule names the failure it prevents. Advice that prevents nothing is cut.

## 1. Default intensity

**Default: a lightweight manual spec** for any localized behavior change.

**Step down** to no spec for mechanical, documentation-only, or no-behavior changes with an
obvious verification: a rename, a formatting pass, a typo, a guard I watch fail then pass.

**Step up** to a full spec, a peer read, and a review gate for cross-cutting behavior,
persistence invariants, public APIs, security, migrations, several modules, unclear
requirements, or handoffs.

Two tests decide; either alone is enough to step up:

1. Can I fully check correctness as I make the change? A delay between mistake and symptom,
   or reach beyond the file I edit, means step up.
2. Is the change behavior-preserving by intent, yet would a plausible implementation alter
   observable behavior? Step up and test the invariant it must hold.

**Prevents:** the locally reasonable fix with invisible reach - mapping every
`IllegalArgumentException` to 404 reclassifies a pagination error - and the refactoring
whose behavior change hides in how it was done, such as a duplicate-lookup fix that binds
request parameters onto the entity it shows.

## 2. Specification template

1. **Purpose** - why current behavior is wrong.
2. **Scope, inputs, and outputs** - the affected routes, declared exhaustive.
3. **Requirements and invariants** - what must not change, and what holds on error paths.
4. **Acceptance criteria** - GIVEN a precondition, WHEN an action, THEN an observable
   result; AND only for further observable results, never a second precondition.
5. **Constraints, non-goals, and mapping** - per criterion, the assertion that fails if the
   behavior is wrong.

A lightweight contract is **800 words maximum**, checked with `wc -w` at the before-planning
gate; over budget is a finding. Notes and unsupported assumptions go elsewhere. Plus a
mandatory **Approach** block of at most 60 words, outside that budget: target method, chosen
shape, in-repository precedent. The implementation gate compares against it, so the budget
must not cut it.

A mapping table proves a test was mentioned, not that anything was asserted. Where cheap,
prove the witness once: break the production line, watch it fail, restore. A criterion
witnessed only by mocks should also be seen in the running app.

**Prevents:** certifying a criterion no test can detect being violated.

## 3. Phase gates

- **Before planning (me; a peer when risk warrants).** Verify the criteria are exhaustive,
  observable, and satisfiable against real seed data; check the budget. *Prevents* a
  criterion contradicting its own data.
- **Before implementation.** Lightweight: compare the spec against its Approach block.
  Stepped-up: compare spec and plan side by side, with a peer. Both ask for the strongest
  senior objection and name every unsupported assumption. *Prevents* a gate nobody can run,
  naming an artifact the tier never makes.
- **Before each commit (me, then the reviewer agent).** Focused tests, the running app when
  practical, the complete diff, then `/review-changes`. It passes only on an explicit
  verdict - findings, or an unambiguous "no findings" - and only once I have spot-checked at
  least one material evidence claim or citation from that verdict against the source. A
  timeout, a turn-limit stop, truncated output, or a citation to a nonexistent or incorrect
  line invalidates the review: resume it, retry it, or complete it manually. *Prevents* both
  an unfinished review and a confidently confabulated one reading as approval.
- **Before closing (CI or the full build, then me).** Full build, confirm every criterion
  against evidence rather than the mapping table, reconcile spec with code. *Prevents*
  closing as verified while a document describes behavior that no longer exists.

A gate with nothing to check is a ritual. A defect caught mid-stream costs two assertions;
at close it reopens finished work.

## 4. Iteration discipline

State one success criterion before editing. Make one independently useful change with one
primary reason to revert. Verify, read the diff, review, commit, then take the next task.
When a task crosses its files, criteria, or risk boundary, stop and split.

No line-count limit: a 150-line additive test class is one reason to revert; a 19-line diff
with two unrelated rationales is two. A change and the guard proving it correct are one
rationale, never split across commits.

**Prevents:** the commit nobody can revert cleanly.

## 5. Tool choice

**Manual Markdown** by default for small, localized work. **OpenSpec** for medium or
cross-cutting brownfield changes, where delta specs repay the overhead. **Manual** again in
tool-restricted environments. **BMAD** or similar only for a multi-role initiative.

Structured validation checks well-formedness, never correctness; human review stays
required.

**Prevents:** ceremony outweighing the change.

## Rule considered and rejected

**A full specification for every typo or mechanical edit.** The artifact would be longer
than the diff and read by nobody, and a process applied everywhere gets skipped everywhere.
A mechanical edit owes verification, not paperwork.

## The hardest rule under time pressure

**Stopping when scope expands** rather than pushing through because I am almost done - when
the diff stops matching the criterion and a second rationale hides in a commit claiming one.
It fires when my judgment is worst.
