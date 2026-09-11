# SDD Tool Evaluation: OpenSpec - 2026-09

Homework 2.6 deliverable. Scope: evaluating a spec-driven-development tool by running it
against a feature this repository already specified by hand, then comparing the two.

The feature under test is the missing-owner 404 described in
`spec-owner-not-found-2026-09.md`. The OpenSpec change produced from it is
`openspec/changes/owner-not-found-404/`. No implementation was performed in this session.

## 1. Selected tool and rationale

**OpenSpec 1.13.0** (`openspec --version` reported `1.13.0`).

Selected because this is a brownfield Spring application and OpenSpec is delta-based. The
feature does not create a system; it changes the status of three existing routes that
already work. OpenSpec models exactly that: a change proposes `ADDED`, `MODIFIED`,
`REMOVED`, or `RENAMED` requirements against a living specification, and archiving folds
the delta into the main spec. Nothing had to be invented to describe behavior that already
exists and stays the same.

Why it was preferred over the alternatives for this exercise:

| Option | Why not chosen here |
| --- | --- |
| Manual | Already done on this branch, and it is the control, not the treatment. The manual specification is good but it is a single document with no enforced structure, no validation command, and no path from "this feature shipped" to "the project specification now says so." Requirement numbering is maintained by hand; the count of acceptance criteria is whatever the author last typed. |
| Spec-Kit | Greenfield-shaped. Its `/specify`, `/plan`, `/tasks` flow assumes a feature is being described from nothing, with a project constitution underneath. Pointing it at one 404 status change in an existing controller means most of the generated scaffolding describes a system that already exists and is not changing. |
| GSD | Execution-oriented rather than specification-oriented. Its value is in driving agents through work; the deliverable for this homework is planning artifacts and a comparison against a hand-written specification, which is the part GSD does least. |
| BMAD | Heaviest of the four. A multi-persona agile team (analyst, PM, architect, scrum master, developer) is a large ceremony budget for a change whose entire production diff is one new exception class and two `throw` sites. The personas would generate role-shaped documents that this three-route change does not need. |

The deciding factor was the shape of the work, not tool quality. A brownfield status-code
correction is a delta. OpenSpec is the only one of the four whose core data model is a
delta.

## 2. Commands and workflow used

Setup:

```
npm install -g @fission-ai/openspec@latest
openspec config set telemetry.enabled false
openspec init --tools claude --profile core --no-animation
```

Planning, through the Claude Code slash commands OpenSpec installed:

```
/opsx:propose    # created the change and generated all four artifacts
/opsx:update     # applied five human-review corrections to the existing artifacts
```

Verification, run after each of the two planning steps:

```
openspec validate owner-not-found-404 --strict
openspec status --change owner-not-found-404
```

Both passed after the update: `Change 'owner-not-found-404' is valid` and
`Progress: 4/4 artifacts complete`.

**`/opsx:apply`, `/opsx:sync`, and `/opsx:archive` were not run.** This homework covers
planning artifacts only. No application code, test, template, message bundle, build file,
or application configuration changed, and nothing was staged or committed.

The setup commands above did write files, by design. `openspec init --tools claude` created
`openspec/config.yaml`, the `.claude/commands/opsx/*` slash commands, and the
`.claude/skills/openspec-*` skills, and `openspec new change` created the change directory.
That is the tool installing itself and is the intended effect of running it. The
repository's pre-existing Claude harness files, the ones described in `HARNESS.md`, were
not modified.

## 3. Generated artifact

Change root: `openspec/changes/owner-not-found-404/`

| File | Role |
| --- | --- |
| `.openspec.yaml` | Change metadata written by `openspec new change`; records the `spec-driven` schema |
| `proposal.md` | Why, What Changes, Capabilities, Impact |
| `design.md` | Context, goals and non-goals, three decisions with rejected alternatives, risks, migration plan |
| `specs/owner-management/spec.md` | Delta specification: `## Purpose` plus `## ADDED Requirements` |
| `tasks.md` | 17 checkbox tasks in four groups, each carrying its own verification |

Final measured state:

- **4/4 artifacts complete** (`openspec status --change owner-not-found-404`)
- **5 requirements** (`grep -c '^### Requirement:'`)
- **13 scenarios** (`grep -c '^#### Scenario:'`)

