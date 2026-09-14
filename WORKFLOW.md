# My Spec-Driven Development Workflow

Every rule names the failure it prevents. Advice that prevents nothing is cut.

## 1. Default intensity

**Default: a lightweight manual spec** for any localized behavior change. Five components,
one page, before the first edit.

**Step down** to no spec for mechanical, documentation-only, or no-behavior changes with an
obvious verification: a rename, a formatting pass, a typo, a two-line guard I can watch fail
and then pass.

**Step up** to a full spec, a peer read, and an explicit review gate for cross-cutting
behavior, persistence invariants, public APIs, security, migrations, several modules,
unclear requirements, or multiple handoffs.

The boundary is not size but whether I can fully check correctness as I make the change; a
delay between mistake and symptom, or reach beyond the file I am editing, means step up.

**Prevents:** the locally reasonable fix with invisible reach. Mapping every
`IllegalArgumentException` in a controller to 404 meets the stated goal and silently
reclassifies an unrelated pagination error, with nothing going red.

## 2. Specification template

1. **Purpose and intended outcome** - why current behavior is wrong.
2. **Scope, inputs, and outputs** - the affected routes, declared exhaustive.
3. **Requirements and invariants** - what must not change, and what stays true on the error
   path.
4. **Acceptance criteria** - GIVEN a precondition, WHEN an action, THEN an observable
   result, with AND only for additional observable results. Never an AND smuggling in a
   second precondition.
5. **Constraints, non-goals, and verification mapping** - what is forbidden, what is
   excluded, and per criterion clause the assertion or observation that fails if the
   behavior is wrong.

A mapping table proves a test was mentioned, not that anything was asserted. Where cheap,
prove the witness once: break the production line, watch that assertion fail, restore.

**Prevents:** certifying a criterion no test can detect being violated. A clause reading
"and the record is saved" passed a green suite while the save could be deleted with no
failure.

## 3. Phase gates

- **Before planning (me; a peer when risk warrants).** Verify scope and acceptance criteria:
  exhaustive, observable, satisfiable against real seed data. *Prevents* building the wrong
  thing, or a criterion contradicting the data it describes.
- **Before implementation (me, plus a peer on stepped-up work).** Compare spec and plan side
  by side, ask for the strongest senior objection, name every unsupported assumption.
  *Prevents* a plan that freezes the document it must edit, or reads its test map as a
  ceiling rather than a floor.
- **Before each commit (me, then the reviewer agent).** Focused tests, exercise the affected
  path in the running app when practical, read the complete diff, run `/review-changes`.
  *Prevents* green-build confidence over an unexercised path, and stray files riding along.
- **Before closing (CI or the full build, then me).** Full build, confirm every criterion
  against evidence, reconcile spec with implementation. *Prevents* closing a workflow as
  verified while a document describes behavior that no longer exists.

A coverage defect caught mid-stream costs two assertions; at close it reopens finished work.

## 4. Iteration discipline

State one success criterion before editing. Make one independently useful change with one
primary reason to revert. Verify, read the diff, review, commit, and only then start the
next task. When a task crosses its planned files, its acceptance criteria, or its risk
boundary, stop and split.

No line-count limit. A 150-line additive test class is one reason to revert; a 19-line diff
carrying two unrelated rationales is two.

**Prevents:** the commit nobody can review or revert cleanly, where a behavior change and
its coverage arrive separately and the first cannot ship alone.

## 5. Tool choice

**Manual Markdown** by default for small, localized work. **OpenSpec** for medium or
cross-cutting brownfield changes, where delta specs, a living specification, and structured
team handoffs repay the fixed overhead. **Manual** again in tool-restricted environments.
**BMAD or another multi-agent framework** only for a genuinely multi-role initiative.

Structured validation checks well-formedness, never correctness: a strict-clean change still
named a database-free test class as precedent for a test needing a database. Human review
stays required at every tier.

**Prevents:** ceremony outweighing the change. Four coupled artifacts plus tool round trips
for a seven-line diff.

## Rule considered and rejected

**Requiring a full specification for every typo, formatting change, or mechanical edit.**
The artifact would be longer than the diff and read by nobody. Worse, a process applied
everywhere gets skipped everywhere, including where it was needed. A mechanical edit owes
verification, not paperwork.

## How the rules interact

Intensity picks the template depth and the tool. Acceptance criteria drive the gates and
verification, because a gate with nothing to check is a ritual. Iteration produces the
evidence the closing gate consumes: each verified, reviewed commit is one row of the final
reconciliation.

## The hardest rule under time pressure

**Stopping when scope expands**, rather than pushing through because I am almost done. That
is exactly when the diff stops matching the criterion, the review grows too large to read,
and a second rationale hides inside a commit claiming one. It stays because it fires
precisely when my judgment is worst.