Requirement-to-scenario distribution: missing owner returns 404 (4), the 404 reuses the
existing error page (1), a missing-owner request performs no persistent write (2),
existing-owner behavior is unchanged (3), unrelated error classifications are preserved (3).

## 4. What worked out of the box

**Installation and Claude integration.** One global npm install, then
`openspec init --tools claude --profile core` wrote the skills and slash commands directly
into `.claude/`. The `/opsx:*` commands were available in the next session with no manual
wiring, no MCP server to register, and no settings edit. This is the smoothest part of the
tool.

**Standard proposal, specification, design, and task handoff structure.** The schema
defines an artifact dependency graph, and `openspec status --json` reports it: `specs` and
`design` depend on `proposal`, `tasks` depends on both. Each artifact ships a template and
a rules block fetched with `openspec instructions <id>`. The result is that two different
changes in this repository would have the same shape, which is the property a hand-written
document cannot promise.

**Strict validation.** `openspec validate --strict` is a real gate, not a formatting pass.
It enforces that every requirement has at least one scenario, that scenarios use exactly
four hashes, that a new capability opens with a `## Purpose` of meaningful length, and that
a change declares at least one delta or explicitly sets `skip_specs`. The four-hash rule
matters: three hashes fail silently and the scenario disappears from the parsed change.

**Brownfield codebase investigation.** The workflow required reading the implementation
before drafting, and that read produced the most valuable line in the whole change. The
manual specification forbids mapping every `IllegalArgumentException` to 404 because
`GET /owners?page=0` raises one. Reading `OwnerController` showed that the invalid-pagination
exception is raised at `OwnerController.java:135`, inside `processFindForm`, which is a
method of `OwnerController` itself. So the obvious safe-looking narrowing, an
`@ExceptionHandler(IllegalArgumentException.class)` scoped to just this controller, does
not work either: the pagination failure is inside the scope. It reads safe and it is not.
Neither the manual specification nor the peer-read notes state this. It is recorded as
decision D1 in `design.md`.

A second, smaller find: `PetController` and `VisitController` live in the same `owner`
package and throw the same exception class for a missing owner, so package placement alone
gives no isolation. Only the set of throw sites does.

## 5. What required adjustment

The generated artifacts were not correct on the first pass. Five corrections, all found by
human review and none by the tool:

1. **Wrong integration-test precedent.** The artifacts said the new
   `OwnerNotFoundIntegrationTests` should be modelled on `CrashControllerIntegrationTests`,
   because that class is the visible example of `@SpringBootTest(RANDOM_PORT)` plus
   `TestRestTemplate` asserting an error page. That is superficially right and materially
   wrong: `CrashControllerIntegrationTests.java:95-99` declares a nested
   `@SpringBootApplication` that excludes `DataSourceAutoConfiguration`,
   `DataSourceTransactionManagerAutoConfiguration`, and `HibernateJpaAutoConfiguration`.
   It runs with no database at all, so it could never support the persistent-state
   invariant, which needs to count owners and re-read a control owner. The correct
   full-context H2 precedent is `PetClinicIntegrationTests`. Corrected to
   `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `@AutoConfigureTestRestTemplate`, an
   injected `TestRestTemplate`, and an injected `OwnerRepository`.
2. **AC-5 assumed to be already covered.** The artifacts asserted that `OwnerControllerTests`
   could remain completely unmodified because it already verifies the existing-owner paths.
   It does not, quite. `processUpdateOwnerFormSuccess` at `OwnerControllerTests.java:211-221`
   asserts `is3xxRedirection()` and the view name `redirect:/owners/{ownerId}`, but never the
   expanded redirect URL and never that a save occurred. AC-5's third bullet claims both.
   The tool inherited that claim from the manual specification's test-mapping table rather
   than checking it. Corrected by adding a task to strengthen that one method with
   `redirectedUrl("/owners/1")` and `verify(this.owners).save(any(Owner.class))`, keeping the
   existing assertions and changing no other method. The required imports already exist at
   `OwnerControllerTests.java:43,48,52`.
3. **Out-of-scope promoted to normative.** The delta specification contained a scenario
   named "Pet and visit routes are untouched" asserting their behavior is exactly as before,
   including the status returned for a missing owner. That silently converted an exclusion
   into a requirement: it would have frozen the current pet and visit 500 into the
   `owner-management` capability, so a later change fixing them would first have to amend a
   specification that was never supposed to govern them. Removed. Their non-modification is
   now held as a proposal non-goal, a design non-goal, and a `git diff --name-only` scope
   guard in `tasks.md`, which is where a scope guard belongs.
4. **Fixture data written as a requirement.** The generated existing-owner scenario
   inherited fixture-specific wording from the manual specification, asserting that the
   `owner` model attribute contains "the stored owner, its pets, and their visits." Read as
   a requirement rather than as a description of the seed data, that says every owner
   detail response must carry pets and every pet must carry visits, which is not the
   behavior and would not hold for an owner with no pets. Human review changed it to "the
   requested owner, including any associated pets and their visits" and added an explicit
   sentence that this does not require the seeded owner 1 to have any particular pet or
   visit, because the requirement constrains the shape of what is exposed and not the
   fixture data. This prevents fixture data from becoming a product requirement, which
   matters because the capability outlives the seed script: a seeded row that hardens into
   normative text makes the test data expensive to change later, and for the wrong reason.
5. **Scenario miscount.** The propose step reported 15 scenarios in a file that contained
   14. Nothing in the tool caught this, because scenario count is not something
   `openspec validate` checks; it validates structure, not arithmetic. Removing the pet and
   visit scenario brought the real count to 13, confirmed by
   `grep -c '^#### Scenario:'`.

All five were fixed through `/opsx:update`, which kept the four artifacts consistent with
one another rather than editing one in isolation. The update workflow showed each proposed
revision before writing and waited for approval, which is the right default for a tool that
edits four coupled documents at once.

Two patterns run through these. Items 1 and 2 are plausible-sounding claims about test
infrastructure and existing coverage that were never checked against the test sources: the
codebase investigation that worked well in section 4 was applied to the production code
and not, with the same rigor, to the test code. Items 3 and 4 are boundary leaks in the
same direction as each other, an exclusion and a fixture both hardening into normative
text, which is the characteristic failure mode of a format that rewards writing
requirements. Item 5 is arithmetic that no validator checks.

## 6. What OpenSpec forced that Manual did not

**Named capability and delta-spec structure.** The manual specification is a document about
a feature. OpenSpec required naming a durable capability, `owner-management`, and writing
the change as a delta against it. The forcing function is useful: it asks "what part of the
system does this belong to after the change ships," which a feature document never has to
answer. Helpful.

**Requirements colocated with scenarios.** The manual specification separates section 2
(FR-1 to FR-6) from section 3 (AC-1 to AC-8) and links them by cross-reference and by a
mapping table in section 8. OpenSpec puts each scenario directly under the requirement it
verifies, so a requirement with no scenario is a validation error rather than an oversight
a reader has to notice. This caught nothing here because the manual specification was
already thorough, but it removes a whole class of drift. Helpful.

**Separate design decisions and rejected alternatives.** The manual specification
deliberately leaves the implementation open, which is correct for a specification and
leaves the hardest question unanswered. `design.md` is where the controller-scoped
`@ExceptionHandler` trap got written down. The rejected-alternatives format is what made
that finding a durable artifact instead of a remark. Most helpful single difference.

**Standard task checklist and archive-ready handoff.** `tasks.md` is parsed by the apply
phase, so checkboxes are tracked state, not decoration. The schema rule that each task must
state how it is verified pushed the scope guards into executable form (`grep`,
`git diff --name-only`) rather than prose prohibitions. Helpful.

**Where it added noise.** For a change this small, some ceremony did not earn its cost. The
`## Purpose` section, the Migration Plan heading in `design.md` (answer: not applicable, no
data migration, no API version change), and the Goals and Non-Goals section restating
boundaries the proposal already sets are all structure for structure's sake at this size.
The artifact dependency ordering also means four `openspec instructions` round trips before
any content is written. On a change with one new exception class, the fixed overhead is a
visible fraction of the total work. It would disappear into the noise on a larger change,
which is precisely the scoping recommendation in section 8.

## 7. Team handoff

Each artifact answers a different reader's question, and the split is the part worth keeping:

- **`proposal.md`** communicates intent and scope. A reviewer who reads only this knows why
  a 500 is wrong here, which three routes change, and what is explicitly out of bounds.
- **`specs/owner-management/spec.md`** communicates testable behavior. 5 requirements and
  13 scenarios in GIVEN/WHEN/THEN form, each one a candidate test case, with no class names
  or framework choices in it.
- **`design.md`** communicates technical decisions and rejected alternatives. This is what a
  future contributor needs to not re-introduce the controller-scoped exception handler that
  looks safe and breaks `?page=0`.
- **`tasks.md`** communicates implementation order and verification. 17 checkboxes in four
  groups, dependency-ordered, each stating how completion is checked.

**Archive would update the living specification**, promoting the delta into
`openspec/specs/owner-management/spec.md` so the project's specification reflects shipped
behavior. That is the step that makes the tool worth using across many changes rather than
one. **Archive was not performed in this homework**, and neither was sync, so
`openspec/specs/` is still empty.

## 8. Three-sentence justification for keeping the tool

1. It fits this feature's scope adequately but not comfortably: a three-route status-code
   correction is at the low end of where four coupled planning artifacts pay for themselves,
   and the fixed structural overhead was a visible fraction of the total effort.
2. It fits the team and environment well: installation was one npm command, the Claude Code
   integration required no manual configuration, `openspec validate --strict` is a real CI-able
   gate, and the artifacts are plain Markdown that reviews in a normal pull request with no
   tooling on the reader's side.
3. Its delta-based and living-spec philosophy matches this workflow closely, because a
   brownfield Spring application accumulates changes to existing behavior rather than
   greenfield features, and the `ADDED`/`MODIFIED`/`REMOVED` model plus archive is exactly
   how that accumulation should be recorded; therefore keep OpenSpec for medium or
   cross-cutting brownfield changes, and do not require it for every small change, where a
   focused pull request description remains the better tool.

## 9. Configuration choice rejected

**Anonymous telemetry is enabled by default.** OpenSpec collects anonymous usage data out
of the box. Even anonymized, outbound telemetry from a developer workstation in a company
environment is a decision that belongs to the organization and not to a tool default, and
the data is command and workflow usage from a private repository.

This was rejected and disabled before the tool was used for any real work:

```
openspec config set telemetry.enabled false
```

Verification:

```
$ openspec config get telemetry.enabled
false
```

**Setup noise, not a failure.** Every `openspec` invocation on this machine prints
`ExperimentalWarning: Importing JSON modules is an experimental feature and might change at
any time` on stderr, followed by the `--trace-warnings` hint. This is Node v22.10.0
reporting on OpenSpec's use of JSON module imports. It appears before valid output,
including the successful strict validation, and it affects no exit code and no behavior.
It is cosmetic, it is the tool's business and not this repository's, and it should not be
mistaken for a broken install. Filtering stderr keeps command output readable.

## 10. Final evaluation

**OpenSpec improved artifact consistency and handoff shape.** Four artifacts with fixed
roles, a machine-checkable structure, requirements that cannot exist without scenarios, and
tasks that cannot be written without stating their verification. Compared against the
hand-written specification covering the same feature, the OpenSpec version is not better
informed, but it is better organized and harder to leave half-finished. It also produced
one genuinely new technical finding, the controller-scoped exception handler trap, that the
manual specification and its peer-read both missed.

**It did not replace human review.** The tool validated as strict-clean while asserting a
test precedent that runs without a database, while trusting an existing test's coverage
claim it had not verified, and while quietly promoting an out-of-scope controller into a
normative requirement. Strict validation confirms a change is well-formed. It says nothing
about whether the change is right. Every one of the five corrections in section 5 was found
by a human reading the artifacts against the code, and all five are the kind of error that
looks completely reasonable on the page.

**No implementation was performed.** `/opsx:apply`, `/opsx:sync`, and `/opsx:archive` were
not run. No application code, test, template, message bundle, build file, or application
configuration changed, and the manual specification is untouched. OpenSpec initialization
intentionally created `openspec/config.yaml`, `.claude/commands/opsx/*`, and
`.claude/skills/openspec-*`, which is the tool installing itself rather than a change to
this project's behavior. The repository's pre-existing Claude harness files were not
modified.

**Verdict: keep it, scoped.** Adopt OpenSpec for medium and cross-cutting brownfield changes
in this repository, where the delta model and the living specification earn their overhead
and the four-artifact handoff is worth writing. Do not mandate it for small changes, where
the structure costs more than it returns. Keep human review as a required gate regardless,
because that is where this session's real defects were caught, and treat
`openspec validate --strict` as a well-formedness check rather than a correctness one.
